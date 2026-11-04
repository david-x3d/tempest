package io.github.davidx3d.tempest.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.davidx3d.tempest.data.pace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlin.math.abs

/**
 * Live battery readings. The broadcast only fires when something changes, so the current
 * (which the broadcast does not carry) is re-read on a short tick against the last intent.
 */
class BatteryRepository(
    private val context: Context,
    private val foreground: StateFlow<Boolean>,
    scope: CoroutineScope,
) {

    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    /** Which sysfs file gives the voltage on this phone; probed once, null when none is readable. */
    private val voltageFile: File? by lazy {
        VOLTAGE_FILES.map(::File).firstOrNull { file -> readVolts(file) != null }
    }

    private var averageCurrent: Float? = null
    private var averagedStatus: ChargeStatus? = null

    val snapshot: StateFlow<BatterySnapshot?> =
        combine(batteryIntents(), ticker()) { intent, _ -> parse(intent) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private fun batteryIntents(): Flow<Intent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { trySend(it) }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            foreground.pace(CURRENT_POLL_MS, BACKGROUND_POLL_MS)
        }
    }

    private fun parse(intent: Intent): BatterySnapshot? {
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return null
        val millivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.Charging
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.Discharging
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.Full
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NotCharging
            else -> ChargeStatus.Unknown
        }
        val current = readCurrentMilliamps()
        val exactVolts = normalizeVolts(millivolts) ?: voltageFile?.let(::readVolts)
        return BatterySnapshot(
            temperatureCelsius = tenths / 10f,
            // Some vendors only send whole volts ("4"). Better a rough value than a wrong exact one.
            voltageVolts = exactVolts ?: millivolts.takeIf { it in 1..19 }?.toFloat(),
            voltageEstimated = exactVolts == null && millivolts in 1..19,
            currentMilliamps = current,
            averageCurrentMilliamps = average(current, status),
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            status = status,
            source = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.Ac
                BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.Usb
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.Wireless
                BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.Dock
                else -> PowerSource.Battery
            },
            health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.Good
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.Overheat
                BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.Dead
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OverVoltage
                BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.Cold
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.Failure
                else -> BatteryHealth.Unknown
            },
            chargeCounterMah = readProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                ?.takeIf { it > 0 }?.let { it / 1000 },
            cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
            } else {
                null
            },
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            timeToFullMillis = batteryManager?.computeChargeTimeRemaining()?.takeIf { it > 0 },
        )
    }

    /**
     * EXTRA_VOLTAGE should be millivolts, but some vendors send whole volts (e.g. 4) or microvolts.
     * A Li-ion cell sits between roughly 2.5 and 5 V, so the magnitude tells the unit.
     */
    private fun normalizeVolts(raw: Int): Float? = when {
        raw <= 0 -> null
        raw < 20 -> null // whole volts carry no useful precision, sysfs is better
        raw < 100_000 -> raw / 1000f
        else -> raw / 1_000_000f
    }

    /** voltage_now is in microvolts, a few vendor files use millivolts. */
    private fun readVolts(file: File): Float? = runCatching {
        file.readText().trim().toLong()
    }.getOrNull()?.takeIf { it > 0 }?.let { raw ->
        when {
            raw > 100_000 -> raw / 1_000_000f
            raw > 1_000 -> raw / 1000f
            else -> null
        }
    }

    /**
     * A smoothed current for the runtime estimate; the instant value jumps with every screen
     * redraw. Starts over when the phone is plugged in or out.
     */
    private fun average(current: Int?, status: ChargeStatus): Int? {
        if (current == null) return null
        if (status != averagedStatus) {
            averagedStatus = status
            averageCurrent = null
        }
        val next = averageCurrent?.let { it * 0.85f + current * 0.15f } ?: current.toFloat()
        averageCurrent = next
        return next.toInt()
    }

    /** CURRENT_NOW is specified in microamps, but some vendors report milliamps. */
    private fun readCurrentMilliamps(): Int? {
        val raw = readProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: return null
        return if (abs(raw) > MICROAMP_THRESHOLD) raw / 1000 else raw
    }

    private fun readProperty(id: Int): Int? =
        batteryManager?.getIntProperty(id)?.takeIf { it != Int.MIN_VALUE }

    private companion object {
        const val CURRENT_POLL_MS = 2_000L
        const val BACKGROUND_POLL_MS = 30_000L
        val VOLTAGE_FILES = listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/battery/voltage_avg",
            "/sys/class/power_supply/bms/voltage_now",
            "/sys/class/power_supply/battery/batt_vol",
            "/sys/class/power_supply/main-battery/voltage_now",
        )
        const val MICROAMP_THRESHOLD = 20_000
    }
}

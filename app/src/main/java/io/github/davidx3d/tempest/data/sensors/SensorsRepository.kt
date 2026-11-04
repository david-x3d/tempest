package io.github.davidx3d.tempest.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class SensorGroup { Motion, Position, Environment, Other }

enum class ReportingMode { Continuous, OnChange, OneShot, Special }

/** One hardware or virtual sensor with its datasheet values. */
data class SensorSpec(
    val id: Int,
    val name: String,
    val vendor: String,
    val type: Int,
    val stringType: String,
    val group: SensorGroup,
    val wakeUp: Boolean,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    /** Fastest rate in Hz, null for sensors that only report on change. */
    val maxRateHz: Float?,
    val version: Int,
    val reportingMode: ReportingMode,
    /** How many events the hardware can buffer while the phone sleeps; 0 without batching. */
    val fifoMax: Int,
    /** Plugged in at runtime, e.g. by a connected accessory. */
    val dynamic: Boolean,
)

/**
 * Every sensor the phone exposes, and live values for the few a screen asks for. Only those are
 * switched on, and only while collected, so the sensor hub can sleep again right after.
 */
class SensorsRepository(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val hardware: Map<Int, Sensor> = manager.getSensorList(Sensor.TYPE_ALL).associateBy { it.id() }

    val sensors: List<SensorSpec> = hardware.values
        .map { it.toSpec() }
        .sortedWith(compareBy({ it.group.ordinal }, { it.type }, { it.name }))

    /** The sensor Android itself would use for [type], preferring a non-wake-up one. */
    fun default(type: Int): SensorSpec? =
        (manager.getDefaultSensor(type, false) ?: manager.getDefaultSensor(type))?.let { hardware[it.id()]?.toSpec() }

    /**
     * Latest values of [ids], pushed at most every [periodMs]. One-shot sensors are skipped,
     * they cannot be listened to like this.
     */
    fun listen(ids: Collection<Int>, periodMs: Long = 100L): Flow<Map<Int, FloatArray>> = callbackFlow {
        val latest = ConcurrentHashMap<Int, FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latest[event.sensor.id()] = event.values.copyOf()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        ids.mapNotNull { hardware[it] }
            .filter { it.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT }
            .forEach { runCatching { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) } }
        val pump = launch {
            while (true) {
                if (latest.isNotEmpty()) trySend(HashMap(latest))
                delay(periodMs)
            }
        }
        awaitClose {
            pump.cancel()
            manager.unregisterListener(listener)
        }
    }.conflate()

    private fun Sensor.toSpec() = SensorSpec(
        id = id(),
        name = name,
        vendor = vendor,
        type = type,
        stringType = stringType.orEmpty(),
        group = groupOf(type),
        wakeUp = isWakeUpSensor,
        maxRange = maximumRange,
        resolution = resolution,
        powerMa = power,
        maxRateHz = minDelay.takeIf { it > 0 }?.let { 1_000_000f / it },
        version = version,
        reportingMode = when (reportingMode) {
            Sensor.REPORTING_MODE_CONTINUOUS -> ReportingMode.Continuous
            Sensor.REPORTING_MODE_ON_CHANGE -> ReportingMode.OnChange
            Sensor.REPORTING_MODE_ONE_SHOT -> ReportingMode.OneShot
            else -> ReportingMode.Special
        },
        fifoMax = fifoMaxEventCount,
        dynamic = isDynamicSensor,
    )

    /** Sensor.getId() is 0 or -1 on many phones, so identity is built from what makes it unique. */
    private fun Sensor.id(): Int = (name + vendor + type + isWakeUpSensor).hashCode()

    /** Sensor.TYPE_ORIENTATION, deprecated but still listed by many phones. */
    private val TYPE_ORIENTATION = 3

    private fun groupOf(type: Int): SensorGroup = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY,
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR,
        Sensor.TYPE_SIGNIFICANT_MOTION, Sensor.TYPE_MOTION_DETECT, Sensor.TYPE_STATIONARY_DETECT,
        Sensor.TYPE_HEAD_TRACKER, Sensor.TYPE_ACCELEROMETER_LIMITED_AXES, Sensor.TYPE_GYROSCOPE_LIMITED_AXES,
        -> SensorGroup.Motion

        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_PROXIMITY, Sensor.TYPE_POSE_6DOF,
        Sensor.TYPE_HINGE_ANGLE, Sensor.TYPE_HEADING, TYPE_ORIENTATION,
        -> SensorGroup.Position

        Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_RELATIVE_HUMIDITY,
        -> SensorGroup.Environment

        else -> SensorGroup.Other
    }
}

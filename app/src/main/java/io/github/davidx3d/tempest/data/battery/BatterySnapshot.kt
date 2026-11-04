package io.github.davidx3d.tempest.data.battery

/** One reading of the battery, taken from the sticky ACTION_BATTERY_CHANGED broadcast. */
data class BatterySnapshot(
    val temperatureCelsius: Float,
    val voltageVolts: Float?,
    /** Positive while charging on most devices; the sign convention is up to the vendor. */
    val currentMilliamps: Int?,
    val levelPercent: Int?,
    val status: ChargeStatus,
    val source: PowerSource,
    val health: BatteryHealth,
    /** Remaining charge reported by the fuel gauge. */
    val chargeCounterMah: Int? = null,
    /** Android 14+. */
    val cycleCount: Int? = null,
    val technology: String? = null,
    /** Only while charging, estimated by the system. */
    val timeToFullMillis: Long? = null,
    /** True when the phone only reports whole volts, so [voltageVolts] is rounded. */
    val voltageEstimated: Boolean = false,
    /** [currentMilliamps] smoothed over the last minute or so. */
    val averageCurrentMilliamps: Int? = null,
) {
    /**
     * Some fast-charging phones (Oppo, OnePlus, Realme...) put two cells in series. A single
     * Li-ion cell never goes above ~4.5 V, so a higher reading means a two-cell pack.
     */
    val seriesCells: Int
        get() = if (voltageVolts != null && voltageVolts > 5.5f) 2 else 1

    /** Instantaneous power in watts, from voltage and current. */
    val powerWatts: Float?
        get() = if (voltageVolts != null && currentMilliamps != null) voltageVolts * currentMilliamps / 1000f else null

    /**
     * Rough time until empty from the charge left and the smoothed drain. Null while charging or
     * when the phone does not report a plausible charge or current.
     */
    val timeToEmptyMillis: Long?
        get() {
            if (status != ChargeStatus.Discharging) return null
            val left = chargeCounterMah?.takeIf { it > 100 } ?: return null
            val drain = averageCurrentMilliamps?.let { kotlin.math.abs(it) }?.takeIf { it >= 20 } ?: return null
            return (left * 3_600_000L / drain).takeIf { it < 100 * 3_600_000L }
        }
}

enum class ChargeStatus { Charging, Discharging, Full, NotCharging, Unknown }

enum class PowerSource { Ac, Usb, Wireless, Dock, Battery }

enum class BatteryHealth { Good, Overheat, Dead, OverVoltage, Cold, Failure, Unknown }

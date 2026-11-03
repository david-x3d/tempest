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
) {
    /** Instantaneous power in watts, from voltage and current. */
    val powerWatts: Float?
        get() = if (voltageVolts != null && currentMilliamps != null) voltageVolts * currentMilliamps / 1000f else null
}

enum class ChargeStatus { Charging, Discharging, Full, NotCharging, Unknown }

enum class PowerSource { Ac, Usb, Wireless, Dock, Battery }

enum class BatteryHealth { Good, Overheat, Dead, OverVoltage, Cold, Failure, Unknown }

package io.github.davidx3d.tempest.data.thermal

data class ThermalZone(
    val index: Int,
    val type: String,
    val celsius: Float,
)

/** A fan, a CPU frequency limiter, a charger throttle... State 0 means "not throttling". */
data class CoolingDevice(
    val index: Int,
    val type: String,
    val current: Int,
    val max: Int,
)

data class CpuCore(
    val index: Int,
    val currentKhz: Long,
    val maxKhz: Long?,
)

enum class RootState { Off, Checking, Granted, Denied }

/** Mirrors PowerManager.THERMAL_STATUS_*. */
enum class ThermalStatus { None, Light, Moderate, Severe, Critical, Emergency, Shutdown }

data class ThermalReading(
    val zones: List<ThermalZone> = emptyList(),
    val coolingDevices: List<CoolingDevice> = emptyList(),
    val cpuCores: List<CpuCore> = emptyList(),
    /** PowerManager forecast for the next 10 s; 1.0 means throttling starts. Null if unsupported. */
    val headroom: Float? = null,
    val root: RootState = RootState.Off,
    val loaded: Boolean = false,
) {
    /** The hottest zone that looks like part of the SoC (CPU, GPU, on-die sensors). */
    val socZone: ThermalZone? by lazy {
        zones.filter { SOC_TYPES.containsMatchIn(it.type) }.maxByOrNull { it.celsius }
            ?: zones.filterNot { NON_SOC_TYPES.containsMatchIn(it.type) }.maxByOrNull { it.celsius }
    }

    private companion object {
        val SOC_TYPES = Regex("cpu|soc|gpu|tsens|apc|cluster|core|big|little|mid|npu|tpu|mtktscpu", RegexOption.IGNORE_CASE)
        val NON_SOC_TYPES = Regex("batt|charger|usb|skin|xo|pa_|quiet|modem|bms|pmic|chg", RegexOption.IGNORE_CASE)
    }
}

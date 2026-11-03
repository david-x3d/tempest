package io.github.davidx3d.tempest.data.thermal

data class ThermalZone(
    val index: Int,
    val type: String,
    val celsius: Float,
)

enum class RootState { Off, Checking, Granted, Denied }

/** Mirrors PowerManager.THERMAL_STATUS_*. */
enum class ThermalStatus { None, Light, Moderate, Severe, Critical, Emergency, Shutdown }

data class ThermalReading(
    val zones: List<ThermalZone> = emptyList(),
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

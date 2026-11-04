package io.github.davidx3d.tempest.data.thermal

import io.github.davidx3d.tempest.data.privilege.Access

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
    val access: Access = Access(),
    val loaded: Boolean = false,
    /** SoC temperature from the thermal HAL, for phones that hide every zone even from Shizuku. */
    val halSoc: Float? = null,
) {
    /** The hottest zone that looks like part of the SoC (CPU, GPU, on-die sensors). */
    val socZone: ThermalZone? by lazy {
        val candidates = ZoneKinds.socCandidates(zones.map { it.type })
        zones.filterIndexed { i, _ -> i in candidates }.maxByOrNull { it.celsius }
            ?: halSoc?.let { ThermalZone(-1, "SoC", it) }
    }
}

/** Shared by the app and the background recorder, so both pick the same "SoC" reading. */
internal object ZoneKinds {
    private val SOC_TYPES = Regex("cpu|soc|gpu|tsens|apc|cluster|core|big|little|mid|npu|tpu|mtktscpu", RegexOption.IGNORE_CASE)
    private val NON_SOC_TYPES = Regex("batt|charger|usb|skin|xo|pa_|quiet|modem|bms|pmic|chg", RegexOption.IGNORE_CASE)

    /** Positions in [types] whose hottest member counts as the SoC temperature. */
    fun socCandidates(types: List<String>): Set<Int> {
        val soc = types.indices.filter { SOC_TYPES.containsMatchIn(types[it]) }
        return (soc.ifEmpty { types.indices.filterNot { NON_SOC_TYPES.containsMatchIn(types[it]) } }).toSet()
    }

    private val HAL_TEMP = Regex("""Temperature\{mValue=(-?[\d.]+), mType=(\d+),""")
    /** CPU, GPU and SOC in android.os.Temperature. */
    private val HAL_SOC_TYPES = setOf(0, 1, 13)

    /** The hottest CPU, GPU or SoC sensor in the "Current temperatures from HAL" block of `dumpsys thermalservice`. */
    fun halSoc(dump: String): Float? = dump.substringAfter("Current temperatures from HAL", "")
        .lineSequence()
        .drop(1)
        .takeWhile { it.isBlank() || it.trimStart().startsWith("Temperature{") }
        .mapNotNull { HAL_TEMP.find(it) }
        .filter { it.groupValues[2].toInt() in HAL_SOC_TYPES }
        .mapNotNull { it.groupValues[1].toFloatOrNull()?.takeIf { c -> c in -30f..200f } }
        .maxOrNull()

    /** Most kernels report millidegrees, a few tenths or whole degrees. Null when implausible. */
    fun celsius(raw: Long): Float? {
        val celsius = when {
            kotlin.math.abs(raw) >= 1_000 -> raw / 1000f
            kotlin.math.abs(raw) >= 200 -> raw / 10f
            else -> raw.toFloat()
        }
        return celsius.takeIf { it in -40f..200f }
    }
}

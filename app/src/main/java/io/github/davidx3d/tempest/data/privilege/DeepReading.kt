package io.github.davidx3d.tempest.data.privilege

import io.github.davidx3d.tempest.data.thermal.ThermalStatus
import kotlin.math.abs

enum class ExtraSource { Hal, Hwmon, PowerSupply }

/** A temperature that is not a plain thermal zone: Android's thermal HAL, hwmon chips, chargers. */
data class ExtraTemp(
    val source: ExtraSource,
    val name: String,
    /** "SKIN", "NPU", "MODEM"... for HAL sensors, null otherwise. */
    val kind: String?,
    val celsius: Float,
    val status: ThermalStatus = ThermalStatus.None,
)

/** One entry of /sys/class/power_supply with the files that could be read. */
data class PowerSupply(val name: String, val fields: Map<String, String>) {
    val type: String? get() = fields["type"]
    fun long(key: String): Long? = fields[key]?.toLongOrNull()
}

/** Charging facts only the kernel knows, from the battery and charger power supplies. */
data class ChargingDetails(
    /** Full charge capacity as a share of the design capacity. */
    val healthPercent: Int?,
    val fullMah: Int?,
    val designMah: Int?,
    val cycleCount: Int?,
    val chargeType: String?,
    val maxChargeMa: Int?,
    val inputLimitMa: Int?,
    val maxVoltageV: Float?,
    val inputVolts: Float?,
    val inputMa: Int?,
) {
    val inputWatts: Float? get() = if (inputVolts != null && inputMa != null && inputMa > 0) inputVolts * inputMa / 1000f else null
    val isEmpty: Boolean
        get() = listOf(healthPercent, fullMah, designMah, cycleCount, chargeType, maxChargeMa, inputLimitMa, maxVoltageV, inputWatts).all { it == null }
}

data class DeepReading(
    val temps: List<ExtraTemp> = emptyList(),
    val supplies: List<PowerSupply> = emptyList(),
    val loaded: Boolean = false,
) {
    val charging: ChargingDetails? by lazy {
        val battery = supplies.firstOrNull { it.type.equals("Battery", true) } ?: return@lazy null
        val charger = supplies.filter { it.type.equals("USB", true) || it.type.equals("Wireless", true) || it.type.equals("Mains", true) }
            .firstOrNull { (it.long("online") ?: 0L) > 0L && (it.long("voltage_now") ?: 0L) > 0L }
        val full = battery.long("charge_full")?.takeIf { it > 0 }
        val design = battery.long("charge_full_design")?.takeIf { it > 0 }
        val health = if (full != null && design != null) (full * 100f / design).toInt().takeIf { it in 10..130 } else null
        ChargingDetails(
            healthPercent = health,
            fullMah = full?.let { (it / 1000).toInt() },
            designMah = design?.let { (it / 1000).toInt() },
            cycleCount = battery.long("cycle_count")?.toInt()?.takeIf { it > 0 },
            chargeType = battery.fields["charge_type"]?.takeIf { !it.equals("N/A", true) && !it.equals("Unknown", true) },
            maxChargeMa = (battery.long("constant_charge_current_max") ?: charger?.long("constant_charge_current_max"))
                ?.takeIf { it > 0 }?.let { (it / 1000).toInt() },
            inputLimitMa = (charger?.long("input_current_limit") ?: battery.long("input_current_limit"))
                ?.takeIf { it > 0 }?.let { (it / 1000).toInt() },
            maxVoltageV = (battery.long("voltage_max") ?: charger?.long("voltage_max"))?.takeIf { it > 0 }?.let { it / 1_000_000f },
            inputVolts = charger?.long("voltage_now")?.let { it / 1_000_000f },
            inputMa = charger?.long("current_now")?.let { abs(it) / 1000 }?.toInt(),
        ).takeUnless { it.isEmpty }
    }
}

/** Turns the output of [DeepRepository.SCRIPT] into a [DeepReading]. */
internal object DeepParser {

    const val MARKER = "#THERMALSERVICE"

    private val HAL = Regex("""Temperature\{mValue=(-?[\d.]+), mType=(-?\d+), mName=([^,]*), mStatus=(\d+)\}""")
    private val HAL_KINDS = mapOf(
        0 to "CPU", 1 to "GPU", 2 to "BATTERY", 3 to "SKIN", 4 to "USB", 5 to "PA", 6 to "BCL V",
        7 to "BCL I", 8 to "BCL %", 9 to "NPU", 10 to "TPU", 11 to "DISPLAY", 12 to "MODEM", 13 to "SOC",
    )

    fun parse(output: String): DeepReading {
        val parts = output.split(MARKER, limit = 2)
        val files = parts[0].lineSequence()
            .mapNotNull { line -> line.split('|', limit = 2).takeIf { it.size == 2 && it[1].isNotBlank() } }
            .associate { it[0].trim() to it[1].trim() }
        val supplies = supplies(files)
        return DeepReading(
            temps = hal(parts.getOrElse(1) { "" }) + hwmon(files) + supplyTemps(supplies),
            supplies = supplies,
            loaded = true,
        )
    }

    /** The "Current temperatures from HAL" block of `dumpsys thermalservice`. */
    private fun hal(dump: String): List<ExtraTemp> {
        val lines = dump.lineSequence().dropWhile { !it.contains("Current temperatures from HAL") }.drop(1)
        // Lines with a NaN value are skipped, but they must not end the block.
        return lines.takeWhile { it.trimStart().startsWith("Temperature{") }
            .mapNotNull { HAL.find(it) }
            .mapNotNull { match ->
                val (value, type, name, status) = match.destructured
                val celsius = value.toFloatOrNull()?.takeIf { it in -40f..200f } ?: return@mapNotNull null
                ExtraTemp(
                    ExtraSource.Hal, name.ifBlank { "sensor" }, HAL_KINDS[type.toInt()], celsius,
                    ThermalStatus.entries.getOrElse(status.toInt()) { ThermalStatus.None },
                )
            }
            .distinctBy { it.name }
            .toList()
    }

    private fun hwmon(files: Map<String, String>): List<ExtraTemp> {
        val dirs = files.keys.filter { it.startsWith("/sys/class/hwmon/hwmon") }
            .map { it.substringBeforeLast('/') }.distinct().sorted()
        return dirs.flatMap { dir ->
            val chip = files["$dir/name"] ?: dir.substringAfterLast('/')
            val inputs = files.keys.filter { it.startsWith("$dir/temp") && it.endsWith("_input") }.sorted()
            inputs.mapNotNull { path ->
                val celsius = files[path]?.toLongOrNull()?.let { it / 1000f }?.takeIf { it in -40f..200f } ?: return@mapNotNull null
                val label = files[path.removeSuffix("_input") + "_label"]
                ExtraTemp(ExtraSource.Hwmon, listOfNotNull(chip, label ?: path.substringAfterLast('/').removeSuffix("_input").takeIf { inputs.size > 1 }).joinToString(" "), null, celsius)
            }
        }
    }

    private fun supplies(files: Map<String, String>): List<PowerSupply> {
        val prefix = "/sys/class/power_supply/"
        return files.keys.filter { it.startsWith(prefix) }
            .groupBy { it.removePrefix(prefix).substringBefore('/') }
            .map { (name, paths) -> PowerSupply(name, paths.associate { it.substringAfterLast('/') to files.getValue(it) }) }
            .sortedBy { it.name }
    }

    /** power_supply "temp" is in tenths of a degree. Battery is skipped: the battery tab shows it. */
    private fun supplyTemps(supplies: List<PowerSupply>): List<ExtraTemp> = supplies
        .filterNot { it.type.equals("Battery", true) }
        .mapNotNull { supply ->
            val raw = supply.long("temp") ?: return@mapNotNull null
            val celsius = (if (abs(raw) >= 1000) raw / 1000f else raw / 10f).takeIf { it in -40f..200f } ?: return@mapNotNull null
            ExtraTemp(ExtraSource.PowerSupply, supply.name, null, celsius)
        }
}

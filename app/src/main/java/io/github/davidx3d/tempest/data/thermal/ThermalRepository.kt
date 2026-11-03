package io.github.davidx3d.tempest.data.thermal

import android.content.Context
import android.os.PowerManager
import io.github.davidx3d.tempest.data.privilege.Access
import io.github.davidx3d.tempest.data.privilege.PrivilegeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlin.math.abs

/**
 * Reads thermal zones, cooling devices and CPU clocks from sysfs. Without Shizuku or root only what
 * SELinux lets an app read is listed; with either, everything is read through one shell call per refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThermalRepository(
    context: Context,
    private val privilege: PrivilegeManager,
    scope: CoroutineScope,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    val reading: StateFlow<ThermalReading> = privilege.access
        .flatMapLatest { access -> poll(access) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, ThermalReading())

    val status: StateFlow<ThermalStatus> = callbackFlow {
        val listener = PowerManager.OnThermalStatusChangedListener { trySend(it.toThermalStatus()) }
        powerManager.addThermalStatusListener(listener)
        trySend(powerManager.currentThermalStatus.toThermalStatus())
        awaitClose { powerManager.removeThermalStatusListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, ThermalStatus.None)

    private fun poll(access: Access): Flow<ThermalReading> = flow {
        if (access.state == RootState.Checking) emit(ThermalReading(access = access))
        while (true) {
            val files = (if (access.granted) readPrivileged() else null) ?: readDirectly()
            emit(parse(files, access))
            delay(POLL_MS)
        }
    }

    /** sysfs path -> trimmed content, for every file in [GLOBS] that could be read. */
    private fun readDirectly(): Map<String, String> {
        val out = HashMap<String, String>()
        fun readAll(dir: String, prefix: String, names: List<String>) {
            File(dir).listFiles { f -> f.name.startsWith(prefix) }?.forEach { sub ->
                names.forEach { name ->
                    val file = File(sub, name)
                    runCatching { file.readText().trim() }.getOrNull()?.let { out[file.path] = it }
                }
            }
        }
        readAll(THERMAL_DIR, ZONE, listOf("type", "temp"))
        readAll(THERMAL_DIR, COOLING, listOf("type", "cur_state", "max_state"))
        readAll(CPU_DIR, "cpu", listOf("cpufreq/scaling_cur_freq", "cpufreq/cpuinfo_max_freq"))
        return out
    }

    private suspend fun readPrivileged(): Map<String, String>? {
        val output = privilege.run(ROOT_SCRIPT) ?: return null
        return output.lineSequence()
            .mapNotNull { line -> line.split('|', limit = 2).takeIf { it.size == 2 } }
            .filter { it[1].isNotBlank() }
            .associate { it[0].trim() to it[1].trim() }
    }

    private fun parse(files: Map<String, String>, access: Access): ThermalReading {
        val zones = indexed(files, "$THERMAL_DIR/$ZONE").mapNotNull { (i, base) ->
            val raw = files["$base/temp"]?.toLongOrNull() ?: return@mapNotNull null
            toZone(i, files["$base/type"].orEmpty(), raw)
        }
        val cooling = indexed(files, "$THERMAL_DIR/$COOLING").mapNotNull { (i, base) ->
            val max = files["$base/max_state"]?.toIntOrNull() ?: return@mapNotNull null
            val cur = files["$base/cur_state"]?.toIntOrNull() ?: return@mapNotNull null
            if (max <= 0) return@mapNotNull null
            CoolingDevice(i, files["$base/type"].orEmpty().ifBlank { "cooling_device$i" }, cur, max)
        }
        val cpus = indexed(files, "$CPU_DIR/cpu").mapNotNull { (i, base) ->
            val cur = files["$base/cpufreq/scaling_cur_freq"]?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            CpuCore(i, cur, files["$base/cpufreq/cpuinfo_max_freq"]?.toLongOrNull())
        }
        return ThermalReading(
            zones = zones,
            coolingDevices = cooling,
            cpuCores = cpus,
            headroom = powerManager.getThermalHeadroom(HEADROOM_FORECAST_S).takeIf { !it.isNaN() && it >= 0f },
            access = access,
            loaded = true,
        )
    }

    /** All distinct "<prefix><n>" directories mentioned in [files], sorted by n. */
    private fun indexed(files: Map<String, String>, prefix: String): List<Pair<Int, String>> =
        files.keys.asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull { path ->
                val rest = path.removePrefix(prefix)
                val digits = rest.takeWhile { it.isDigit() }
                digits.toIntOrNull()?.let { it to prefix + digits }
            }
            .distinct()
            .sortedBy { it.first }
            .toList()

    /** Most kernels report millidegrees, a few tenths or whole degrees. */
    private fun toZone(index: Int, type: String, raw: Long): ThermalZone? {
        val celsius = when {
            abs(raw) >= 1_000 -> raw / 1000f
            abs(raw) >= 200 -> raw / 10f
            else -> raw.toFloat()
        }
        if (celsius !in -40f..200f) return null
        return ThermalZone(index, type.ifBlank { "thermal_zone$index" }, celsius)
    }

    private fun Int.toThermalStatus() = ThermalStatus.entries.getOrElse(this) { ThermalStatus.Shutdown }

    private companion object {
        const val THERMAL_DIR = "/sys/class/thermal"
        const val CPU_DIR = "/sys/devices/system/cpu"
        const val ZONE = "thermal_zone"
        const val COOLING = "cooling_device"
        const val POLL_MS = 2_000L
        const val HEADROOM_FORECAST_S = 10
        const val ROOT_SCRIPT =
            "for f in /sys/class/thermal/thermal_zone*/type /sys/class/thermal/thermal_zone*/temp " +
                "/sys/class/thermal/cooling_device*/type /sys/class/thermal/cooling_device*/cur_state " +
                "/sys/class/thermal/cooling_device*/max_state " +
                "/sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq " +
                "/sys/devices/system/cpu/cpu[0-9]*/cpufreq/cpuinfo_max_freq; do " +
                "printf '%s|%s\\n' \"\$f\" \"\$(cat \$f 2>/dev/null)\"; done"
    }
}

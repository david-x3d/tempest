package io.github.davidx3d.tempest.data.thermal

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * Reads /sys/class/thermal/thermal_zone*. Without root only the zones SELinux lets an app read
 * are listed; with root every zone is read through a single `su -c` call per refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThermalRepository(
    context: Context,
    useRoot: Flow<Boolean>,
    scope: CoroutineScope,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    val reading: StateFlow<ThermalReading> = useRoot
        .distinctUntilChanged()
        .flatMapLatest { wantsRoot -> pollZones(wantsRoot) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, ThermalReading())

    val status: StateFlow<ThermalStatus> = callbackFlow {
        val listener = PowerManager.OnThermalStatusChangedListener { trySend(it.toThermalStatus()) }
        powerManager.addThermalStatusListener(listener)
        trySend(powerManager.currentThermalStatus.toThermalStatus())
        awaitClose { powerManager.removeThermalStatusListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, ThermalStatus.None)

    private fun pollZones(wantsRoot: Boolean): Flow<ThermalReading> = flow {
        var root = RootState.Off
        if (wantsRoot) {
            emit(ThermalReading(root = RootState.Checking))
            root = if (RootShell.isGranted()) RootState.Granted else RootState.Denied
        }
        while (true) {
            val zones = if (root == RootState.Granted) readWithRoot() ?: readDirectly() else readDirectly()
            emit(ThermalReading(zones = zones, root = root, loaded = true))
            delay(POLL_MS)
        }
    }

    private fun readDirectly(): List<ThermalZone> {
        val zoneDirs = File(THERMAL_DIR).listFiles { file -> file.name.startsWith(ZONE_PREFIX) }
            ?: return emptyList()
        return zoneDirs.mapNotNull { dir ->
            val index = dir.name.removePrefix(ZONE_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val raw = runCatching { File(dir, "temp").readText().trim().toLong() }.getOrNull()
                ?: return@mapNotNull null
            val type = runCatching { File(dir, "type").readText().trim() }.getOrDefault("")
            toZone(index, type, raw)
        }.sortedBy { it.index }
    }

    private suspend fun readWithRoot(): List<ThermalZone>? {
        val output = RootShell.run(ROOT_SCRIPT) ?: return null
        return output.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@mapNotNull null
            val index = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val raw = parts[2].trim().toLongOrNull() ?: return@mapNotNull null
            toZone(index, parts[1].trim(), raw)
        }.sortedBy { it.index }.toList()
    }

    /** Most kernels report millidegrees, a few tenths or whole degrees. */
    private fun toZone(index: Int, type: String, raw: Long): ThermalZone? {
        val celsius = when {
            kotlin.math.abs(raw) >= 1_000 -> raw / 1000f
            kotlin.math.abs(raw) >= 200 -> raw / 10f
            else -> raw.toFloat()
        }
        if (celsius !in -40f..200f) return null
        return ThermalZone(index, type.ifBlank { "thermal_zone$index" }, celsius)
    }

    private fun Int.toThermalStatus() = ThermalStatus.entries.getOrElse(this) { ThermalStatus.Shutdown }

    private companion object {
        const val THERMAL_DIR = "/sys/class/thermal"
        const val ZONE_PREFIX = "thermal_zone"
        const val POLL_MS = 3_000L
        const val ROOT_SCRIPT =
            "for z in /sys/class/thermal/thermal_zone*; do " +
                "printf '%s|%s|%s\\n' \"\${z##*thermal_zone}\" \"\$(cat \$z/type 2>/dev/null)\" \"\$(cat \$z/temp 2>/dev/null)\"; " +
                "done"
    }
}

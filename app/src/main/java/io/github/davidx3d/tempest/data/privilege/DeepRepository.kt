package io.github.davidx3d.tempest.data.privilege

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Readings that need Shizuku or root: the thermal HAL's named sensors (skin, modem, NPU...),
 * hwmon chips, charger temperatures and the battery's design and charge limits. Only polled
 * while a screen shows them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeepRepository(privilege: PrivilegeManager, scope: CoroutineScope) {

    val reading: StateFlow<DeepReading> = privilege.access
        .map { it.granted }
        .distinctUntilChanged()
        .flatMapLatest { granted ->
            flow {
                if (!granted) {
                    emit(DeepReading())
                    return@flow
                }
                while (true) {
                    privilege.run(SCRIPT, timeoutSeconds = 8)?.let { emit(DeepParser.parse(it)) }
                    delay(POLL_MS)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DeepReading())

    internal companion object {
        const val POLL_MS = 3_000L
        private val SUPPLY_FIELDS = listOf(
            "type", "online", "temp", "voltage_now", "current_now", "charge_full", "charge_full_design",
            "cycle_count", "charge_type", "constant_charge_current_max", "input_current_limit", "voltage_max",
        )
        val SCRIPT: String = buildString {
            append("for f in /sys/class/hwmon/hwmon*/name /sys/class/hwmon/hwmon*/temp*_input /sys/class/hwmon/hwmon*/temp*_label")
            SUPPLY_FIELDS.forEach { append(" /sys/class/power_supply/*/").append(it) }
            append("; do printf '%s|%s\\n' \"\$f\" \"\$(cat \$f 2>/dev/null)\"; done; echo '${DeepParser.MARKER}'; dumpsys thermalservice 2>/dev/null")
        }
    }
}

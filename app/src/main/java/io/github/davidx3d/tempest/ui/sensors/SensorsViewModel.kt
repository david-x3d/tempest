package io.github.davidx3d.tempest.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.sensors.SensorGroup
import io.github.davidx3d.tempest.data.sensors.SensorSpec
import io.github.davidx3d.tempest.data.sensors.SensorsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlin.math.sqrt

/** Latest values of one sensor plus a short history of their magnitude for the sparkline. */
data class SensorLive(val values: FloatArray, val history: List<Float>)

data class SensorsUiState(
    val sensors: List<SensorSpec> = emptyList(),
    val live: Map<Int, SensorLive> = emptyMap(),
    /** Null shows every group. */
    val filter: SensorGroup? = null,
) {
    val visible: List<SensorSpec> get() = if (filter == null) sensors else sensors.filter { it.group == filter }
    val groups: List<SensorGroup> get() = sensors.map { it.group }.distinct().sorted()
}

class SensorsViewModel(repository: SensorsRepository) : ViewModel() {

    private val filter = MutableStateFlow<SensorGroup?>(null)

    private val live = repository.values().scan(emptyMap<Int, SensorLive>()) { acc, values ->
        values.mapValues { (id, v) ->
            val magnitude = if (v.size >= 3) sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) else v.firstOrNull() ?: 0f
            val history = ((acc[id]?.history ?: emptyList()) + magnitude).takeLast(HISTORY)
            SensorLive(v, history)
        }
    }

    val uiState: StateFlow<SensorsUiState> =
        combine(live, filter) { values, group -> SensorsUiState(repository.sensors, values, group) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SensorsUiState(repository.sensors))

    fun setFilter(group: SensorGroup?) {
        filter.value = group
    }

    companion object {
        private const val HISTORY = 60

        val Factory = viewModelFactory {
            initializer {
                SensorsViewModel((this[APPLICATION_KEY] as TempestApp).container.sensors)
            }
        }
    }
}

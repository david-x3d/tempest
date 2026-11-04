package io.github.davidx3d.tempest.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.sensors.InstrumentAvailability
import io.github.davidx3d.tempest.data.sensors.InstrumentSensors
import io.github.davidx3d.tempest.data.sensors.Instruments
import io.github.davidx3d.tempest.data.sensors.SensorGroup
import io.github.davidx3d.tempest.data.sensors.SensorSpec
import io.github.davidx3d.tempest.data.sensors.SensorsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** The sensor opened in the detail sheet, with its latest values and the last few seconds of them. */
data class SensorDetail(val spec: SensorSpec, val values: FloatArray? = null, val history: List<FloatArray> = emptyList())

data class SensorsUiState(
    val sensors: List<SensorSpec> = emptyList(),
    val query: String = "",
    /** Null shows every group. */
    val filter: SensorGroup? = null,
    val instruments: Instruments = Instruments(),
    val available: InstrumentAvailability = InstrumentAvailability(),
    val detail: SensorDetail? = null,
) {
    val groups: List<SensorGroup> get() = sensors.map { it.group }.distinct().sorted()
    val vendors: Int get() = sensors.map { it.vendor }.distinct().size

    /** Matching sensors, grouped in the order of [SensorGroup]. */
    val visible: Map<SensorGroup, List<SensorSpec>>
        get() {
            val q = query.trim()
            return sensors
                .filter { filter == null || it.group == filter }
                .filter { q.isEmpty() || listOf(it.name, it.vendor, it.stringType).any { field -> field.contains(q, ignoreCase = true) } }
                .groupBy { it.group }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SensorsViewModel(private val repository: SensorsRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<SensorGroup?>(null)
    private val selected = MutableStateFlow<SensorSpec?>(null)
    private val instrumentSensors = InstrumentSensors(repository)

    /** Only the handful of sensors behind the instruments run while the tab is visible. */
    private val instruments = repository.listen(instrumentSensors.ids, periodMs = 66L)
        .map { instrumentSensors.read(it) }
        .onStart { emit(Instruments()) }

    private val detail = selected.flatMapLatest { spec ->
        if (spec == null) {
            flowOf(null)
        } else {
            repository.listen(setOf(spec.id))
                .scan(SensorDetail(spec)) { acc, values ->
                    val v = values[spec.id] ?: return@scan acc
                    acc.copy(values = v, history = (acc.history + v).takeLast(HISTORY))
                }
        }
    }

    val uiState: StateFlow<SensorsUiState> =
        combine(query, filter, instruments, detail) { q, group, inst, open ->
            SensorsUiState(repository.sensors, q, group, inst, instrumentSensors.available, open)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SensorsUiState(repository.sensors, available = instrumentSensors.available))

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(group: SensorGroup?) {
        filter.value = group
    }

    fun open(sensor: SensorSpec?) {
        selected.value = sensor
    }

    companion object {
        private const val HISTORY = 100

        val Factory = viewModelFactory {
            initializer {
                SensorsViewModel((this[APPLICATION_KEY] as TempestApp).container.sensors)
            }
        }
    }
}

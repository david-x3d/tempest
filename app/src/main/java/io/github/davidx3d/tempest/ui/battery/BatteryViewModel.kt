package io.github.davidx3d.tempest.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.battery.BatteryRepository
import io.github.davidx3d.tempest.data.battery.BatterySnapshot
import io.github.davidx3d.tempest.data.history.HistoryRepository
import io.github.davidx3d.tempest.data.history.Sample
import io.github.davidx3d.tempest.data.history.historyCsv
import io.github.davidx3d.tempest.data.history.trendPerMinute
import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BatteryUiState(
    val snapshot: BatterySnapshot? = null,
    val history: List<Sample> = emptyList(),
    val threshold: Float = Settings.DEFAULT_BATTERY_THRESHOLD,
    val trendPerMinute: Float? = null,
) {
    val isWarning: Boolean get() = snapshot != null && snapshot.temperatureCelsius >= threshold
}

class BatteryViewModel(
    battery: BatteryRepository,
    private val history: HistoryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<BatteryUiState> =
        combine(battery.snapshot, history.battery, settings.settings) { snapshot, samples, prefs ->
            BatteryUiState(snapshot, samples, prefs.batteryThreshold, samples.trendPerMinute())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryUiState())

    fun exportCsv(): String = historyCsv(history.battery.value, history.soc.value)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TempestApp).container
                BatteryViewModel(container.battery, container.history, container.settings)
            }
        }
    }
}

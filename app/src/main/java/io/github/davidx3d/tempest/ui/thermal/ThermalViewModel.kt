package io.github.davidx3d.tempest.ui.thermal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.history.HistoryRepository
import io.github.davidx3d.tempest.data.history.Sample
import io.github.davidx3d.tempest.data.history.historyCsv
import io.github.davidx3d.tempest.data.history.trendPerMinute
import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.data.settings.SettingsRepository
import io.github.davidx3d.tempest.data.thermal.ThermalReading
import io.github.davidx3d.tempest.data.thermal.ThermalRepository
import io.github.davidx3d.tempest.data.thermal.ThermalStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ThermalUiState(
    val reading: ThermalReading = ThermalReading(),
    val status: ThermalStatus = ThermalStatus.None,
    val socHistory: List<Sample> = emptyList(),
    val threshold: Float = Settings.DEFAULT_SOC_THRESHOLD,
) {
    val socWarning: Boolean get() = (reading.socZone?.celsius ?: 0f) >= threshold
    val trendPerMinute: Float? get() = socHistory.trendPerMinute()
}

class ThermalViewModel(
    thermal: ThermalRepository,
    private val history: HistoryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<ThermalUiState> =
        combine(
            thermal.reading,
            thermal.status,
            history.soc,
            settings.settings,
        ) { reading, status, soc, prefs ->
            ThermalUiState(reading, status, soc, prefs.socThreshold)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThermalUiState())

    fun exportCsv(): String = historyCsv(history.battery.value, history.soc.value)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TempestApp).container
                ThermalViewModel(container.thermal, container.history, container.settings)
            }
        }
    }
}

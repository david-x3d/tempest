package io.github.davidx3d.tempest.ui.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.device.DeviceInfo
import io.github.davidx3d.tempest.data.device.DeviceRepository
import io.github.davidx3d.tempest.data.device.LiveChip
import io.github.davidx3d.tempest.data.thermal.CpuCore
import io.github.davidx3d.tempest.data.thermal.RootState
import io.github.davidx3d.tempest.data.thermal.ThermalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class CpuUiState(
    val info: DeviceInfo? = null,
    val cores: List<CpuCore> = emptyList(),
    val socCelsius: Float? = null,
    val live: LiveChip = LiveChip(),
    val root: RootState = RootState.Off,
) {
    /** Average of current/max clock over all online cores, 0..1. */
    val clockLoad: Float?
        get() = cores.mapNotNull { core -> core.maxKhz?.takeIf { it > 0 }?.let { core.currentKhz.toFloat() / it } }
            .takeIf { it.isNotEmpty() }?.average()?.toFloat()?.coerceIn(0f, 1f)
}

class CpuViewModel(device: DeviceRepository, thermal: ThermalRepository) : ViewModel() {

    val uiState: StateFlow<CpuUiState> =
        combine(
            device.info,
            thermal.reading,
            device.live(thermal.reading.map { it.root == RootState.Granted }).onStart { emit(LiveChip()) },
        ) { info, reading, live ->
            CpuUiState(info, reading.cpuCores, reading.socZone?.celsius, live, reading.root)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CpuUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TempestApp).container
                CpuViewModel(container.device, container.thermal)
            }
        }
    }
}

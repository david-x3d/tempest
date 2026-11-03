package io.github.davidx3d.tempest.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.data.settings.SettingsRepository
import io.github.davidx3d.tempest.data.thermal.RootState
import io.github.davidx3d.tempest.data.thermal.ThermalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App languages offered in the picker; null tag means "follow the system". */
enum class AppLanguage(val tag: String?) { System(null), English("en"), German("de"), French("fr"), Italian("it") }

data class SettingsUiState(
    val settings: Settings = Settings(),
    val root: RootState = RootState.Off,
    val language: AppLanguage = AppLanguage.System,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    thermal: ThermalRepository,
) : ViewModel() {

    private val language = MutableStateFlow(currentLanguage())

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.settings, thermal.reading.map { it.root }, language.asStateFlow()) { settings, root, lang ->
            SettingsUiState(settings, root, lang)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(repository.settings.value))

    fun setBatteryThreshold(value: Float) = viewModelScope.launch { repository.setBatteryThreshold(value) }

    fun setSocThreshold(value: Float) = viewModelScope.launch { repository.setSocThreshold(value) }

    fun setUseRoot(value: Boolean) = viewModelScope.launch { repository.setUseRoot(value) }

    /** Recreates the activity in the new language; Android 13+ also shows it in system settings. */
    fun setLanguage(value: AppLanguage) {
        language.value = value
        AppCompatDelegate.setApplicationLocales(
            value.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList(),
        )
    }

    private fun currentLanguage(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().get(0)?.language
        return AppLanguage.entries.firstOrNull { it.tag != null && it.tag == tag } ?: AppLanguage.System
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TempestApp).container
                SettingsViewModel(container.settings, container.thermal)
            }
        }
    }
}

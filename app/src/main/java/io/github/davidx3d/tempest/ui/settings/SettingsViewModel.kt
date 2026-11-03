package io.github.davidx3d.tempest.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.alerts.AlertNotifier
import io.github.davidx3d.tempest.alerts.AlertSource
import io.github.davidx3d.tempest.data.battery.BatteryRepository
import io.github.davidx3d.tempest.data.privilege.Access
import io.github.davidx3d.tempest.data.privilege.PrivilegeManager
import io.github.davidx3d.tempest.data.privilege.ShizukuShell
import io.github.davidx3d.tempest.data.privilege.ShizukuStatus
import io.github.davidx3d.tempest.data.settings.HeatLevels
import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App languages offered in the picker; null tag means "follow the system". */
enum class AppLanguage(val tag: String?) { System(null), English("en"), German("de"), French("fr"), Italian("it") }

data class SettingsUiState(
    val settings: Settings = Settings(),
    val access: Access = Access(),
    val shizuku: ShizukuStatus = ShizukuStatus.NotInstalled,
    val language: AppLanguage = AppLanguage.System,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    privilege: PrivilegeManager,
    private val battery: BatteryRepository,
) : ViewModel() {

    private val language = MutableStateFlow(currentLanguage())

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.settings, privilege.access, ShizukuShell.status, language.asStateFlow()) { settings, access, shizuku, lang ->
            SettingsUiState(settings, access, shizuku, lang)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(repository.settings.value))

    fun setBatteryThreshold(value: Float) = viewModelScope.launch { repository.setBatteryThreshold(value) }

    fun setSocThreshold(value: Float) = viewModelScope.launch { repository.setSocThreshold(value) }

    fun setUseRoot(value: Boolean) = viewModelScope.launch { repository.setUseRoot(value) }

    fun setUseShizuku(value: Boolean) = viewModelScope.launch {
        repository.setUseShizuku(value)
        if (value) {
            ShizukuShell.refresh()
            ShizukuShell.requestPermission()
        }
    }

    fun setOnlineLookup(value: Boolean) = viewModelScope.launch { repository.setOnlineLookup(value) }

    fun requestShizukuPermission() = ShizukuShell.requestPermission()

    fun refreshShizuku() = ShizukuShell.refresh()

    fun setFahrenheit(value: Boolean) = viewModelScope.launch { repository.setFahrenheit(value) }

    fun setAlertsEnabled(value: Boolean) = viewModelScope.launch { repository.setAlertsEnabled(value) }

    fun setLevels(value: HeatLevels) = viewModelScope.launch { repository.setLevels(value) }

    fun setAlertRepeatMinutes(value: Int) = viewModelScope.launch { repository.setAlertRepeatMinutes(value) }

    /** Shows the real big alert with the current battery temperature, so you know what to expect. */
    fun sendTestAlert(context: Context) {
        val settings = repository.settings.value
        val celsius = battery.snapshot.value?.temperatureCelsius ?: settings.batteryThreshold
        AlertNotifier(context).apply {
            ensureChannels()
            showAlert(AlertSource.Battery, celsius, settings.batteryThreshold, settings.fahrenheit)
        }
    }

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
                SettingsViewModel(container.settings, container.privilege, container.battery)
            }
        }
    }
}

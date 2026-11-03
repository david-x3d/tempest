package io.github.davidx3d.tempest.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Settings(
    val batteryThreshold: Float = DEFAULT_BATTERY_THRESHOLD,
    val socThreshold: Float = DEFAULT_SOC_THRESHOLD,
    val useRoot: Boolean = false,
) {
    companion object {
        const val DEFAULT_BATTERY_THRESHOLD = 40f
        const val DEFAULT_SOC_THRESHOLD = 45f
        val BATTERY_RANGE = 30f..60f
        val SOC_RANGE = 35f..95f
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Thresholds and the root toggle. The app language is stored by AppCompat/LocaleManager. */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.dataStore

    val settings: StateFlow<Settings> = store.data
        .map { prefs ->
            Settings(
                batteryThreshold = prefs[BATTERY_THRESHOLD] ?: Settings.DEFAULT_BATTERY_THRESHOLD,
                socThreshold = prefs[SOC_THRESHOLD] ?: Settings.DEFAULT_SOC_THRESHOLD,
                useRoot = prefs[USE_ROOT] ?: false,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun setBatteryThreshold(value: Float) = store.edit { it[BATTERY_THRESHOLD] = value }

    suspend fun setSocThreshold(value: Float) = store.edit { it[SOC_THRESHOLD] = value }

    suspend fun setUseRoot(value: Boolean) = store.edit { it[USE_ROOT] = value }

    private companion object {
        val BATTERY_THRESHOLD = floatPreferencesKey("battery_threshold")
        val SOC_THRESHOLD = floatPreferencesKey("soc_threshold")
        val USE_ROOT = booleanPreferencesKey("use_root")
    }
}

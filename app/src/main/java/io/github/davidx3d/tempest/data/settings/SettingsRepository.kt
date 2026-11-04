package io.github.davidx3d.tempest.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Where one heat level ends and the next begins, in °C. Always ascending with at least
 * [MIN_GAP] between neighbours, so every level keeps a visible slice of the colour scale.
 */
data class HeatLevels(
    val coldBelow: Float = 20f,
    val warmFrom: Float = 33f,
    val hotFrom: Float = 40f,
    val ultraFrom: Float = 48f,
) {
    fun sanitized(): HeatLevels {
        val cold = coldBelow.coerceIn(RANGE.start, RANGE.endInclusive - 3 * MIN_GAP)
        val warm = warmFrom.coerceIn(cold + MIN_GAP, RANGE.endInclusive - 2 * MIN_GAP)
        val hot = hotFrom.coerceIn(warm + MIN_GAP, RANGE.endInclusive - MIN_GAP)
        val ultra = ultraFrom.coerceIn(hot + MIN_GAP, RANGE.endInclusive)
        return HeatLevels(cold, warm, hot, ultra)
    }

    companion object {
        const val MIN_GAP = 3f
        val RANGE = 0f..90f

        /** Lets the levels sit higher for gaming or lower for a phone that should stay cool. */
        val PRESETS = listOf(
            HeatLevels(),
            HeatLevels(coldBelow = 22f, warmFrom = 36f, hotFrom = 44f, ultraFrom = 52f),
            HeatLevels(coldBelow = 18f, warmFrom = 30f, hotFrom = 37f, ultraFrom = 44f),
        )
    }
}

data class Settings(
    val batteryThreshold: Float = DEFAULT_BATTERY_THRESHOLD,
    val socThreshold: Float = DEFAULT_SOC_THRESHOLD,
    val useRoot: Boolean = false,
    val useShizuku: Boolean = false,
    /** Looks unknown chip codes up on Wikipedia. The only feature that uses the internet. */
    val onlineLookup: Boolean = false,
    val levels: HeatLevels = HeatLevels(),
    val alertsEnabled: Boolean = false,
    val fahrenheit: Boolean = false,
    /** Re-alert every N minutes while still above the threshold; 0 alerts once per crossing. */
    val alertRepeatMinutes: Int = 0,
    /** Keeps recording through a Shizuku daemon while the app is closed, without a notification. */
    val backgroundRecording: Boolean = false,
    val recordIntervalSeconds: Int = 60,
) {
    companion object {
        val REPEAT_OPTIONS = listOf(0, 2, 5, 10)
        val RECORD_INTERVALS = listOf(30, 60, 120, 300)
        const val DEFAULT_BATTERY_THRESHOLD = 40f
        const val DEFAULT_SOC_THRESHOLD = 45f
        val BATTERY_RANGE = 30f..60f
        val SOC_RANGE = 35f..95f
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Thresholds, heat levels and toggles. The app language is stored by AppCompat/LocaleManager. */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.dataStore

    val settings: StateFlow<Settings> = store.data
        .map { prefs ->
            val defaults = HeatLevels()
            Settings(
                batteryThreshold = prefs[BATTERY_THRESHOLD] ?: Settings.DEFAULT_BATTERY_THRESHOLD,
                socThreshold = prefs[SOC_THRESHOLD] ?: Settings.DEFAULT_SOC_THRESHOLD,
                useRoot = prefs[USE_ROOT] ?: false,
                useShizuku = prefs[USE_SHIZUKU] ?: false,
                onlineLookup = prefs[ONLINE_LOOKUP] ?: false,
                levels = HeatLevels(
                    coldBelow = prefs[COLD_BELOW] ?: defaults.coldBelow,
                    warmFrom = prefs[WARM_FROM] ?: defaults.warmFrom,
                    hotFrom = prefs[HOT_FROM] ?: defaults.hotFrom,
                    ultraFrom = prefs[ULTRA_FROM] ?: defaults.ultraFrom,
                ).sanitized(),
                alertsEnabled = prefs[ALERTS_ENABLED] ?: false,
                fahrenheit = prefs[FAHRENHEIT] ?: false,
                alertRepeatMinutes = prefs[ALERT_REPEAT] ?: 0,
                backgroundRecording = prefs[BACKGROUND_RECORDING] ?: false,
                recordIntervalSeconds = prefs[RECORD_INTERVAL] ?: 60,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun setBatteryThreshold(value: Float) = store.edit { it[BATTERY_THRESHOLD] = value }

    suspend fun setSocThreshold(value: Float) = store.edit { it[SOC_THRESHOLD] = value }

    suspend fun setUseRoot(value: Boolean) = store.edit { it[USE_ROOT] = value }

    suspend fun setUseShizuku(value: Boolean) = store.edit { it[USE_SHIZUKU] = value }

    suspend fun setOnlineLookup(value: Boolean) = store.edit { it[ONLINE_LOOKUP] = value }

    suspend fun setAlertsEnabled(value: Boolean) = store.edit { it[ALERTS_ENABLED] = value }

    suspend fun setFahrenheit(value: Boolean) = store.edit { it[FAHRENHEIT] = value }

    suspend fun setAlertRepeatMinutes(value: Int) = store.edit { it[ALERT_REPEAT] = value }

    suspend fun setBackgroundRecording(value: Boolean) = store.edit { it[BACKGROUND_RECORDING] = value }

    suspend fun setRecordIntervalSeconds(value: Int) = store.edit { it[RECORD_INTERVAL] = value }

    suspend fun setLevels(levels: HeatLevels) = store.edit {
        val clean = levels.sanitized()
        it[COLD_BELOW] = clean.coldBelow
        it[WARM_FROM] = clean.warmFrom
        it[HOT_FROM] = clean.hotFrom
        it[ULTRA_FROM] = clean.ultraFrom
    }

    private companion object {
        val BATTERY_THRESHOLD = floatPreferencesKey("battery_threshold")
        val SOC_THRESHOLD = floatPreferencesKey("soc_threshold")
        val USE_ROOT = booleanPreferencesKey("use_root")
        val USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
        val ONLINE_LOOKUP = booleanPreferencesKey("online_lookup")
        val COLD_BELOW = floatPreferencesKey("level_cold_below")
        val WARM_FROM = floatPreferencesKey("level_warm_from")
        val HOT_FROM = floatPreferencesKey("level_hot_from")
        val ULTRA_FROM = floatPreferencesKey("level_ultra_from")
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val FAHRENHEIT = booleanPreferencesKey("fahrenheit")
        val ALERT_REPEAT = intPreferencesKey("alert_repeat_minutes")
        val BACKGROUND_RECORDING = booleanPreferencesKey("background_recording")
        val RECORD_INTERVAL = intPreferencesKey("record_interval_seconds")
    }
}

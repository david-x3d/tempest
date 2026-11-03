package io.github.davidx3d.tempest

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidx3d.tempest.alerts.MonitorService
import io.github.davidx3d.tempest.ui.LocalFahrenheit
import io.github.davidx3d.tempest.ui.TempestRoot
import io.github.davidx3d.tempest.ui.theme.HeatScale
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale
import io.github.davidx3d.tempest.ui.theme.TempestTheme

/** AppCompatActivity so AppCompatDelegate can switch the app language on Android 12 as well. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = (application as TempestApp).container.settings.settings
        // Background alerts survive the app being swiped away; bring the service back if needed.
        if (settingsFlow.value.alertsEnabled) MonitorService.start(this)

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            val heatScale = remember(settings.levels) { HeatScale(settings.levels) }
            CompositionLocalProvider(
                LocalHeatScale provides heatScale,
                LocalFahrenheit provides settings.fahrenheit,
            ) {
                TempestTheme {
                    TempestRoot()
                }
            }
        }
    }
}

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
    private val container get() = (application as TempestApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = container.settings.settings
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

    override fun onStart() {
        super.onStart()
        container.foreground.value = true
        // Fill the chart with what the background recorder saw while the app was closed.
        container.recorder.syncLater()
    }

    override fun onStop() {
        container.foreground.value = false
        super.onStop()
    }
}

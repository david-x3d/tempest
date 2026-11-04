package io.github.davidx3d.tempest.ui

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.data.settings.socLevels
import io.github.davidx3d.tempest.ui.theme.HeatScale
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale
import io.github.davidx3d.tempest.ui.overview.OverviewScreen
import androidx.compose.material.icons.rounded.Dashboard
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.ui.battery.BatteryScreen
import io.github.davidx3d.tempest.ui.cpu.CpuScreen
import io.github.davidx3d.tempest.ui.sensors.SensorsScreen
import io.github.davidx3d.tempest.ui.settings.SettingsScreen
import io.github.davidx3d.tempest.ui.thermal.ThermalScreen

enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    Overview(R.string.overview_title, Icons.Rounded.Dashboard),
    Battery(R.string.nav_battery, Icons.Rounded.BatteryFull),
    Thermal(R.string.nav_thermal, Icons.Rounded.DeviceThermostat),
    Cpu(R.string.nav_cpu, Icons.Rounded.Memory),
    Sensors(R.string.nav_sensors, Icons.Rounded.Sensors),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

val startDestinations = listOf(Destination.Settings, Destination.Overview, Destination.Battery, Destination.Thermal, Destination.Cpu, Destination.Sensors)

@Composable
fun TempestRoot() {
    val app = LocalContext.current.applicationContext as TempestApp
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    if (!settings.loaded) return
    val start = startDestinations.getOrElse(settings.startTab) { Destination.Settings }
    var current by rememberSaveable { mutableStateOf(start) }
    androidx.activity.compose.BackHandler(enabled = current != start) { current = start }
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                Destination.entries.filter { it != Destination.Sensors }.forEach { destination ->
                    ShortNavigationBarItem(
                        selected = destination == current,
                        onClick = {
                            if (destination != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            current = destination
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        // Five tabs leave little room: long labels like "Einstellungen" shrink
                        // to fit on one line instead of wrapping.
                        label = {
                            Text(
                                stringResource(destination.label),
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp,
                                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                ),
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                if (!settings.animations) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None else
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.04f))
            },
            label = "destination",
        ) { destination ->
            CompositionLocalProvider(LocalHeatScale provides HeatScale(if (destination == Destination.Thermal || destination == Destination.Cpu) settings.socLevels else settings.levels)) {
                when (destination) {
                    Destination.Overview -> OverviewScreen(padding) { current = it }
                    Destination.Battery -> BatteryScreen(padding)
                    Destination.Thermal -> ThermalScreen(padding)
                    Destination.Cpu -> CpuScreen(padding)
                    Destination.Sensors -> SensorsScreen(padding)
                    Destination.Settings -> SettingsScreen(padding)
            }
            }
        }
    }
}

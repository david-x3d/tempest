package io.github.davidx3d.tempest.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.DeviceThermostat
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
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.ui.battery.BatteryScreen
import io.github.davidx3d.tempest.ui.settings.SettingsScreen
import io.github.davidx3d.tempest.ui.thermal.ThermalScreen

enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    Battery(R.string.nav_battery, Icons.Rounded.BatteryFull),
    Thermal(R.string.nav_thermal, Icons.Rounded.DeviceThermostat),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

@Composable
fun TempestRoot() {
    var current by rememberSaveable { mutableStateOf(Destination.Battery) }
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                Destination.entries.forEach { destination ->
                    ShortNavigationBarItem(
                        selected = destination == current,
                        onClick = {
                            if (destination != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            current = destination
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.04f))
            },
            label = "destination",
        ) { destination ->
            when (destination) {
                Destination.Battery -> BatteryScreen(padding)
                Destination.Thermal -> ThermalScreen(padding)
                Destination.Settings -> SettingsScreen(padding)
            }
        }
    }
}

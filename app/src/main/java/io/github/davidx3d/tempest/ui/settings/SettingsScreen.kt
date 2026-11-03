package io.github.davidx3d.tempest.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.settings.HeatLevels
import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.alerts.MonitorService
import io.github.davidx3d.tempest.ui.theme.HeatScale
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.davidx3d.tempest.data.thermal.RootState
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall)
        }
        item { UnitCard(state.settings.fahrenheit, viewModel::setFahrenheit) }
        item { LevelsCard(state.settings.levels, viewModel::setLevels) }
        item {
            SectionCard(
                title = stringResource(R.string.settings_thresholds),
                subtitle = stringResource(R.string.settings_thresholds_body),
            ) {
                ThresholdSlider(
                    label = stringResource(R.string.settings_threshold_battery),
                    value = state.settings.batteryThreshold,
                    range = Settings.BATTERY_RANGE,
                    onCommit = viewModel::setBatteryThreshold,
                )
                ThresholdSlider(
                    label = stringResource(R.string.settings_threshold_soc),
                    value = state.settings.socThreshold,
                    range = Settings.SOC_RANGE,
                    onCommit = viewModel::setSocThreshold,
                )
            }
        }
        item {
            AlertsCard(
                enabled = state.settings.alertsEnabled,
                onChange = viewModel::setAlertsEnabled,
                repeatMinutes = state.settings.alertRepeatMinutes,
                onRepeatChange = viewModel::setAlertRepeatMinutes,
                onTest = viewModel::sendTestAlert,
            )
        }
        item { RootCard(state.settings.useRoot, state.root, viewModel::setUseRoot) }
        item { LanguageCard(state.language, viewModel::setLanguage) }
        item { AboutCard() }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var dragged by remember(value) { mutableFloatStateOf(value) }
    val color = LocalHeatScale.current.color(dragged)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(celsius(dragged, 0), style = MaterialTheme.typography.headlineSmall, color = color)
        }
        Slider(
            value = dragged,
            onValueChange = { new ->
                val snapped = new.roundToInt().toFloat()
                if (snapped != dragged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                dragged = snapped
            },
            onValueChangeFinished = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onCommit(dragged)
            },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                activeTickColor = color.copy(alpha = 0f),
                inactiveTickColor = color.copy(alpha = 0f),
            ),
        )
    }
}

@Composable
private fun UnitCard(fahrenheit: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    SectionCard(title = stringResource(R.string.settings_unit)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to R.string.unit_option_celsius, true to R.string.unit_option_fahrenheit).forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = fahrenheit == value,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onChange(value)
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

/** Four sliders for the boundaries between the five heat levels, with a live colour preview. */
@Composable
private fun LevelsCard(levels: HeatLevels, onCommit: (HeatLevels) -> Unit) {
    var draft by remember(levels) { mutableStateOf(levels) }
    val preview = remember(draft) { HeatScale(draft.sanitized()) }
    val range = HeatLevels.RANGE
    SectionCard(
        title = stringResource(R.string.settings_levels),
        subtitle = stringResource(R.string.settings_levels_body),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    Brush.horizontalGradient(
                        (0..20).map { i -> preview.color(range.start + (range.endInclusive - range.start) * i / 20f) },
                    ),
                ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.preset_default, R.string.preset_gaming, R.string.preset_careful)
                .zip(HeatLevels.PRESETS)
                .forEach { (label, preset) ->
                    FilterChip(
                        selected = draft == preset,
                        onClick = {
                            draft = preset
                            onCommit(preset)
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
        }
        CompositionLocalProvider(LocalHeatScale provides preview) {
            ThresholdSlider(stringResource(R.string.level_cold_below), draft.coldBelow, range, onCommit = {
                draft = draft.copy(coldBelow = it).sanitized(); onCommit(draft)
            })
            ThresholdSlider(stringResource(R.string.level_warm_from), draft.warmFrom, range, onCommit = {
                draft = draft.copy(warmFrom = it).sanitized(); onCommit(draft)
            })
            ThresholdSlider(stringResource(R.string.level_hot_from), draft.hotFrom, range, onCommit = {
                draft = draft.copy(hotFrom = it).sanitized(); onCommit(draft)
            })
            ThresholdSlider(stringResource(R.string.level_ultra_from), draft.ultraFrom, range, onCommit = {
                draft = draft.copy(ultraFrom = it).sanitized(); onCommit(draft)
            })
        }
    }
}

/**
 * Background alerts need the notification permission on Android 13+. Full-screen alerts need a
 * separate grant on Android 14+, which only the system settings page can give.
 */
@Composable
private fun AlertsCard(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    repeatMinutes: Int,
    onRepeatChange: (Int) -> Unit,
    onTest: (Context) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var fullScreenAllowed by remember { mutableStateOf(canUseFullScreen(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        fullScreenAllowed = canUseFullScreen(context)
        onPauseOrDispose {}
    }

    fun turnOn() {
        onChange(true)
        MonitorService.start(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        if (granted) turnOn()
    }

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch) { on ->
                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    when {
                        !on -> {
                            onChange(false)
                            MonitorService.stop(context)
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed ->
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> turnOn()
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_alerts), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_alerts_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!notificationsAllowed) {
                    Text(
                        stringResource(R.string.settings_alerts_blocked),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = enabled && notificationsAllowed, onCheckedChange = null)
        }
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_fullscreen), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_fullscreen_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (fullScreenAllowed) {
                    Text(
                        stringResource(R.string.settings_fullscreen_allowed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    FilledTonalButton(onClick = {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                        )
                    }) { Text(stringResource(R.string.settings_fullscreen_allow)) }
                }
            }
        }
        if (enabled && notificationsAllowed) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_alert_repeat), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_alert_repeat_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val options = Settings.REPEAT_OPTIONS
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, minutes ->
                        SegmentedButton(
                            selected = repeatMinutes == minutes,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onRepeatChange(minutes)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) {
                            Text(
                                if (minutes == 0) {
                                    stringResource(R.string.settings_alert_repeat_off)
                                } else {
                                    stringResource(R.string.duration_minutes, minutes)
                                },
                            )
                        }
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTest(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_alert_test)) }
        }
    }
}

private fun canUseFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

@Composable
private fun RootCard(enabled: Boolean, root: RootState, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch) {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onChange(it)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_root), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_root_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabled) {
                    Text(
                        stringResource(
                            when (root) {
                                RootState.Checking -> R.string.root_checking
                                RootState.Granted -> R.string.root_granted
                                RootState.Denied -> R.string.root_denied
                                RootState.Off -> R.string.root_off
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (root == RootState.Denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = enabled,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        if (enabled) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                    )
                },
                colors = SwitchDefaults.colors(),
            )
        }
    }
}

@Composable
private fun LanguageCard(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val haptics = LocalHapticFeedback.current
    SectionCard(title = stringResource(R.string.settings_language)) {
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = language == selected, role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onSelect(language)
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = language == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(language.label()), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    SectionCard(
        title = stringResource(R.string.settings_about),
        subtitle = stringResource(R.string.settings_about_body),
    ) {
        Text(
            stringResource(R.string.settings_version, version),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AppLanguage.label() = when (this) {
    AppLanguage.System -> R.string.settings_language_system
    AppLanguage.English -> R.string.language_en
    AppLanguage.German -> R.string.language_de
    AppLanguage.French -> R.string.language_fr
    AppLanguage.Italian -> R.string.language_it
}

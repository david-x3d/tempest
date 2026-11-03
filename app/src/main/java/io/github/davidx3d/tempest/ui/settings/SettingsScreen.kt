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
import io.github.davidx3d.tempest.data.settings.Settings
import io.github.davidx3d.tempest.data.thermal.RootState
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.theme.HeatScale
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
    val color = HeatScale.color(dragged)

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

package io.github.davidx3d.tempest.ui.thermal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.thermal.RootState
import io.github.davidx3d.tempest.data.thermal.ThermalStatus
import io.github.davidx3d.tempest.data.thermal.ThermalZone
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.components.CardShape
import io.github.davidx3d.tempest.ui.components.HistoryCard
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.components.TemperatureHero
import io.github.davidx3d.tempest.ui.components.WarningBanner
import io.github.davidx3d.tempest.ui.components.rememberWaveArmed
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.theme.HeatScale

@Composable
fun ThermalScreen(
    contentPadding: PaddingValues,
    viewModel: ThermalViewModel = viewModel(factory = ThermalViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reading = state.reading
    val soc = reading.socZone

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.socWarning) {
        if (state.socWarning) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Header(state.reading.zones.size, reading.root, state.status) }

        if (soc != null) {
            item { TemperatureHero(celsiusValue = soc.celsius, label = stringResource(R.string.thermal_soc)) }
            item {
                AnimatedVisibility(state.socWarning) {
                    WarningBanner(stringResource(R.string.thermal_soc_warning, celsius(state.threshold, 0)))
                }
            }
            item { HistoryCard(state.socHistory, state.threshold) }
        }

        when {
            !reading.loaded -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
            }
            reading.zones.isEmpty() -> item { EmptyState() }
            else -> items(reading.zones.sortedByDescending { it.celsius }, key = { it.index }) { zone ->
                ZoneRow(zone, warning = state.threshold, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun Header(count: Int, root: RootState, status: ThermalStatus) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.thermal_title), style = MaterialTheme.typography.displaySmall)
        if (count > 0) {
            Text(
                pluralStringResource(R.plurals.thermal_zone_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        stringResource(
                            when (root) {
                                RootState.Off -> R.string.root_off
                                RootState.Checking -> R.string.root_checking
                                RootState.Granted -> R.string.root_granted
                                RootState.Denied -> R.string.root_denied
                            },
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        if (root == RootState.Granted) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        contentDescription = null,
                    )
                },
            )
        }
        Text(
            stringResource(R.string.thermal_status, stringResource(status.label())),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ZoneRow(zone: ThermalZone, warning: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(zone.celsius, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "zone")
    val color by animateColorAsState(HeatScale.color(zone.celsius), label = "zoneColor")
    val heat = HeatScale.fraction(shown)
    val waveArmed = rememberWaveArmed()
    val container = if (zone.celsius >= warning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(color.copy(alpha = 0.2f), MaterialShapes.Cookie6Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Thermostat, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        zone.type,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.thermal_zone_index, zone.index),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(celsius(shown), style = MaterialTheme.typography.headlineSmall, color = color)
            }
            LinearWavyProgressIndicator(
                progress = { heat },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                amplitude = { if (waveArmed) 0.2f + 0.8f * heat else 0f },
                wavelength = (40 - 24 * heat).dp,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    SectionCard(
        title = stringResource(R.string.thermal_empty_title),
        subtitle = stringResource(R.string.thermal_empty_body),
    ) {}
}

private fun ThermalStatus.label() = when (this) {
    ThermalStatus.None -> R.string.thermal_status_none
    ThermalStatus.Light -> R.string.thermal_status_light
    ThermalStatus.Moderate -> R.string.thermal_status_moderate
    ThermalStatus.Severe -> R.string.thermal_status_severe
    ThermalStatus.Critical -> R.string.thermal_status_critical
    ThermalStatus.Emergency -> R.string.thermal_status_emergency
    ThermalStatus.Shutdown -> R.string.thermal_status_shutdown
}

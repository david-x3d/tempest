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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.privilege.Access
import io.github.davidx3d.tempest.data.privilege.ExtraSource
import io.github.davidx3d.tempest.data.privilege.ExtraTemp
import io.github.davidx3d.tempest.data.privilege.PrivilegeMode
import io.github.davidx3d.tempest.data.thermal.CoolingDevice
import io.github.davidx3d.tempest.data.thermal.RootState
import io.github.davidx3d.tempest.data.thermal.ThermalStatus
import io.github.davidx3d.tempest.data.thermal.ThermalZone
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.components.CardShape
import io.github.davidx3d.tempest.ui.components.HeatBar
import io.github.davidx3d.tempest.ui.components.HistoryCard
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.components.TemperatureHero
import io.github.davidx3d.tempest.ui.components.WarningBanner
import io.github.davidx3d.tempest.ui.formatUnit
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale

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
        item { Header(reading.zones.size, reading.access, state.status) }

        if (soc != null) {
            item {
                TemperatureHero(
                    celsiusValue = soc.celsius,
                    label = stringResource(R.string.thermal_soc),
                    trendPerMinute = state.trendPerMinute,
                )
            }
            item {
                AnimatedVisibility(state.socWarning) {
                    WarningBanner(stringResource(R.string.thermal_soc_warning, celsius(state.threshold, 0)))
                }
            }
            item { HistoryCard(state.socHistory, state.threshold, csv = viewModel::exportCsv) }
        }

        reading.headroom?.let { headroom -> item { HeadroomCard(headroom) } }

        when {
            !reading.loaded -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
            }
            reading.zones.isEmpty() -> item { EmptyState() }
            else -> items(reading.zones.sortedByDescending { it.celsius }, key = { "zone${it.index}" }) { zone ->
                ZoneRow(zone, warning = state.threshold, modifier = Modifier.animateItem())
            }
        }

        if (state.deep.temps.isNotEmpty()) item { ExtraTempsCard(state.deep.temps, state.threshold) }
        if (reading.coolingDevices.isNotEmpty()) item { CoolingCard(reading.coolingDevices) }
    }
}

@Composable
private fun Header(count: Int, access: Access, status: ThermalStatus) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.thermal_title), style = MaterialTheme.typography.displaySmall)
        if (count > 0) {
            Text(
                pluralStringResource(R.plurals.thermal_zone_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = {},
            label = {
                Text(
                    stringResource(
                        when (access.mode to access.state) {
                            PrivilegeMode.Shizuku to RootState.Checking -> R.string.shizuku_checking
                            PrivilegeMode.Shizuku to RootState.Granted -> R.string.shizuku_granted
                            PrivilegeMode.Shizuku to RootState.Denied -> R.string.shizuku_denied
                            else -> when (access.state) {
                                RootState.Off -> R.string.root_off
                                RootState.Checking -> R.string.root_checking
                                RootState.Granted -> R.string.root_granted
                                RootState.Denied -> R.string.root_denied
                            }
                        },
                    ),
                )
            },
            leadingIcon = {
                Icon(if (access.granted) Icons.Rounded.LockOpen else Icons.Rounded.Lock, contentDescription = null)
            },
        )
        Text(
            stringResource(R.string.thermal_status, stringResource(status.label())),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Badge(icon: ImageVector, color: Color, shape: RoundedPolygon = MaterialShapes.Cookie6Sided) {
    Box(
        Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.2f), shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ZoneRow(zone: ThermalZone, warning: Float, modifier: Modifier = Modifier) {
    val scale = LocalHeatScale.current
    val shown by animateFloatAsState(zone.celsius, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "zone")
    val color by animateColorAsState(scale.color(zone.celsius), label = "zoneColor")
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
                Badge(Icons.Rounded.Thermostat, color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(zone.type, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.thermal_zone_index, zone.index),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(celsius(shown), style = MaterialTheme.typography.headlineSmall, color = color)
            }
            HeatBar(scale.fraction(zone.celsius), color)
        }
    }
}

@Composable
private fun HeadroomCard(headroom: Float) {
    val color = when {
        headroom >= 1f -> MaterialTheme.colorScheme.error
        headroom >= 0.7f -> Color(0xFFFF7A1F)
        else -> MaterialTheme.colorScheme.primary
    }
    SectionCard(
        title = stringResource(R.string.thermal_headroom),
        subtitle = stringResource(R.string.thermal_headroom_body),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Rounded.DeviceThermostat, color, MaterialShapes.Sunny)
            Spacer(Modifier.width(12.dp))
            HeatBar(headroom.coerceIn(0f, 1f), color, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(formatUnit(R.string.unit_percent, headroom * 100f, 0), style = MaterialTheme.typography.headlineSmall, color = color)
        }
    }
}

@Composable
private fun CoolingCard(devices: List<CoolingDevice>) {
    val color = MaterialTheme.colorScheme.primary
    SectionCard(title = stringResource(R.string.section_cooling)) {
        devices.forEach { device ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AcUnit, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(device.type, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        if (device.current == 0) {
                            stringResource(R.string.cooling_idle)
                        } else {
                            stringResource(R.string.cooling_level, device.current, device.max)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HeatBar(device.current.toFloat() / device.max, color)
            }
        }
    }
}

@Composable
private fun ExtraTempsCard(temps: List<ExtraTemp>, warning: Float) {
    val scale = LocalHeatScale.current
    SectionCard(title = stringResource(R.string.section_extra), subtitle = stringResource(R.string.section_extra_body)) {
        temps.sortedByDescending { it.celsius }.forEach { temp ->
            val color = if (temp.celsius >= warning) MaterialTheme.colorScheme.error else scale.color(temp.celsius)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(temp.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                stringResource(
                                    when (temp.source) {
                                        ExtraSource.Hal -> R.string.extra_hal
                                        ExtraSource.Hwmon -> R.string.extra_hwmon
                                        ExtraSource.PowerSupply -> R.string.extra_supply
                                    },
                                ),
                                temp.kind,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(celsius(temp.celsius), style = MaterialTheme.typography.titleLarge, color = color)
                }
                HeatBar(scale.fraction(temp.celsius), color)
            }
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

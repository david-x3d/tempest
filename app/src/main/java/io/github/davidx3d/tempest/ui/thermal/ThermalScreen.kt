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
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
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
import io.github.davidx3d.tempest.data.sensors.EnvironmentKind
import io.github.davidx3d.tempest.data.sensors.EnvironmentState
import io.github.davidx3d.tempest.data.thermal.CoolingDevice
import io.github.davidx3d.tempest.data.thermal.CpuCore
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
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.formatNumber
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
        item { Header(reading.zones.size, reading.root, state.status) }

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

        if (reading.cpuCores.isNotEmpty()) item { CpuCard(reading.cpuCores) }
        if (reading.coolingDevices.isNotEmpty()) item { CoolingCard(reading.coolingDevices) }
        item { EnvironmentCard(state.environment) }
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
                Icon(if (root == RootState.Granted) Icons.Rounded.LockOpen else Icons.Rounded.Lock, contentDescription = null)
            },
        )
        Text(
            stringResource(R.string.thermal_status, stringResource(status.label())),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A wavy bar that grows calmer and longer when cool, tighter and taller when hot. */
@Composable
private fun HeatBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val armed = rememberWaveArmed()
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bar")
    LinearWavyProgressIndicator(
        progress = { shown },
        modifier = modifier.fillMaxWidth().height(12.dp),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        amplitude = { if (armed) 0.2f + 0.8f * shown else 0f },
        wavelength = (40 - 24 * shown).dp,
    )
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
private fun CpuCard(cores: List<CpuCore>) {
    val locale = currentLocale()
    val color = MaterialTheme.colorScheme.tertiary
    SectionCard(title = stringResource(R.string.section_cpu)) {
        cores.forEach { core ->
            val ghz = core.currentKhz / 1_000_000f
            val value = if (ghz >= 1f) {
                stringResource(R.string.unit_ghz, formatNumber(ghz, locale, 2))
            } else {
                stringResource(R.string.unit_mhz, formatNumber(core.currentKhz / 1000f, locale, 0))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(stringResource(R.string.cpu_core, core.index), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.titleSmall, color = color)
                }
                val max = core.maxKhz?.takeIf { it > 0 } ?: core.currentKhz
                HeatBar((core.currentKhz.toFloat() / max).coerceIn(0f, 1f), color)
            }
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
private fun EnvironmentCard(environment: EnvironmentState) {
    val locale = currentLocale()
    SectionCard(
        title = stringResource(R.string.section_environment),
        subtitle = pluralStringResource(R.plurals.sensor_count, environment.sensorCount, environment.sensorCount),
    ) {
        if (environment.readings.isEmpty()) {
            Text(
                stringResource(R.string.environment_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EnvironmentKind.entries.forEach { kind ->
            val reading = environment.readings[kind] ?: return@forEach
            val (label, value, icon) = when (kind) {
                EnvironmentKind.AmbientTemperature -> Triple(R.string.sensor_ambient_temperature, celsius(reading.value), Icons.Rounded.Thermostat)
                EnvironmentKind.Humidity -> Triple(R.string.sensor_humidity, formatUnit(R.string.unit_percent, reading.value, 0), Icons.Rounded.WaterDrop)
                EnvironmentKind.Pressure -> Triple(R.string.sensor_pressure, formatUnit(R.string.unit_hpa, reading.value, 1), Icons.Rounded.Compress)
                EnvironmentKind.Light -> Triple(R.string.sensor_light, stringResource(R.string.unit_lux, formatNumber(reading.value, locale, 0)), Icons.Rounded.WbSunny)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(icon, MaterialTheme.colorScheme.secondary, MaterialShapes.Clover4Leaf)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        reading.sensorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(value, style = MaterialTheme.typography.titleMedium)
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

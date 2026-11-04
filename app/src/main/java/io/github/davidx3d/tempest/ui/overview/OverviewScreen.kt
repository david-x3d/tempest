package io.github.davidx3d.tempest.ui.overview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidx3d.tempest.TempestApp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.settings.*
import io.github.davidx3d.tempest.data.thermal.ThermalStatus
import io.github.davidx3d.tempest.data.history.trendPerMinute
import io.github.davidx3d.tempest.ui.*
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.components.*
import io.github.davidx3d.tempest.ui.theme.*
import io.github.davidx3d.tempest.ui.thermal.label

@Composable
fun OverviewScreen(padding: PaddingValues, navigate: (Destination) -> Unit) {
    val app = LocalContext.current.applicationContext as TempestApp
    val c = app.container
    val battery by c.battery.snapshot.collectAsStateWithLifecycle()
    val thermal by c.thermal.reading.collectAsStateWithLifecycle()
    val status by c.thermal.status.collectAsStateWithLifecycle()
    val info by c.device.info.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val samples by c.history.soc.collectAsStateWithLifecycle()
    val soc = thermal.socZone?.celsius
    val constrained = status.ordinal >= ThermalStatus.Moderate.ordinal
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = padding + PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.overview_title), style = MaterialTheme.typography.displaySmall)
                Text(info?.soc?.name ?: android.os.Build.MODEL, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CompositionLocalProvider(LocalHeatScale provides HeatScale(if (soc != null) settings.socLevels else settings.levels)) {
                val temperature = soc ?: battery?.temperatureCelsius
                if (temperature != null) TemperatureHero(
                    celsiusValue = temperature,
                    label = stringResource(if (soc != null) R.string.thermal_soc else R.string.nav_battery),
                    trendPerMinute = if (soc != null) samples.trendPerMinute() else null,
                )
            }
        }
        if (constrained) item {
            WarningBanner(stringResource(R.string.overview_limited))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.nav_battery), battery?.temperatureCelsius, settings.levels, Modifier.weight(1f)) { navigate(Destination.Battery) }
                MetricCard(stringResource(R.string.thermal_soc), soc, settings.socLevels, Modifier.weight(1f)) { navigate(Destination.Thermal) }
            }
        }
        battery?.levelPercent?.let { level -> item {
            SectionCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.battery_level), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("$level %", style = MaterialTheme.typography.headlineSmall)
                }
                HeatBar(level / 100f, MaterialTheme.colorScheme.primary)
            }
        } }
        item {
            SectionCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(settings.thermalProfile.label()), style = MaterialTheme.typography.titleLarge)
                    }
                    FilledTonalButton(onClick = { navigate(Destination.Settings) }) { Text(stringResource(R.string.nav_settings)) }
                }
                Text(stringResource(R.string.overview_system_status, stringResource(status.label())), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (soc != null) item {
            HistoryCard(samples, settings.effectiveSocThreshold, csv = { io.github.davidx3d.tempest.data.history.historyCsv(c.history.battery.value, samples) }, recording = settings.backgroundRecording)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { navigate(Destination.Cpu) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.nav_cpu)) }
                FilledTonalButton(onClick = { navigate(Destination.Sensors) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.nav_sensors)) }
            }
        }
        if (soc == null) item {
            Text(stringResource(R.string.overview_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: Float?, levels: HeatLevels, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value?.let { celsius(it) } ?: "—", style = MaterialTheme.typography.headlineMedium, color = value?.let { HeatScale(levels).color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            value?.let { HeatBar(HeatScale(levels).fraction(it), HeatScale(levels).color(it)) }
        }
    }
}

@Composable
fun ProfileCard(settings: Settings, onSelect: (ThermalProfile) -> Unit) {
    SectionCard(title = stringResource(R.string.profile_title), subtitle = stringResource(R.string.profile_body)) {
        ThermalProfile.entries.chunked(2).forEach { profiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    FilterChip(
                        shape = androidx.compose.foundation.shape.CircleShape,selected = settings.thermalProfile == profile, onClick = { onSelect(profile) },
                        label = { Text(stringResource(profile.label())) }, modifier = Modifier.weight(1f))
                }
            }
        }
        InfoRow(stringResource(R.string.profile_limit), celsius(settings.effectiveSocThreshold))
        Text(stringResource(R.string.profile_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun ThermalProfile.label() = when(this) {
    ThermalProfile.Balanced -> R.string.profile_balanced
    ThermalProfile.Active -> R.string.profile_active
    ThermalProfile.Gaming -> R.string.profile_gaming
    ThermalProfile.Custom -> R.string.profile_custom
}

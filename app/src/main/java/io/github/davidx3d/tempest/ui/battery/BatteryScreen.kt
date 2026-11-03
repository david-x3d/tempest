package io.github.davidx3d.tempest.ui.battery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.battery.BatteryHealth
import io.github.davidx3d.tempest.data.battery.BatterySnapshot
import io.github.davidx3d.tempest.data.battery.ChargeStatus
import io.github.davidx3d.tempest.data.battery.PowerSource
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.components.HistoryCard
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.components.StatTile
import io.github.davidx3d.tempest.ui.components.TemperatureHero
import io.github.davidx3d.tempest.ui.components.WarningBanner
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.formatNumber
import io.github.davidx3d.tempest.ui.formatUnit
import io.github.davidx3d.tempest.ui.plus

@Composable
fun BatteryScreen(
    contentPadding: PaddingValues,
    viewModel: BatteryViewModel = viewModel(factory = BatteryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.isWarning) {
        if (state.isWarning) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    if (snapshot == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            LoadingIndicator()
            Text(
                stringResource(R.string.battery_waiting),
                modifier = Modifier.padding(top = 120.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            TemperatureHero(
                celsiusValue = snapshot.temperatureCelsius,
                label = stringResource(R.string.battery_title),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item {
            AnimatedVisibility(state.isWarning) {
                WarningBanner(stringResource(R.string.battery_warning, celsius(state.threshold, 0)))
            }
        }
        item { LevelCard(snapshot) }
        item { StatsGrid(snapshot) }
        item { HistoryCard(state.history, state.threshold) }
    }
}

@Composable
private fun LevelCard(snapshot: BatterySnapshot) {
    val level = snapshot.levelPercent ?: return
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.battery_level), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.weight(1f))
            Text(
                formatUnit(R.string.unit_percent, level.toFloat(), 0),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        LinearWavyProgressIndicator(
            progress = { level / 100f },
            modifier = Modifier.fillMaxWidth().height(14.dp),
            // The wave only moves while charging.
            waveSpeed = if (snapshot.status == ChargeStatus.Charging) 20.dp else 0.dp,
        )
    }
}

@Composable
private fun StatsGrid(snapshot: BatterySnapshot) {
    val locale = currentLocale()
    val unavailable = stringResource(R.string.value_unavailable)
    val voltage = snapshot.voltageVolts?.let { formatUnit(R.string.unit_volt, it, 2) } ?: unavailable
    val current = snapshot.currentMilliamps?.let {
        stringResource(R.string.unit_milliampere, formatNumber(it.toFloat(), locale, 0))
    } ?: unavailable

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.battery_voltage),
                value = voltage,
                icon = Icons.Rounded.Bolt,
                shape = MaterialShapes.Cookie4Sided,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.battery_current),
                value = current,
                icon = Icons.Rounded.Speed,
                shape = MaterialShapes.Pill,
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiaryContainer,
                onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.battery_status),
                value = stringResource(snapshot.status.label()),
                icon = Icons.Rounded.BatteryChargingFull,
                shape = MaterialShapes.Sunny,
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primaryContainer,
                onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            StatTile(
                label = stringResource(R.string.battery_source),
                value = stringResource(snapshot.source.label()),
                icon = if (snapshot.source == PowerSource.Battery) Icons.Rounded.Power else Icons.Rounded.ElectricalServices,
                shape = MaterialShapes.Cookie6Sided,
                modifier = Modifier.weight(1f),
            )
        }
        StatTile(
            label = stringResource(R.string.battery_health),
            value = stringResource(snapshot.health.label()),
            icon = Icons.Rounded.Favorite,
            shape = MaterialShapes.Clover4Leaf,
            modifier = Modifier.fillMaxWidth(),
            accent = MaterialTheme.colorScheme.tertiaryContainer,
            onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private fun ChargeStatus.label() = when (this) {
    ChargeStatus.Charging -> R.string.status_charging
    ChargeStatus.Discharging -> R.string.status_discharging
    ChargeStatus.Full -> R.string.status_full
    ChargeStatus.NotCharging -> R.string.status_not_charging
    ChargeStatus.Unknown -> R.string.status_unknown
}

private fun PowerSource.label() = when (this) {
    PowerSource.Ac -> R.string.source_ac
    PowerSource.Usb -> R.string.source_usb
    PowerSource.Wireless -> R.string.source_wireless
    PowerSource.Dock -> R.string.source_dock
    PowerSource.Battery -> R.string.source_battery
}

private fun BatteryHealth.label() = when (this) {
    BatteryHealth.Good -> R.string.health_good
    BatteryHealth.Overheat -> R.string.health_overheat
    BatteryHealth.Dead -> R.string.health_dead
    BatteryHealth.OverVoltage -> R.string.health_over_voltage
    BatteryHealth.Cold -> R.string.health_cold
    BatteryHealth.Failure -> R.string.health_failure
    BatteryHealth.Unknown -> R.string.health_unknown
}

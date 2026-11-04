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
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
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
import io.github.davidx3d.tempest.data.privilege.ChargingDetails
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.components.HeatBar
import io.github.davidx3d.tempest.ui.components.HistoryCard
import io.github.davidx3d.tempest.ui.components.InfoRow
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.components.StatTile
import io.github.davidx3d.tempest.ui.components.TemperatureHero
import io.github.davidx3d.tempest.ui.components.WarningBanner
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.duration
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
        contentPadding = contentPadding + PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            TemperatureHero(
                celsiusValue = snapshot.temperatureCelsius,
                label = stringResource(R.string.battery_title),
                modifier = Modifier.padding(vertical = 8.dp),
                trendPerMinute = state.trendPerMinute,
            )
        }
        item {
            AnimatedVisibility(state.isWarning) {
                WarningBanner(stringResource(R.string.battery_warning, celsius(state.threshold, 0)))
            }
        }
        item { LevelCard(snapshot) }
        item { StatsGrid(snapshot) }
        state.charging?.let { details -> item { ChargingCard(details) } }
        item { HistoryCard(state.history, state.threshold, csv = viewModel::exportCsv, recording = state.backgroundRecording) }
    }
}

@Composable
private fun LevelCard(snapshot: BatterySnapshot) {
    val wavesEnabled = io.github.davidx3d.tempest.ui.components.LocalWavesEnabled.current
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
            waveSpeed = if (snapshot.status == ChargeStatus.Charging && io.github.davidx3d.tempest.ui.components.LocalMotionEnabled.current) 20.dp else 0.dp,
            amplitude = { if (wavesEnabled) 1f else 0f },
        )
    }
}

private data class Stat(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val shape: RoundedPolygon,
    val tone: Int,
)

/** Every battery reading the system exposes, two per row; values the device lacks are skipped. */
@Composable
private fun StatsGrid(snapshot: BatterySnapshot) {
    val locale = currentLocale()
    val unavailable = stringResource(R.string.value_unavailable)
    val stats = buildList {
        add(
            Stat(
                if (snapshot.seriesCells > 1) {
                    stringResource(R.string.battery_voltage_cells, snapshot.seriesCells)
                } else {
                    stringResource(R.string.battery_voltage)
                },
                snapshot.voltageVolts?.let {
                    if (snapshot.voltageEstimated) formatUnit(R.string.unit_volt_approx, it, 0) else formatUnit(R.string.unit_volt, it, 2)
                } ?: unavailable,
                Icons.Rounded.Bolt, MaterialShapes.Cookie4Sided, 0,
            ),
        )
        add(
            Stat(
                stringResource(R.string.battery_current),
                snapshot.currentMilliamps?.let { stringResource(R.string.unit_milliampere, formatNumber(it.toFloat(), locale, 0)) } ?: unavailable,
                Icons.Rounded.Speed, MaterialShapes.Pill, 1,
            ),
        )
        snapshot.powerWatts?.let {
            val power = if (snapshot.voltageEstimated) formatUnit(R.string.unit_watt_approx, it, 1) else formatUnit(R.string.unit_watt, it, 2)
            add(Stat(stringResource(R.string.battery_power), power, Icons.Rounded.ElectricBolt, MaterialShapes.Sunny, 2))
        }
        snapshot.chargeCounterMah?.let {
            add(Stat(stringResource(R.string.battery_charge_counter), stringResource(R.string.unit_mah, formatNumber(it.toFloat(), locale, 0)), Icons.Rounded.BatteryStd, MaterialShapes.Cookie7Sided, 0))
        }
        add(Stat(stringResource(R.string.battery_status), stringResource(snapshot.status.label()), Icons.Rounded.BatteryChargingFull, MaterialShapes.Cookie9Sided, 2))
        add(
            Stat(
                stringResource(R.string.battery_source),
                stringResource(snapshot.source.label()),
                if (snapshot.source == PowerSource.Battery) Icons.Rounded.Power else Icons.Rounded.ElectricalServices,
                MaterialShapes.Cookie6Sided, 0,
            ),
        )
        snapshot.timeToFullMillis?.let { add(Stat(stringResource(R.string.battery_time_to_full), duration(it), Icons.Rounded.Timer, MaterialShapes.Clover8Leaf, 1)) }
        snapshot.timeToEmptyMillis?.let {
            add(Stat(stringResource(R.string.battery_time_to_empty), stringResource(R.string.value_approx, duration(it)), Icons.Rounded.HourglassBottom, MaterialShapes.Clover8Leaf, 1))
        }
        add(Stat(stringResource(R.string.battery_health), stringResource(snapshot.health.label()), Icons.Rounded.HealthAndSafety, MaterialShapes.Cookie12Sided, if (snapshot.health == BatteryHealth.Good) 2 else 1))
        snapshot.cycleCount?.let { add(Stat(stringResource(R.string.battery_cycles), formatNumber(it.toFloat(), locale, 0), Icons.Rounded.Autorenew, MaterialShapes.Flower, 2)) }
        snapshot.technology?.let { add(Stat(stringResource(R.string.battery_technology), it, Icons.Rounded.Science, MaterialShapes.Gem, 0)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { stat ->
                    val (accent, onAccent) = when (stat.tone) {
                        1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                        2 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    StatTile(
                        label = stat.label,
                        value = stat.value,
                        icon = stat.icon,
                        shape = stat.shape,
                        modifier = Modifier.weight(1f),
                        accent = accent,
                        onAccent = onAccent,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Capacity health and charge limits straight from the kernel; only present with Shizuku or root. */
@Composable
private fun ChargingCard(d: ChargingDetails) {
    val locale = currentLocale()
    @Composable fun mah(value: Int) = formatUnit(R.string.unit_mah, value.toFloat(), 0)
    SectionCard(title = stringResource(R.string.charging_title)) {
        d.healthPercent?.let { health ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(stringResource(R.string.charging_health), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(formatUnit(R.string.unit_percent, health.toFloat(), 0), style = MaterialTheme.typography.titleSmall)
                }
                HeatBar((health / 100f).coerceIn(0f, 1f), if (health >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
        InfoRow(stringResource(R.string.charging_full), d.fullMah?.let { mah(it) })
        InfoRow(stringResource(R.string.charging_design), d.designMah?.let { mah(it) })
        InfoRow(stringResource(R.string.battery_cycles), d.cycleCount?.let { formatNumber(it.toFloat(), locale, 0) })
        InfoRow(stringResource(R.string.charging_type), d.chargeType)
        InfoRow(stringResource(R.string.charging_max_current), d.maxChargeMa?.let { formatUnit(R.string.unit_milliampere, it.toFloat(), 0) })
        InfoRow(stringResource(R.string.charging_input_limit), d.inputLimitMa?.let { formatUnit(R.string.unit_milliampere, it.toFloat(), 0) })
        InfoRow(stringResource(R.string.charging_max_voltage), d.maxVoltageV?.let { formatUnit(R.string.unit_volt, it, 2) })
        InfoRow(stringResource(R.string.charging_input), d.inputWatts?.let { formatUnit(R.string.unit_watt, it, 1) })
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

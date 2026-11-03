package io.github.davidx3d.tempest.ui.sensors

import android.hardware.Sensor
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.sensors.SensorGroup
import io.github.davidx3d.tempest.data.sensors.SensorSpec
import io.github.davidx3d.tempest.ui.components.CardShape
import io.github.davidx3d.tempest.ui.components.InfoRow
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.formatNumber
import io.github.davidx3d.tempest.ui.plus
import java.util.Locale
import kotlin.math.abs

@Composable
fun SensorsScreen(
    contentPadding: PaddingValues,
    viewModel: SensorsViewModel = viewModel(factory = SensorsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sensors_title), style = MaterialTheme.typography.displaySmall)
                Text(
                    pluralStringResource(R.plurals.sensor_count, state.sensors.size, state.sensors.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.filter == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text(stringResource(R.string.sensors_filter_all)) },
                    )
                }
                items(state.groups) { group ->
                    FilterChip(
                        selected = state.filter == group,
                        onClick = { viewModel.setFilter(if (state.filter == group) null else group) },
                        label = { Text(stringResource(group.label())) },
                    )
                }
            }
        }
        items(state.visible, key = { it.id }) { sensor ->
            SensorCard(sensor, state.live[sensor.id], Modifier.animateItem())
        }
    }
}

@Composable
private fun SensorCard(sensor: SensorSpec, live: SensorLive?, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(sensor.id) { mutableStateOf(false) }
    val (container, content) = sensor.group.colors()
    val unit = unitOf(sensor.type)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(container, sensor.group.shape().toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(iconOf(sensor.type), null, tint = content, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(typeLabel(sensor), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        sensor.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val values = live?.values
            if (values == null || values.isEmpty()) {
                Text(
                    stringResource(R.string.sensor_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ValuesRow(values.take(axisCount(sensor.type, values.size)), unit)
                if (live.history.size > 2) Sparkline(live.history, content)
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val locale = currentLocale()
                    InfoRow(stringResource(R.string.sensor_vendor), sensor.vendor)
                    InfoRow(stringResource(R.string.sensor_version), sensor.version.toString())
                    InfoRow(stringResource(R.string.sensor_range), "${number(sensor.maxRange, locale)} $unit".trim())
                    InfoRow(stringResource(R.string.sensor_resolution), "${number(sensor.resolution, locale)} $unit".trim())
                    InfoRow(stringResource(R.string.sensor_power), stringResource(R.string.unit_milliampere, formatNumber(sensor.powerMa, locale, 2)))
                    InfoRow(
                        stringResource(R.string.sensor_rate),
                        sensor.maxRateHz?.let { stringResource(R.string.unit_hz, formatNumber(it, locale, 0)) }
                            ?: stringResource(R.string.sensor_on_change),
                    )
                    InfoRow(stringResource(R.string.sensor_wakeup), stringResource(if (sensor.wakeUp) R.string.value_yes else R.string.value_no))
                    InfoRow(stringResource(R.string.sensor_type), sensor.stringType)
                }
            }
        }
    }
}

@Composable
private fun ValuesRow(values: List<Float>, unit: String) {
    val locale = currentLocale()
    if (values.size == 1) {
        Text("${number(values[0], locale)} $unit".trim(), style = MaterialTheme.typography.headlineMedium)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { i, value ->
            Column(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(AXES.getOrElse(i) { "v$i" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(number(value, locale), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
    if (unit.isNotEmpty()) {
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The last few seconds of the reading's magnitude as a thin line. */
@Composable
private fun Sparkline(history: List<Float>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val min = history.min()
        val max = history.max()
        val span = (max - min).takeIf { it > 1e-6f } ?: 1f
        val step = size.width / (history.size - 1)
        val path = Path()
        history.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v - min) / span * size.height * 0.9f - size.height * 0.05f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val AXES = listOf("X", "Y", "Z", "W")

private fun axisCount(type: Int, available: Int): Int = when (type) {
    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> minOf(4, available)
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GRAVITY,
    Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
    -> minOf(3, available)
    else -> if (available >= 3) 3 else 1
}

private fun number(value: Float, locale: Locale): String = formatNumber(
    value,
    locale,
    when {
        abs(value) >= 100f -> 0
        abs(value) >= 10f -> 1
        else -> 2
    },
)

private fun unitOf(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_GRAVITY,
    Sensor.TYPE_LINEAR_ACCELERATION,
    -> "m/s²"
    Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
    Sensor.TYPE_LIGHT -> "lx"
    Sensor.TYPE_PRESSURE -> "hPa"
    Sensor.TYPE_PROXIMITY -> "cm"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
    Sensor.TYPE_HEART_RATE -> "bpm"
    Sensor.TYPE_HINGE_ANGLE -> "°"
    else -> ""
}

private fun iconOf(type: Int): ImageVector = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_LINEAR_ACCELERATION -> Icons.Rounded.Speed
    Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> Icons.Rounded.Rotate90DegreesCw
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> Icons.Rounded.Explore
    Sensor.TYPE_GRAVITY -> Icons.Rounded.ArrowDownward
    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> Icons.Rounded.ScreenRotation
    Sensor.TYPE_LIGHT -> Icons.Rounded.LightMode
    Sensor.TYPE_PRESSURE -> Icons.Rounded.Compress
    Sensor.TYPE_RELATIVE_HUMIDITY -> Icons.Rounded.WaterDrop
    Sensor.TYPE_AMBIENT_TEMPERATURE -> Icons.Rounded.Thermostat
    Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Sensor.TYPE_HEART_RATE, Sensor.TYPE_HEART_BEAT -> Icons.Rounded.MonitorHeart
    Sensor.TYPE_SIGNIFICANT_MOTION, Sensor.TYPE_MOTION_DETECT -> Icons.Rounded.Waves
    else -> Icons.Rounded.Sensors
}

@Composable
private fun typeLabel(sensor: SensorSpec): String {
    val res = when (sensor.type) {
        Sensor.TYPE_ACCELEROMETER -> R.string.sensor_type_accelerometer
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> R.string.sensor_type_accelerometer_raw
        Sensor.TYPE_GYROSCOPE -> R.string.sensor_type_gyroscope
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> R.string.sensor_type_gyroscope_raw
        Sensor.TYPE_MAGNETIC_FIELD -> R.string.sensor_type_magnetic
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> R.string.sensor_type_magnetic_raw
        Sensor.TYPE_GRAVITY -> R.string.sensor_type_gravity
        Sensor.TYPE_LINEAR_ACCELERATION -> R.string.sensor_type_linear
        Sensor.TYPE_ROTATION_VECTOR -> R.string.sensor_type_rotation
        Sensor.TYPE_GAME_ROTATION_VECTOR -> R.string.sensor_type_game_rotation
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> R.string.sensor_type_geo_rotation
        Sensor.TYPE_LIGHT -> R.string.sensor_light
        Sensor.TYPE_PROXIMITY -> R.string.sensor_type_proximity
        Sensor.TYPE_PRESSURE -> R.string.sensor_pressure
        Sensor.TYPE_RELATIVE_HUMIDITY -> R.string.sensor_humidity
        Sensor.TYPE_AMBIENT_TEMPERATURE -> R.string.sensor_ambient_temperature
        Sensor.TYPE_STEP_COUNTER -> R.string.sensor_type_step_counter
        Sensor.TYPE_STEP_DETECTOR -> R.string.sensor_type_step_detector
        Sensor.TYPE_SIGNIFICANT_MOTION -> R.string.sensor_type_significant_motion
        Sensor.TYPE_HEART_RATE -> R.string.sensor_type_heart_rate
        Sensor.TYPE_HINGE_ANGLE -> R.string.sensor_type_hinge
        else -> null
    }
    if (res != null) return stringResource(res)
    // Vendor sensors only carry a string type like "com.vendor.sensor.tilt_detector".
    return sensor.stringType.substringAfterLast('.').replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .ifBlank { sensor.name }
}

@StringRes
private fun SensorGroup.label() = when (this) {
    SensorGroup.Motion -> R.string.sensor_group_motion
    SensorGroup.Position -> R.string.sensor_group_position
    SensorGroup.Environment -> R.string.sensor_group_environment
    SensorGroup.Other -> R.string.sensor_group_other
}

private fun SensorGroup.shape(): RoundedPolygon = when (this) {
    SensorGroup.Motion -> MaterialShapes.Cookie6Sided
    SensorGroup.Position -> MaterialShapes.Clover4Leaf
    SensorGroup.Environment -> MaterialShapes.Sunny
    SensorGroup.Other -> MaterialShapes.Cookie9Sided
}

@Composable
private fun SensorGroup.colors(): Pair<Color, Color> = when (this) {
    SensorGroup.Motion -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    SensorGroup.Position -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    SensorGroup.Environment -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    SensorGroup.Other -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
}

package io.github.davidx3d.tempest.ui.sensors

import android.hardware.Sensor
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.davidx3d.tempest.data.sensors.ReportingMode
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
    val haptics = LocalHapticFeedback.current
    val visible = state.visible

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sensors_title), style = MaterialTheme.typography.displaySmall)
                Text(
                    pluralStringResource(R.plurals.sensor_count, state.sensors.size, state.sensors.size) + " · " +
                        pluralStringResource(R.plurals.sensors_vendor_count, state.vendors, state.vendors),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(Modifier.padding(bottom = 20.dp)) {
                // Collected here so only the instruments recompose at sensor speed.
                val instruments by viewModel.instruments.collectAsStateWithLifecycle()
                InstrumentPanel(instruments, state.available)
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.sensors_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
            )
        }
        item {
            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.setFilter(if (state.filter == group) null else group)
                        },
                        label = { Text(stringResource(group.label())) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.sensors_none_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }
        visible.forEach { (group, sensors) ->
            item(key = "header-$group") {
                Row(
                    Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp).animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(group.label()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("${sensors.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(sensors, key = { _, it -> it.id }) { i, sensor ->
                SensorRow(sensor, segmentShape(i, sensors.size), Modifier.animateItem()) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    viewModel.open(sensor)
                }
            }
        }
    }

    DetailHost(viewModel)
}

/** Its own scope, so live values in the sheet do not recompose the list behind it. */
@Composable
private fun DetailHost(viewModel: SensorsViewModel) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    detail?.let { SensorSheet(it, onDismiss = { viewModel.open(null) }) }
}

/** Rows of one group read as one block: round outer corners, tight inner ones. */
private fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
private fun SensorRow(sensor: SensorSpec, shape: RoundedCornerShape, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (container, content) = sensor.group.colors()
    val locale = currentLocale()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(sensor, container, content, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(typeLabel(sensor), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${sensor.name} · ${sensor.vendor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                sensor.maxRateHz?.let {
                    Text(
                        stringResource(R.string.unit_hz, formatNumber(it, locale, 0)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sensor.wakeUp) {
                    Text(stringResource(R.string.sensor_wakeup_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun Badge(sensor: SensorSpec, container: Color, content: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(container, sensor.group.shape().toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconOf(sensor.type), null, tint = content, modifier = Modifier.size(size / 2))
    }
}

/** Live values, a chart of every axis and the full datasheet of one sensor. */
@Composable
private fun SensorSheet(detail: SensorDetail, onDismiss: () -> Unit) {
    val sensor = detail.spec
    val (container, content) = sensor.group.colors()
    val unit = unitOf(sensor.type)
    val locale = currentLocale()
    val axisColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(sensor, container, content, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(typeLabel(sensor), style = MaterialTheme.typography.headlineSmall)
                    Text(sensor.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.sensor_live), style = MaterialTheme.typography.titleMedium)
                    val values = detail.values
                    when {
                        sensor.reportingMode == ReportingMode.OneShot -> Text(
                            stringResource(R.string.sensor_one_shot_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        values == null || values.isEmpty() -> Text(
                            stringResource(R.string.sensor_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {
                            val axes = axisCount(sensor.type, values.size)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                values.take(axes).forEachIndexed { i, value ->
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        if (axes > 1) {
                                            Text(AXES.getOrElse(i) { "v$i" }, style = MaterialTheme.typography.labelMedium, color = axisColors[i % axisColors.size])
                                        }
                                        Text(number(value, locale), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                    }
                                }
                            }
                            if (unit.isNotEmpty()) {
                                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (detail.history.size > 2) AxisChart(detail.history, axes, axisColors)
                        }
                    }
                }
            }

            Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow(stringResource(R.string.sensor_vendor), sensor.vendor)
                    InfoRow(stringResource(R.string.sensor_type), sensor.stringType)
                    InfoRow(stringResource(R.string.sensor_version), sensor.version.toString())
                    InfoRow(stringResource(R.string.sensor_range), "${number(sensor.maxRange, locale)} $unit".trim())
                    InfoRow(stringResource(R.string.sensor_resolution), "${number(sensor.resolution, locale)} $unit".trim())
                    InfoRow(stringResource(R.string.sensor_power), stringResource(R.string.unit_milliampere, formatNumber(sensor.powerMa, locale, 2)))
                    InfoRow(
                        stringResource(R.string.sensor_rate),
                        sensor.maxRateHz?.let { stringResource(R.string.unit_hz, formatNumber(it, locale, 0)) }
                            ?: stringResource(R.string.sensor_on_change),
                    )
                    InfoRow(stringResource(R.string.sensor_reporting), stringResource(sensor.reportingMode.label()))
                    InfoRow(
                        stringResource(R.string.sensor_fifo),
                        if (sensor.fifoMax > 0) stringResource(R.string.sensor_fifo_value, sensor.fifoMax) else stringResource(R.string.sensor_fifo_none),
                    )
                    InfoRow(stringResource(R.string.sensor_wakeup), stringResource(if (sensor.wakeUp) R.string.value_yes else R.string.value_no))
                    if (sensor.dynamic) InfoRow(stringResource(R.string.sensor_dynamic), stringResource(R.string.value_yes))
                }
            }
        }
    }
}

/** The last few seconds of every axis, each scaled into the same box. */
@Composable
private fun AxisChart(history: List<FloatArray>, axes: Int, colors: List<Color>) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val all = history.flatMap { v -> v.take(axes) }
        val min = all.min()
        val max = all.max()
        val span = (max - min).takeIf { it > 1e-6f } ?: 1f
        val step = size.width / (history.size - 1)
        drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 1.dp.toPx())
        for (axis in 0 until axes) {
            val path = Path()
            history.forEachIndexed { i, v ->
                val value = v.getOrElse(axis) { 0f }
                val x = i * step
                val y = size.height - (value - min) / span * size.height * 0.9f - size.height * 0.05f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors[axis % colors.size], style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@StringRes
private fun ReportingMode.label() = when (this) {
    ReportingMode.Continuous -> R.string.reporting_continuous
    ReportingMode.OnChange -> R.string.sensor_on_change
    ReportingMode.OneShot -> R.string.reporting_one_shot
    ReportingMode.Special -> R.string.reporting_special
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

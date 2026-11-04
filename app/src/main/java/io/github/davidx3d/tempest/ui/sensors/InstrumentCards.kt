package io.github.davidx3d.tempest.ui.sensors

import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.sensors.InstrumentAvailability
import io.github.davidx3d.tempest.data.sensors.Instruments
import io.github.davidx3d.tempest.ui.components.CardShape
import io.github.davidx3d.tempest.ui.components.StatTile
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.formatNumber
import io.github.davidx3d.tempest.ui.formatUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Orientation on top (compass and level), then small tiles for everything else the phone has. */
@Composable
fun InstrumentPanel(instruments: Instruments, available: InstrumentAvailability) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (available.compass || available.level) OrientationCard(instruments, available)
        val locale = currentLocale()
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            if (available.light) add { m ->
                val lux = instruments.lux
                StatTile(
                    label = stringResource(R.string.sensor_light) + (lux?.let { " · " + stringResource(lightClass(it)) } ?: ""),
                    value = lux?.let { formatUnit(R.string.unit_lux, it, 0) } ?: "–",
                    icon = Icons.Rounded.LightMode,
                    shape = MaterialShapes.Sunny,
                    modifier = m,
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onAccent = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (available.motion) add { m ->
                StatTile(
                    label = stringResource(R.string.instrument_motion),
                    value = instruments.gForce?.let { formatUnit(R.string.unit_g, it, 2) } ?: "–",
                    icon = Icons.Rounded.Vibration,
                    shape = MaterialShapes.Cookie6Sided,
                    modifier = m,
                    accent = MaterialTheme.colorScheme.primaryContainer,
                    onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (available.proximity) add { m ->
                StatTile(
                    label = stringResource(R.string.sensor_type_proximity),
                    value = when (instruments.near) {
                        null -> "–"
                        true -> stringResource(R.string.proximity_near)
                        false -> stringResource(R.string.proximity_far)
                    },
                    icon = Icons.Rounded.Sensors,
                    shape = MaterialShapes.Clover4Leaf,
                    modifier = m,
                    accent = if (instruments.near == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer,
                    onAccent = if (instruments.near == true) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (available.pressure) add { m ->
                val hpa = instruments.pressureHpa
                StatTile(
                    label = hpa?.let {
                        stringResource(
                            R.string.pressure_altitude,
                            formatNumber(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it), locale, 0),
                        )
                    } ?: stringResource(R.string.sensor_pressure),
                    value = hpa?.let { formatUnit(R.string.unit_hpa, it, 1) } ?: "–",
                    icon = Icons.Rounded.Compress,
                    shape = MaterialShapes.Cookie9Sided,
                    modifier = m,
                )
            }
        }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Compass dial on the left, heading and a small bubble level on the right. */
@Composable
private fun OrientationCard(instruments: Instruments, available: InstrumentAvailability) {
    val points = stringArrayResource(R.array.compass_points)
    val locale = currentLocale()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (available.compass) {
                CompassDial(instruments.azimuth, points, Modifier.size(148.dp))
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (available.compass) {
                    val heading = instruments.azimuth?.roundToInt()?.mod(360)
                    Column {
                        Text(
                            stringResource(R.string.instrument_compass),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(heading?.let { "$it°" } ?: "–", style = MaterialTheme.typography.displaySmall)
                            if (heading != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    points[((heading + 22) % 360) / 45],
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (available.compass && available.level) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (available.level) {
                    val tilt = instruments.tilt
                    val flat = tilt != null && abs(tilt.first) < 1f && abs(tilt.second) < 1f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BubbleLevel(tilt, flat, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.instrument_level),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                when {
                                    tilt == null -> "–"
                                    flat -> stringResource(R.string.level_flat)
                                    else -> "${formatNumber(abs(tilt.first), locale, 0)}° · ${formatNumber(abs(tilt.second), locale, 0)}°"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ticks and the four cardinal letters turn with the heading; the pointer at the top stays put. */
@Composable
private fun CompassDial(azimuth: Float?, points: Array<String>, modifier: Modifier) {
    // Unwrap the angle so the dial takes the short way from 359° to 1°.
    var unwrapped by remember { mutableFloatStateOf(azimuth ?: 0f) }
    if (azimuth != null) unwrapped += ((azimuth - unwrapped) % 360f + 540f) % 360f - 180f
    val shown by animateFloatAsState(unwrapped, spring(stiffness = 150f), label = "compass")

    val primary = MaterialTheme.colorScheme.primary
    val minor = MaterialTheme.colorScheme.outlineVariant
    val major = MaterialTheme.colorScheme.onSurfaceVariant
    val north = MaterialTheme.colorScheme.error
    val dial = MaterialTheme.colorScheme.surfaceContainerHighest
    val measurer = rememberTextMeasurer()
    val letterStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

    Canvas(modifier) {
        val radius = min(size.width, size.height) / 2f
        drawCircle(dial, radius)
        fun polar(deg: Float, r: Float): Offset {
            val a = Math.toRadians((deg - shown - 90f).toDouble())
            return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        }
        for (deg in 0 until 360 step 10) {
            val isMajor = deg % 90 == 0
            val isMid = deg % 30 == 0
            val outer = radius - 6.dp.toPx()
            val length = when {
                isMajor -> 10.dp
                isMid -> 7.dp
                else -> 4.dp
            }.toPx()
            drawLine(
                color = if (isMajor || isMid) major else minor,
                start = polar(deg.toFloat(), outer - length),
                end = polar(deg.toFloat(), outer),
                strokeWidth = (if (isMajor) 2.5.dp else 1.5.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
        // Upright letters at the four cardinal points.
        listOf(0 to points[0], 90 to points[2], 180 to points[4], 270 to points[6]).forEach { (deg, letter) ->
            val layout = measurer.measure(letter, TextStyle(color = if (deg == 0) north else major).merge(letterStyle))
            val at = polar(deg.toFloat(), radius - 30.dp.toPx())
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }
        // Fixed pointer: where the top of the phone points.
        val tip = Path().apply {
            moveTo(center.x, center.y - radius + 1.dp.toPx())
            lineTo(center.x - 6.dp.toPx(), center.y - radius + 13.dp.toPx())
            lineTo(center.x + 6.dp.toPx(), center.y - radius + 13.dp.toPx())
            close()
        }
        drawPath(tip, primary)
        drawCircle(primary, 4.dp.toPx())
    }
}

@Composable
private fun BubbleLevel(tilt: Pair<Float, Float>?, flat: Boolean, modifier: Modifier) {
    val x by animateFloatAsState(tilt?.first ?: 0f, spring(stiffness = 300f), label = "levelX")
    val y by animateFloatAsState(tilt?.second ?: 0f, spring(stiffness = 300f), label = "levelY")
    val bubble by animateColorAsState(if (flat) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.tertiary, label = "bubble")
    val ring = MaterialTheme.colorScheme.outlineVariant
    val dial = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        drawCircle(dial, radius)
        drawCircle(ring, radius * 0.35f, style = Stroke(1.dp.toPx()))
        val bubbleRadius = radius * 0.28f
        val reach = radius - bubbleRadius
        // 30° of tilt moves the bubble to the edge; it floats to the higher side.
        val offset = Offset((x / 30f).coerceIn(-1f, 1f) * reach, (-y / 30f).coerceIn(-1f, 1f) * reach)
        drawCircle(bubble, bubbleRadius, center + offset)
    }
}

private fun lightClass(lux: Float) = when {
    lux < 10f -> R.string.light_dark
    lux < 200f -> R.string.light_dim
    lux < 1_000f -> R.string.light_indoor
    lux < 10_000f -> R.string.light_overcast
    lux < 50_000f -> R.string.light_daylight
    else -> R.string.light_sun
}

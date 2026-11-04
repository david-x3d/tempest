package io.github.davidx3d.tempest.ui.sensors

import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.sensors.InstrumentAvailability
import io.github.davidx3d.tempest.data.sensors.Instruments
import io.github.davidx3d.tempest.ui.components.CardShape
import io.github.davidx3d.tempest.ui.components.HeatBar
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.formatNumber
import io.github.davidx3d.tempest.ui.formatUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The live instruments, two per row; only those the phone has sensors for. */
@Composable
fun InstrumentGrid(instruments: Instruments, available: InstrumentAvailability) {
    val cards = buildList<@Composable (Modifier) -> Unit> {
        if (available.compass) add { m -> CompassCard(instruments.azimuth, m) }
        if (available.level) add { m -> LevelCard(instruments.tilt, m) }
        if (available.light) add { m -> LightCard(instruments.lux, m) }
        if (available.motion) add { m -> MotionCard(instruments.gForce, m) }
        if (available.proximity) add { m -> ProximityCard(instruments.near, m) }
        if (available.pressure) add { m -> PressureCard(instruments.pressureHpa, m) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card -> card(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InstrumentCard(
    title: String,
    modifier: Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.aspectRatio(0.86f),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** A dial that turns so north stays north, with the heading in the middle. */
@Composable
private fun CompassCard(azimuth: Float?, modifier: Modifier) {
    // Unwrap the angle so the dial takes the short way from 359° to 1°.
    var unwrapped by remember { mutableFloatStateOf(azimuth ?: 0f) }
    if (azimuth != null) {
        val delta = ((azimuth - unwrapped) % 360f + 540f) % 360f - 180f
        unwrapped += delta
    }
    val shown by animateFloatAsState(unwrapped, spring(stiffness = 120f), label = "compass")
    val points = stringArrayResource(R.array.compass_points)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val dial = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error

    InstrumentCard(stringResource(R.string.instrument_compass), modifier) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val radius = min(size.width, size.height) / 2f
                drawCircle(dial, radius)
                rotate(-shown) {
                    for (deg in 0 until 360 step 15) {
                        val major = deg % 90 == 0
                        val a = Math.toRadians(deg.toDouble() - 90)
                        val outer = radius - 4.dp.toPx()
                        val inner = outer - (if (major) 10.dp else 5.dp).toPx()
                        drawLine(
                            if (deg == 0) error else if (major) onSurface else outline,
                            Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()),
                            Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat()),
                            strokeWidth = (if (major) 3.dp else 1.5.dp).toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                // Fixed pointer at the top: where the phone is heading.
                val tip = Path().apply {
                    moveTo(center.x, center.y - radius - 2.dp.toPx())
                    lineTo(center.x - 7.dp.toPx(), center.y - radius + 10.dp.toPx())
                    lineTo(center.x + 7.dp.toPx(), center.y - radius + 10.dp.toPx())
                    close()
                }
                drawPath(tip, primary)
            }
            // The letter for north rides the dial; the heading sits in the middle.
            Box(Modifier.fillMaxSize().rotate(-shown), contentAlignment = Alignment.TopCenter) {
                Text(
                    points[0],
                    style = MaterialTheme.typography.labelLarge,
                    color = error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 22.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val heading = azimuth?.roundToInt()?.mod(360)
                Text(heading?.let { "$it°" } ?: "–", style = MaterialTheme.typography.headlineSmall)
                if (heading != null) {
                    Text(points[((heading + 22) % 360) / 45], style = MaterialTheme.typography.labelLarge, color = primary)
                }
            }
        }
    }
}

/** A bubble level: the bubble drifts to the higher side and turns green when the phone lies flat. */
@Composable
private fun LevelCard(tilt: Pair<Float, Float>?, modifier: Modifier) {
    val x by animateFloatAsState(tilt?.first ?: 0f, spring(stiffness = 300f), label = "levelX")
    val y by animateFloatAsState(tilt?.second ?: 0f, spring(stiffness = 300f), label = "levelY")
    val flat = tilt != null && abs(tilt.first) < 1f && abs(tilt.second) < 1f
    val bubble by animateColorAsState(if (flat) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.tertiary, label = "bubble")
    val ring = MaterialTheme.colorScheme.outlineVariant
    val dial = MaterialTheme.colorScheme.surfaceContainerHighest
    val locale = currentLocale()

    InstrumentCard(stringResource(R.string.instrument_level), modifier) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val radius = min(size.width, size.height) / 2f
                drawCircle(dial, radius)
                drawCircle(ring, radius * 0.25f, style = Stroke(1.5.dp.toPx()))
                drawLine(ring, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
                drawLine(ring, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
                val bubbleRadius = radius * 0.2f
                val reach = radius - bubbleRadius
                // 30° of tilt moves the bubble to the edge.
                val bx = (x / 30f).coerceIn(-1f, 1f) * reach
                val by = (-y / 30f).coerceIn(-1f, 1f) * reach
                drawCircle(bubble.copy(alpha = 0.3f), bubbleRadius * 1.3f, Offset(center.x + bx, center.y + by))
                drawCircle(bubble, bubbleRadius, Offset(center.x + bx, center.y + by))
            }
        }
        Text(
            if (tilt == null) {
                "–"
            } else if (flat) {
                stringResource(R.string.level_flat)
            } else {
                "${formatNumber(abs(tilt.first), locale, 0)}° · ${formatNumber(abs(tilt.second), locale, 0)}°"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (flat) bubble else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Brightness with a plain-language class and a logarithmic bar from darkness to full sun. */
@Composable
private fun LightCard(lux: Float?, modifier: Modifier) {
    val label = when {
        lux == null -> null
        lux < 10f -> R.string.light_dark
        lux < 200f -> R.string.light_dim
        lux < 1_000f -> R.string.light_indoor
        lux < 10_000f -> R.string.light_overcast
        lux < 50_000f -> R.string.light_daylight
        else -> R.string.light_sun
    }
    val fraction = lux?.let { (log10(it.coerceAtLeast(1f)) / 5f).coerceIn(0f, 1f) } ?: 0f
    val shown by animateFloatAsState(fraction, label = "lux")
    val color = Color(0xFFF2A516)
    InstrumentCard(stringResource(R.string.sensor_light), modifier) {
        Canvas(Modifier.size(52.dp).clip(MaterialShapes.Sunny.toShape())) {
            drawRect(color.copy(alpha = 0.25f + 0.75f * shown))
        }
        Spacer(Modifier.weight(1f))
        Text(
            lux?.let { formatUnit(R.string.unit_lux, it, 0) } ?: "–",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
        )
        label?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HeatBar(shown, color)
    }
}

/** Acceleration without gravity: how much the phone is being moved or shaken right now. */
@Composable
private fun MotionCard(gForce: Float?, modifier: Modifier) {
    val shown by animateFloatAsState(gForce ?: 0f, spring(stiffness = 400f), label = "g")
    val label = when {
        gForce == null -> null
        gForce < 0.05f -> R.string.motion_still
        gForce < 0.6f -> R.string.motion_moving
        else -> R.string.motion_shaking
    }
    val color = when (label) {
        R.string.motion_shaking -> MaterialTheme.colorScheme.error
        R.string.motion_moving -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    InstrumentCard(stringResource(R.string.instrument_motion), modifier) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val stroke = 12.dp.toPx()
                val side = min(size.width, size.height) - stroke
                val topLeft = Offset(center.x - side / 2, center.y - side / 2)
                val arcSize = androidx.compose.ui.geometry.Size(side, side)
                drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // Two g fill the arc.
                drawArc(color, 135f, 270f * (shown / 2f).coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(
                gForce?.let { formatUnit(R.string.unit_g, it, 2) } ?: "–",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        label?.let {
            Text(stringResource(it), style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ProximityCard(near: Boolean?, modifier: Modifier) {
    val scale by animateFloatAsState(if (near == true) 1f else 0.45f, spring(dampingRatio = 0.5f), label = "proximity")
    val color by animateColorAsState(
        if (near == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "proximityColor",
    )
    InstrumentCard(stringResource(R.string.sensor_type_proximity), modifier) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                drawCircle(color, min(size.width, size.height) / 2f * scale)
            }
        }
        Text(
            when (near) {
                null -> "–"
                true -> stringResource(R.string.proximity_near)
                false -> stringResource(R.string.proximity_far)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Air pressure and the altitude it suggests against the standard atmosphere. */
@Composable
private fun PressureCard(hpa: Float?, modifier: Modifier) {
    val locale = currentLocale()
    InstrumentCard(stringResource(R.string.sensor_pressure), modifier) {
        Spacer(Modifier.weight(1f))
        Text(hpa?.let { formatUnit(R.string.unit_hpa, it, 1) } ?: "–", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        hpa?.let {
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it)
            Text(
                stringResource(R.string.pressure_altitude, formatNumber(altitude, locale, 0)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

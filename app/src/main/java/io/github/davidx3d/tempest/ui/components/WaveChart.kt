package io.github.davidx3d.tempest.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.data.history.Sample
import io.github.davidx3d.tempest.ui.theme.HeatScale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The temperature history as a living wave. The curve follows the samples; on top of it rides a
 * sine whose amplitude and frequency both rise with the temperature at that point, so a hot
 * stretch visibly "boils" while a cool one barely ripples. The phase runs continuously.
 */
@Composable
fun WaveChart(
    samples: List<Sample>,
    threshold: Float,
    modifier: Modifier = Modifier,
) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val thresholdColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier) {
        if (samples.size < 2) return@Canvas
        val values = samples.map { it.celsius }
        val low = min(values.min(), threshold) - 2f
        val high = max(values.max(), threshold) + 2f
        val start = samples.first().timeMillis
        val span = max(samples.last().timeMillis - start, 1L).toFloat()

        val maxAmp = 7.dp.toPx()
        val pad = maxAmp + 4.dp.toPx()
        val plotTop = pad
        val plotHeight = size.height - 2 * pad
        fun yOf(celsius: Float) = plotTop + (1f - (celsius - low) / (high - low)) * plotHeight

        // Grid
        for (i in 0..3) {
            val y = plotTop + plotHeight * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        // Threshold
        val ty = yOf(threshold)
        drawLine(
            color = thresholdColor,
            start = Offset(0f, ty),
            end = Offset(size.width, ty),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
            cap = StrokeCap.Round,
        )

        // Wave path
        val step = 2.dp.toPx()
        val baseWavelength = 90.dp.toPx()
        val line = Path()
        var cursor = 0
        var accumulated = 0f
        var x = 0f
        while (x <= size.width) {
            val t = start + span * (x / size.width)
            while (cursor < samples.size - 2 && samples[cursor + 1].timeMillis < t) cursor++
            val a = samples[cursor]
            val b = samples[cursor + 1]
            val f = ((t - a.timeMillis) / max(b.timeMillis - a.timeMillis, 1L).toFloat()).coerceIn(0f, 1f)
            val celsius = a.celsius + (b.celsius - a.celsius) * f

            val heat = HeatScale.fraction(celsius)
            val amplitude = 1.dp.toPx() + maxAmp * heat
            accumulated += (2 * PI).toFloat() * step / baseWavelength * (0.6f + 2.4f * heat)
            val y = yOf(celsius) + sin(accumulated - phase) * amplitude

            if (x == 0f) line.moveTo(x, y) else line.lineTo(x, y)
            x += step
        }

        val brush = Brush.verticalGradient(
            colorStops = HeatScale.gradientStops(
                low = low - (high - low) * (pad / plotHeight),
                high = high + (high - low) * (pad / plotHeight),
            ),
        )
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, brush, alpha = 0.18f)
        drawPath(line, brush, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Live dot at the newest value
        val lastY = yOf(values.last())
        val dotColor = HeatScale.color(values.last())
        drawCircle(dotColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(size.width - 2.dp.toPx(), lastY))
        drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(size.width - 2.dp.toPx(), lastY))
        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(size.width - 2.dp.toPx(), lastY))
    }
}

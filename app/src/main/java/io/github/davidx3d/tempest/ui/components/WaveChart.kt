package io.github.davidx3d.tempest.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.data.history.HistoryRepository
import io.github.davidx3d.tempest.data.history.Sample
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The temperature history as a living wave. The curve follows the samples; on top of it rides a
 * sine whose amplitude and frequency both rise with the temperature at that point, so a hot
 * stretch visibly "boils" while a cool one barely ripples. The phase runs continuously.
 *
 * The x axis spans [startMillis]..[endMillis]. Stretches without samples for longer than
 * [HistoryRepository.GAP_MILLIS] (the phone slept, the app was closed) stay empty instead of being bridged.
 * Touching and sliding along the chart reports the time under the finger through [onSelect].
 */
@Composable
fun WaveChart(
    samples: List<Sample>,
    threshold: Float,
    startMillis: Long,
    endMillis: Long,
    modifier: Modifier = Modifier,
    selected: Sample? = null,
    onSelect: (Long?) -> Unit = {},
) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val thresholdColor = MaterialTheme.colorScheme.error
    val scale = LocalHeatScale.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val span = max(endMillis - startMillis, 1L).toFloat()
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier.pointerInput(startMillis, endMillis) {
            fun timeAt(x: Float) = startMillis + (span * (x / size.width).coerceIn(0f, 1f)).toLong()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                select(timeAt(down.position.x))
                // Vertical moves belong to the list, horizontal ones scrub through the chart.
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                if (drag != null) {
                    select(timeAt(drag.position.x))
                    horizontalDrag(drag.id) { change ->
                        select(timeAt(change.position.x))
                        change.consume()
                    }
                }
                select(null)
            }
        },
    ) {
        if (samples.size < 2) return@Canvas
        val values = samples.map { it.celsius }
        val low = min(values.min(), threshold) - 2f
        val high = max(values.max(), threshold) + 2f

        val maxAmp = 7.dp.toPx()
        val pad = maxAmp + 4.dp.toPx()
        val plotTop = pad
        val plotHeight = size.height - 2 * pad
        fun yOf(celsius: Float) = plotTop + (1f - (celsius - low) / (high - low)) * plotHeight
        fun xOf(time: Long) = (time - startMillis) / span * size.width

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

        // Wave path, one sub-path per stretch without a gap
        val step = 2.dp.toPx()
        val baseWavelength = 90.dp.toPx()
        val line = Path()
        val fill = Path()
        var cursor = 0
        var accumulated = 0f
        var penDown = false
        var lastX = 0f
        fun liftPen() {
            if (!penDown) return
            fill.lineTo(lastX, size.height)
            fill.close()
            penDown = false
        }
        var x = max(xOf(samples.first().timeMillis), 0f)
        val endX = min(xOf(samples.last().timeMillis), size.width)
        while (x <= endX) {
            val t = startMillis + (span * (x / size.width)).toLong()
            while (cursor < samples.size - 2 && samples[cursor + 1].timeMillis < t) cursor++
            val a = samples[cursor]
            val b = samples[cursor + 1]
            if (b.timeMillis - a.timeMillis > HistoryRepository.GAP_MILLIS) {
                liftPen()
                x += step
                continue
            }
            val f = ((t - a.timeMillis) / max(b.timeMillis - a.timeMillis, 1L).toFloat()).coerceIn(0f, 1f)
            val celsius = a.celsius + (b.celsius - a.celsius) * f

            val heat = scale.fraction(celsius)
            val amplitude = 1.dp.toPx() + maxAmp * heat
            accumulated += (2 * PI).toFloat() * step / baseWavelength * (0.6f + 2.4f * heat)
            val y = yOf(celsius) + sin(accumulated - phase) * amplitude

            if (penDown) {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            } else {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
                penDown = true
            }
            lastX = x
            x += step
        }
        liftPen()

        val brush = Brush.verticalGradient(
            colorStops = scale.gradientStops(
                low = low - (high - low) * (pad / plotHeight),
                high = high + (high - low) * (pad / plotHeight),
            ),
        )
        drawPath(fill, brush, alpha = 0.18f)
        drawPath(line, brush, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Live dot at the newest value
        val last = samples.last()
        val lastX2 = min(xOf(last.timeMillis), size.width - 2.dp.toPx())
        val lastY = yOf(last.celsius)
        val dotColor = scale.color(last.celsius)
        drawCircle(dotColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(lastX2, lastY))
        drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(lastX2, lastY))
        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(lastX2, lastY))

        // Scrub cursor
        if (selected != null) {
            val sx = xOf(selected.timeMillis).coerceIn(0f, size.width)
            val sy = yOf(selected.celsius)
            drawLine(cursorColor.copy(alpha = 0.5f), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.5.dp.toPx())
            val color = scale.color(selected.celsius)
            drawCircle(color, radius = 7.dp.toPx(), center = Offset(sx, sy))
            drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(sx, sy))
        }
    }
}

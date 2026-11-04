package io.github.davidx3d.tempest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

val LocalMotionEnabled = staticCompositionLocalOf { true }
val LocalWavesEnabled = staticCompositionLocalOf { true }

/** Decorative separator, deliberately not a loading or measurement indicator. */
@Composable
fun ExpressiveWave(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val waves = LocalWavesEnabled.current
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val path = Path()
        val amplitude = if (waves) 4.dp.toPx() else 0f
        for (x in 0..size.width.toInt() step 2) {
            val y = size.height / 2 + sin(x / 18.dp.toPx() * Math.PI).toFloat() * amplitude
            if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

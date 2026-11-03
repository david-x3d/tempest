package io.github.davidx3d.tempest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A wavy bar that grows calmer and longer when low, tighter and taller when high. */
@Composable
fun HeatBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val armed = rememberWaveArmed()
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bar")
    LinearWavyProgressIndicator(
        progress = { shown },
        modifier = modifier.fillMaxWidth().height(height),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        amplitude = { if (armed) 0.2f + 0.8f * shown else 0f },
        wavelength = (40 - 24 * shown).dp,
    )
}

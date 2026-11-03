package io.github.davidx3d.tempest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.theme.HeatBand
import io.github.davidx3d.tempest.ui.theme.HeatScale
import io.github.davidx3d.tempest.ui.theme.flexFontFamily

/**
 * The big temperature: a slowly turning MaterialShapes blob that morphs on every new reading,
 * framed by a circular wavy gauge. Cool readings use calm cookie shapes, hot ones a burst.
 */
@Composable
fun TemperatureHero(
    celsiusValue: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val shown by animateFloatAsState(celsiusValue, spatial, label = "temperature")
    val heat = HeatScale.fraction(shown)
    val band = HeatScale.band(celsiusValue)
    val color by animateColorAsState(HeatScale.color(celsiusValue), label = "heatColor")

    // Each new reading flips to the band's alternate shape, so the blob morphs on every change.
    var tick by remember { mutableStateOf(true) }
    val shape = when (band) {
        HeatBand.Cool -> if (tick) MaterialShapes.Cookie12Sided else MaterialShapes.Cookie9Sided
        HeatBand.Warm -> if (tick) MaterialShapes.Sunny else MaterialShapes.Cookie7Sided
        HeatBand.Hot -> if (tick) MaterialShapes.SoftBurst else MaterialShapes.VerySunny
    }

    val pulse = remember { Animatable(1f) }
    val fast = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(celsiusValue) {
        tick = !tick
        pulse.animateTo(1.06f, fast)
        pulse.animateTo(1f, spatial)
    }

    // Hotter spins faster.
    val spinDuration = (48_000 - 36_000 * heat).toInt()
    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spinDuration, easing = LinearEasing)),
        label = "rotation",
    )

    val density = LocalDensity.current
    val stroke = with(density) { Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round) }
    val track = with(density) { Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round) }

    Box(modifier.size(300.dp), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { heat },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            stroke = stroke,
            trackStroke = track,
            amplitude = { 0.35f + 0.65f * heat },
            wavelength = (WavyProgressIndicatorDefaults.CircularWavelength * (1.3f - 0.6f * heat)),
        )
        MorphingBlob(
            target = shape,
            color = color.copy(alpha = 0.16f),
            modifier = Modifier
                .padding(28.dp)
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                celsius(shown),
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = flexFontFamily(weight = (400 + 550 * heat).toInt(), width = 90f + 40f * heat),
                    fontSize = 60.sp,
                    lineHeight = 64.sp,
                ),
            )
            Text(
                stringResource(
                    when (band) {
                        HeatBand.Cool -> R.string.heat_cool
                        HeatBand.Warm -> R.string.heat_warm
                        HeatBand.Hot -> R.string.heat_hot
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }
    }
}

package io.github.davidx3d.tempest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.davidx3d.tempest.ui.LocalFahrenheit
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.formatUnit
import io.github.davidx3d.tempest.ui.temperatureDelta
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import io.github.davidx3d.tempest.ui.theme.HeatBand
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale
import io.github.davidx3d.tempest.ui.theme.flexFontFamily
import kotlinx.coroutines.delay
import kotlin.math.PI

private val HERO_SIZE = 300.dp

/**
 * The big temperature: a slowly turning MaterialShapes blob that morphs on every new reading,
 * framed by a circular wavy gauge. Cool readings use calm cookie shapes, hot ones a burst.
 */
@Composable
fun TemperatureHero(
    celsiusValue: Float,
    label: String,
    modifier: Modifier = Modifier,
    trendPerMinute: Float? = null,
) {
    val motionEnabled = LocalMotionEnabled.current
    val wavesEnabled = LocalWavesEnabled.current
    val scale = LocalHeatScale.current
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val shown by animateFloatAsState(celsiusValue, if (motionEnabled) spatial else androidx.compose.animation.core.snap(), label = "temperature")
    val heat = scale.fraction(shown)
    val band = scale.band(celsiusValue)
    val color by animateColorAsState(scale.color(celsiusValue), label = "heatColor")

    // Each new reading flips to the band's alternate shape, so the blob morphs on every change.
    var tick by remember { mutableStateOf(true) }
    val shape = when (band) {
        HeatBand.Cold -> if (tick) MaterialShapes.Flower else MaterialShapes.Clover8Leaf
        HeatBand.Cool -> if (tick) MaterialShapes.Cookie12Sided else MaterialShapes.Cookie9Sided
        HeatBand.Warm -> if (tick) MaterialShapes.Sunny else MaterialShapes.Cookie7Sided
        HeatBand.Hot -> if (tick) MaterialShapes.SoftBurst else MaterialShapes.VerySunny
        HeatBand.Ultra -> if (tick) MaterialShapes.Burst else MaterialShapes.Boom
    }

    val waveArmed = rememberWaveArmed()

    val pulse = remember { Animatable(1f) }
    val fast = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(celsiusValue, motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
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

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val heroSize = minOf(HERO_SIZE, maxWidth)
        Box(Modifier.size(heroSize), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { heat },
                modifier = Modifier.fillMaxSize(),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                stroke = stroke,
                trackStroke = track,
                // The indicator only builds its wave morph after an amplitude animation, so a custom
                // amplitude starts at zero and springs up once the hero is on screen.
                amplitude = { if (waveArmed && wavesEnabled) 0.35f + 0.65f * heat else 0f },
                // Hotter readings get more, tighter waves around the ring.
                wavelength = (2 * PI * (heroSize.value / 2) / (14 + 14 * heat)).toFloat().dp,
            )
            MorphingBlob(
                target = shape,
                color = color.copy(alpha = 0.16f),
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (motionEnabled) rotation else 0f
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
                        fontSize = (60 * (heroSize / HERO_SIZE)).sp,
                        lineHeight = (64 * (heroSize / HERO_SIZE)).sp,
                    ),
                )
                Text(
                    stringResource(
                        when (band) {
                            HeatBand.Cold -> R.string.heat_cold
                            HeatBand.Cool -> R.string.heat_cool
                            HeatBand.Warm -> R.string.heat_warm
                            HeatBand.Hot -> R.string.heat_hot
                            HeatBand.Ultra -> R.string.heat_ultra
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                if (trendPerMinute != null) TrendLabel(trendPerMinute)
            }
        }
    }
}

/**
 * Wavy indicators only build their wave morph after an amplitude animation has run. A custom
 * amplitude lambda should return 0 until this turns true, so the wave springs up instead of
 * staying flat.
 */
@Composable
fun rememberWaveArmed(): Boolean {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        armed = true
    }
    return armed
}

/** "Rising 0.8 °C/min" with an arrow; anything within ±0.1 °C/min counts as steady. */
@Composable
fun TrendLabel(perMinute: Float, modifier: Modifier = Modifier) {
    val delta = temperatureDelta(perMinute)
    val unit = if (LocalFahrenheit.current) R.string.unit_trend_f else R.string.unit_trend_c
    val amount = formatUnit(unit, kotlin.math.abs(delta), 1)
    val (icon, text) = when {
        perMinute > STEADY -> Icons.AutoMirrored.Rounded.TrendingUp to stringResource(R.string.trend_rising, amount)
        perMinute < -STEADY -> Icons.AutoMirrored.Rounded.TrendingDown to stringResource(R.string.trend_falling, amount)
        else -> Icons.AutoMirrored.Rounded.TrendingFlat to stringResource(R.string.trend_steady)
    }
    androidx.compose.foundation.layout.Row(
        modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private const val STEADY = 0.1f

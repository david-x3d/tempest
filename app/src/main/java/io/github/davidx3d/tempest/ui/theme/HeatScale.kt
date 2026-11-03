package io.github.davidx3d.tempest.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.davidx3d.tempest.data.settings.HeatLevels

enum class HeatBand { Cold, Cool, Warm, Hot, Ultra }

/**
 * Maps a temperature onto the colour scale used everywhere, from icy blue through orange to a
 * deep red. The colour stops sit on the user's own heat levels, so "hot" always looks hot.
 */
class HeatScale(val levels: HeatLevels) {

    val min = levels.coldBelow - 10f
    val max = levels.ultraFrom + 12f

    private val stops = listOf(
        min to Color(0xFF5B5BFF),
        levels.coldBelow to Color(0xFF1E88F0),
        levels.warmFrom - 1.5f to Color(0xFF14B8C8),
        levels.warmFrom + 1f to Color(0xFFFFB02E),
        levels.hotFrom to Color(0xFFFF7A1F),
        levels.ultraFrom to Color(0xFFE5392B),
        max to Color(0xFF9C0F3F),
    )

    fun color(celsius: Float): Color {
        if (celsius <= stops.first().first) return stops.first().second
        stops.zipWithNext().forEach { (a, b) ->
            if (celsius <= b.first) {
                return lerp(a.second, b.second, (celsius - a.first) / (b.first - a.first))
            }
        }
        return stops.last().second
    }

    /** 0 at [min], 1 at [max]. */
    fun fraction(celsius: Float): Float = ((celsius - min) / (max - min)).coerceIn(0f, 1f)

    fun band(celsius: Float): HeatBand = when {
        celsius >= levels.ultraFrom -> HeatBand.Ultra
        celsius >= levels.hotFrom -> HeatBand.Hot
        celsius >= levels.warmFrom -> HeatBand.Warm
        celsius >= levels.coldBelow -> HeatBand.Cool
        else -> HeatBand.Cold
    }

    /** Colour stops for a vertical gradient over the value range [low]..[high] (top = high). */
    fun gradientStops(low: Float, high: Float): Array<Pair<Float, Color>> {
        val span = (high - low).takeIf { it > 0f } ?: 1f
        return (0..8).map { i ->
            val t = i / 8f
            t to color(high - t * span)
        }.toTypedArray()
    }
}

val LocalHeatScale = staticCompositionLocalOf { HeatScale(HeatLevels()) }

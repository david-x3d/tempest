package io.github.davidx3d.tempest.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class HeatBand { Cool, Warm, Hot }

/** Maps a temperature onto the cool blue → warm orange → hot red scale used everywhere. */
object HeatScale {
    const val MIN = 15f
    const val MAX = 65f
    private const val WARM_FROM = 33f
    private const val HOT_FROM = 42f

    private val stops = listOf(
        15f to Color(0xFF2F7BFF),
        26f to Color(0xFF22B5D6),
        33f to Color(0xFFFFB02E),
        40f to Color(0xFFFF7A1F),
        48f to Color(0xFFE5392B),
        65f to Color(0xFFB0102A),
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

    /** 0 at [MIN], 1 at [MAX]. */
    fun fraction(celsius: Float): Float = ((celsius - MIN) / (MAX - MIN)).coerceIn(0f, 1f)

    fun band(celsius: Float): HeatBand = when {
        celsius >= HOT_FROM -> HeatBand.Hot
        celsius >= WARM_FROM -> HeatBand.Warm
        else -> HeatBand.Cool
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

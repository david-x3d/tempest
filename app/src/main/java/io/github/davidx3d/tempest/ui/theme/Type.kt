package io.github.davidx3d.tempest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.davidx3d.tempest.R

/**
 * Roboto Flex is a variable font, so the hero temperature can grow heavier and wider as it
 * heats up. Axis values are quantised to keep the number of cached typefaces small.
 */
fun flexFontFamily(weight: Int, width: Float = 100f): FontFamily {
    val w = (weight / 25 * 25).coerceIn(100, 1000)
    val wd = (width / 5f).toInt() * 5f
    return FontFamily(
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.width(wd.coerceIn(25f, 151f)),
                FontVariation.Setting("opsz", 144f),
            ),
        ),
    )
}

private val Flex = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private val Base = Typography()

private fun TextStyle.flex(weight: Int) = copy(fontFamily = Flex, fontWeight = FontWeight(weight))

val TempestTypography = Typography(
    displayLarge = Base.displayLarge.flex(700),
    displayMedium = Base.displayMedium.flex(700),
    displaySmall = Base.displaySmall.flex(600),
    headlineLarge = Base.headlineLarge.flex(700),
    headlineMedium = Base.headlineMedium.flex(600),
    headlineSmall = Base.headlineSmall.flex(600),
    titleLarge = Base.titleLarge.flex(600),
    titleMedium = Base.titleMedium.flex(600),
    titleSmall = Base.titleSmall.flex(600),
    bodyLarge = Base.bodyLarge.flex(400),
    bodyMedium = Base.bodyMedium.flex(400),
    bodySmall = Base.bodySmall.flex(400),
    labelLarge = Base.labelLarge.flex(600),
    labelMedium = Base.labelMedium.flex(500),
    labelSmall = Base.labelSmall.flex(500),
)

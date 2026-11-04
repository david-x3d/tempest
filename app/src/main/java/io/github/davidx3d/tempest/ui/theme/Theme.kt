package io.github.davidx3d.tempest.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Dynamic color from the wallpaper (minSdk 31 always has it), expressive spring motion. */
@Composable
fun TempestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) darkColorScheme(primary = Color(0xFF89E0CE), secondary = Color(0xFFB2CCC5), tertiary = Color(0xFFB6C9ED))
    else lightColorScheme(primary = Color(0xFF006B5C), secondary = Color(0xFF4B635D), tertiary = Color(0xFF465F85))
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = TempestTypography,
        content = content,
    )
}

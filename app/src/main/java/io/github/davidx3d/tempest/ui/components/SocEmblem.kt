package io.github.davidx3d.tempest.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.github.davidx3d.tempest.data.device.SocVendor

/**
 * A flat Material emblem per chip maker: a tonal MaterialShapes container in the maker's colour
 * with a simple glyph. Deliberately not the makers' trademarked logos.
 */
@Composable
fun SocEmblem(vendor: SocVendor, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val dark = isSystemInDarkTheme()
    val style = vendor.style()
    val container = if (dark) style.darkContainer else style.lightContainer
    val content = if (dark) style.darkContent else style.lightContent
    val glyph = remember(vendor, content) { glyph(vendor, content) }
    Box(
        modifier
            .size(size)
            .background(container, style.shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Image(glyph, contentDescription = vendor.displayName.ifEmpty { null }, modifier = Modifier.fillMaxSize().padding(size / 4))
    }
}

/** The maker's accent colour for text and pills next to the emblem. */
@Composable
fun socAccent(vendor: SocVendor): Color =
    if (isSystemInDarkTheme()) vendor.style().darkContent else vendor.style().lightContent

private class EmblemStyle(
    val shape: RoundedPolygon,
    val lightContainer: Color,
    val lightContent: Color,
    val darkContainer: Color,
    val darkContent: Color,
)

private fun SocVendor.style(): EmblemStyle = when (this) {
    SocVendor.Qualcomm -> EmblemStyle(MaterialShapes.Cookie9Sided, Color(0xFFFFDAD7), Color(0xFF8C1D22), Color(0xFF5C1A1F), Color(0xFFFFB3B0))
    SocVendor.MediaTek -> EmblemStyle(MaterialShapes.Cookie4Sided, Color(0xFFFFDCBE), Color(0xFF6E3B00), Color(0xFF5A3200), Color(0xFFFFB874))
    SocVendor.Samsung -> EmblemStyle(MaterialShapes.Clover4Leaf, Color(0xFFDBE1FF), Color(0xFF1B3D8F), Color(0xFF1B3366), Color(0xFFB2C5FF))
    SocVendor.Google -> EmblemStyle(MaterialShapes.Cookie6Sided, Color(0xFFBCE9FF), Color(0xFF004D61), Color(0xFF004D61), Color(0xFF8AD2EF))
    SocVendor.HiSilicon -> EmblemStyle(MaterialShapes.Flower, Color(0xFFFFD9DD), Color(0xFF8A1D33), Color(0xFF5F1424), Color(0xFFFFB2BB))
    SocVendor.Unisoc -> EmblemStyle(MaterialShapes.Cookie12Sided, Color(0xFFE9DDFF), Color(0xFF4F378A), Color(0xFF3D2C6B), Color(0xFFD0BCFF))
    SocVendor.Unknown -> EmblemStyle(MaterialShapes.Sunny, Color(0xFFE1E2EC), Color(0xFF44474F), Color(0xFF33363D), Color(0xFFC4C6D0))
}

/** 24×24 glyphs: flame, layered diamond, orbit, cube, crescent, hexagon, chip. */
private fun glyph(vendor: SocVendor, color: Color): ImageVector {
    val builder = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    val brush = SolidColor(color)
    fun fill(path: String) = builder.addPath(addPathNodes(path), fill = brush)
    fun stroke(path: String) = builder.addPath(
        addPathNodes(path),
        stroke = brush,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    when (vendor) {
        SocVendor.Qualcomm ->
            fill("M12 2.5c2 4.6 6.5 6.6 6.5 11.4 0 3.8-2.9 6.6-6.5 6.6s-6.5-2.8-6.5-6.6c0-3 2.1-4.8 3.1-7.2 1 2.2 1.9 3.2 2.8 3.6-0.4-2.6-0.2-5.3 0.6-7.8Z")
        SocVendor.MediaTek -> {
            stroke("M12 2.6L21.4 12L12 21.4L2.6 12Z")
            fill("M12 7.8L16.2 12L12 16.2L7.8 12Z")
        }
        SocVendor.Samsung -> {
            stroke("M20.23 7.25A9.5 3.8 -30 1 1 3.77 16.75A9.5 3.8 -30 1 1 20.23 7.25Z")
            fill("M12 9.4a2.6 2.6 0 1 1 0 5.2a2.6 2.6 0 1 1 0-5.2Z")
        }
        SocVendor.Google -> stroke("M12 3L20 7.5V16.5L12 21L4 16.5V7.5Z M4 7.5L12 12L20 7.5 M12 12V21")
        SocVendor.HiSilicon -> fill("M17 3.5A9 9 0 1 0 20.5 15A7 7 0 1 1 17 3.5Z")
        SocVendor.Unisoc -> {
            stroke("M12 3L19.8 7.5V16.5L12 21L4.2 16.5V7.5Z")
            fill("M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6Z")
        }
        SocVendor.Unknown ->
            stroke("M8 6H16Q18 6 18 8V16Q18 18 16 18H8Q6 18 6 16V8Q6 6 8 6Z M9 3V6 M12 3V6 M15 3V6 M9 18V21 M12 18V21 M15 18V21 M3 9H6 M3 12H6 M3 15H6 M18 9H21 M18 12H21 M18 15H21")
    }
    return builder.build()
}

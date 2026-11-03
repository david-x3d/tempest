package io.github.davidx3d.tempest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** A [Shape] that is [progress] of the way through [morph]. MaterialShapes are normalised to 0..1. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * A filled blob that morphs with a spring whenever [target] changes. The previous shape is kept
 * as the morph start, so a change mid-animation continues from where the eye last saw it.
 */
@Composable
fun MorphingBlob(
    target: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = if (progress.value >= 0.5f) to else from
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }

    val morph = remember(from, to) { Morph(from, to) }
    Box(
        modifier = modifier.background(color, MorphShape(morph, progress.value)),
        content = { content() },
    )
}

package io.github.davidx3d.tempest.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection

/** Adds two paddings, e.g. Scaffold insets plus the screen's own margin. */
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        this@plus.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = this@plus.calculateTopPadding() + other.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        this@plus.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = this@plus.calculateBottomPadding() + other.calculateBottomPadding()
}

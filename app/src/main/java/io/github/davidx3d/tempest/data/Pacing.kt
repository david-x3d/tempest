package io.github.davidx3d.tempest.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits [foregroundMillis] while the app is on screen, otherwise [backgroundMillis]. A background
 * wait ends early as soon as the app comes back, so the screen never shows stale values.
 */
suspend fun StateFlow<Boolean>.pace(foregroundMillis: Long, backgroundMillis: Long) {
    if (value) {
        delay(foregroundMillis)
    } else {
        withTimeoutOrNull(backgroundMillis) { first { it } }
    }
}

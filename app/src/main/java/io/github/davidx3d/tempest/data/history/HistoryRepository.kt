package io.github.davidx3d.tempest.data.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Sample(val timeMillis: Long, val celsius: Float)

/**
 * Keeps the last [WINDOW_MILLIS] of battery and SoC temperatures in memory, sampled every
 * [SAMPLE_MILLIS]. Nothing is written to disk; the history lives as long as the process.
 */
class HistoryRepository(
    scope: CoroutineScope,
    batteryCelsius: () -> Float?,
    socCelsius: () -> Float?,
) {
    private val _battery = MutableStateFlow<List<Sample>>(emptyList())
    val battery: StateFlow<List<Sample>> = _battery.asStateFlow()

    private val _soc = MutableStateFlow<List<Sample>>(emptyList())
    val soc: StateFlow<List<Sample>> = _soc.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                batteryCelsius()?.let { _battery.append(Sample(now, it)) }
                socCelsius()?.let { _soc.append(Sample(now, it)) }
                delay(SAMPLE_MILLIS)
            }
        }
    }

    private fun MutableStateFlow<List<Sample>>.append(sample: Sample) = update { samples ->
        val cutoff = sample.timeMillis - WINDOW_MILLIS
        (samples + sample).dropWhile { it.timeMillis < cutoff }
    }

    companion object {
        const val WINDOW_MINUTES = 30
        const val WINDOW_MILLIS = WINDOW_MINUTES * 60_000L
        const val SAMPLE_MILLIS = 5_000L
    }
}

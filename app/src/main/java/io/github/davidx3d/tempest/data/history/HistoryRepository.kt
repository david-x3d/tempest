package io.github.davidx3d.tempest.data.history

import io.github.davidx3d.tempest.data.pace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Sample(val timeMillis: Long, val celsius: Float)

/**
 * Battery and SoC temperatures of the last [KEEP_MILLIS], in memory. Sampled every
 * [SAMPLE_MILLIS] while Tempest is on screen and every [BACKGROUND_SAMPLE_MILLIS] while it only
 * runs in the background. Samples older than [DETAIL_MILLIS] are thinned to one per minute.
 * The background recorder fills the gaps while the app was closed through [import].
 */
class HistoryRepository(
    scope: CoroutineScope,
    foreground: StateFlow<Boolean>,
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
                batteryCelsius()?.let { sample -> _battery.update { thin(it + Sample(now, sample), now) } }
                socCelsius()?.let { sample -> _soc.update { thin(it + Sample(now, sample), now) } }
                foreground.pace(SAMPLE_MILLIS, BACKGROUND_SAMPLE_MILLIS)
            }
        }
    }

    /** Merges recorded samples in, skipping those that land next to one the app took itself. */
    fun import(battery: List<Sample>, soc: List<Sample>) {
        if (battery.isNotEmpty()) _battery.update { merge(it, battery) }
        if (soc.isNotEmpty()) _soc.update { merge(it, soc) }
    }

    fun clear() {
        _battery.value = emptyList()
        _soc.value = emptyList()
    }

    private fun merge(existing: List<Sample>, incoming: List<Sample>): List<Sample> {
        val times = existing.map { it.timeMillis }
        val fresh = incoming.filter { sample ->
            val i = times.binarySearch(sample.timeMillis).let { if (it < 0) -it - 1 else it }
            val before = times.getOrNull(i - 1)
            val after = times.getOrNull(i)
            (before == null || sample.timeMillis - before > DEDUP_MILLIS) && (after == null || after - sample.timeMillis > DEDUP_MILLIS)
        }
        if (fresh.isEmpty()) return existing
        val all = (existing + fresh).sortedBy { it.timeMillis }
        return thin(all, maxOf(all.last().timeMillis, System.currentTimeMillis()))
    }

    private fun thin(samples: List<Sample>, now: Long): List<Sample> {
        val keepFrom = now - KEEP_MILLIS
        val detailFrom = now - DETAIL_MILLIS
        val out = ArrayList<Sample>(samples.size)
        for (sample in samples) {
            if (sample.timeMillis < keepFrom) continue
            val previous = out.lastOrNull()
            if (sample.timeMillis < detailFrom && previous != null && sample.timeMillis - previous.timeMillis < COARSE_MILLIS) continue
            out += sample
        }
        return out
    }

    companion object {
        const val KEEP_MILLIS = 24 * 60 * 60_000L
        const val DETAIL_MILLIS = 30 * 60_000L
        const val COARSE_MILLIS = 60_000L
        const val SAMPLE_MILLIS = 5_000L
        const val BACKGROUND_SAMPLE_MILLIS = 30_000L
        private const val DEDUP_MILLIS = 20_000L

        /** Longer than this without a sample counts as a gap (the phone slept, nothing recorded). */
        const val GAP_MILLIS = 11 * 60_000L
    }
}

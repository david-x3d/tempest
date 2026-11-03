package io.github.davidx3d.tempest.alerts

/**
 * Decides when to fire an alert: once when a reading crosses its threshold, again only after it
 * has cooled down by [HYSTERESIS] °C, or every [repeatMillis] while it stays above the limit.
 * Prevents a notification on every reading near the limit.
 */
class AlertWatcher {
    private val armed = mutableMapOf(AlertSource.Battery to true, AlertSource.Soc to true)
    private val lastFired = mutableMapOf<AlertSource, Long>()

    /** Returns true when an alert should be shown now. */
    fun check(source: AlertSource, celsius: Float?, threshold: Float, repeatMillis: Long = 0, now: Long = 0): Boolean {
        if (celsius == null) return false
        return when {
            celsius >= threshold && armed[source] == true -> {
                armed[source] = false
                lastFired[source] = now
                true
            }
            celsius >= threshold && repeatMillis > 0 && now - (lastFired[source] ?: now) >= repeatMillis -> {
                lastFired[source] = now
                true
            }
            celsius < threshold - HYSTERESIS -> {
                armed[source] = true
                false
            }
            else -> false
        }
    }

    companion object {
        const val HYSTERESIS = 1.5f
    }
}

package io.github.davidx3d.tempest.alerts

/**
 * Decides when to fire an alert: once when a reading crosses its threshold, and again only after
 * it has cooled down by [HYSTERESIS] °C. Prevents a notification on every reading near the limit.
 */
class AlertWatcher {
    private val armed = mutableMapOf(AlertSource.Battery to true, AlertSource.Soc to true)

    /** Returns true when an alert should be shown now. */
    fun check(source: AlertSource, celsius: Float?, threshold: Float): Boolean {
        if (celsius == null) return false
        return when {
            celsius >= threshold && armed[source] == true -> {
                armed[source] = false
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

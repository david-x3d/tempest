package io.github.davidx3d.tempest.data.history

/**
 * How fast the temperature is moving, in °C per minute, from a least-squares fit over the last
 * [windowMillis]. Null until there are enough samples to say anything.
 */
fun List<Sample>.trendPerMinute(windowMillis: Long = 120_000L): Float? {
    val last = lastOrNull() ?: return null
    val recent = filter { it.timeMillis >= last.timeMillis - windowMillis }
    if (recent.size < 4) return null
    val t0 = recent.first().timeMillis
    val xs = recent.map { (it.timeMillis - t0) / 60_000.0 }
    val ys = recent.map { it.celsius.toDouble() }
    val mx = xs.average()
    val my = ys.average()
    val den = xs.sumOf { (it - mx) * (it - mx) }
    if (den == 0.0) return null
    val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
    return (num / den).toFloat()
}

/** Plain CSV (ISO time, battery °C, SoC °C) for sharing; blank where a series has no sample. */
fun historyCsv(battery: List<Sample>, soc: List<Sample>): String {
    val socByTime = soc.associateBy { it.timeMillis }
    val times = (battery.map { it.timeMillis } + soc.map { it.timeMillis }).distinct().sorted()
    val batteryByTime = battery.associateBy { it.timeMillis }
    return buildString {
        appendLine("time,battery_celsius,soc_celsius")
        times.forEach { t ->
            append(java.time.Instant.ofEpochMilli(t)).append(',')
            append(batteryByTime[t]?.celsius?.toString().orEmpty()).append(',')
            appendLine(socByTime[t]?.celsius?.toString().orEmpty())
        }
    }
}

data class HistoryStats(val min: Float, val max: Float, val average: Float, val aboveMillis: Long)

/**
 * Min, max, time-weighted average and how long the curve stayed at or above [threshold]. Weighted
 * by time so a stretch sampled every 5 s does not outweigh one sampled every minute; gaps count
 * for nothing.
 */
fun List<Sample>.stats(threshold: Float): HistoryStats? {
    if (isEmpty()) return null
    var weighted = 0.0
    var weight = 0L
    var above = 0L
    zipWithNext { a, b ->
        val dt = b.timeMillis - a.timeMillis
        if (dt <= 0 || dt > HistoryRepository.GAP_MILLIS) return@zipWithNext
        weighted += (a.celsius + b.celsius) / 2.0 * dt
        weight += dt
        above += when {
            a.celsius >= threshold && b.celsius >= threshold -> dt
            a.celsius >= threshold || b.celsius >= threshold -> dt / 2
            else -> 0
        }
    }
    val average = if (weight > 0) (weighted / weight).toFloat() else map { it.celsius }.average().toFloat()
    return HistoryStats(minOf { it.celsius }, maxOf { it.celsius }, average, above)
}

/** The sample closest to [timeMillis] in a list sorted by time. */
fun List<Sample>.nearest(timeMillis: Long): Sample? {
    if (isEmpty()) return null
    val i = binarySearch { it.timeMillis.compareTo(timeMillis) }.let { if (it < 0) -it - 1 else it }
    return listOfNotNull(getOrNull(i - 1), getOrNull(i)).minByOrNull { kotlin.math.abs(it.timeMillis - timeMillis) }
}

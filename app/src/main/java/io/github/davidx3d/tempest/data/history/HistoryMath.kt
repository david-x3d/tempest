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

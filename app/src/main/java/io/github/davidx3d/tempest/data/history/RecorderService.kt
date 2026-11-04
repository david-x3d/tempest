package io.github.davidx3d.tempest.data.history

import io.github.davidx3d.tempest.IRecorderService
import io.github.davidx3d.tempest.data.thermal.ZoneKinds
import java.io.File
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Records battery and SoC temperatures while Tempest is closed. Shizuku starts it as a daemon
 * with shell (or root) identity, so it keeps running after the app is gone and needs neither a
 * foreground service nor a notification. Instantiated by Shizuku, never by the app.
 *
 * Kind to the battery: it never holds a wake lock. Its timer stops while the phone sleeps and it
 * samples again once the phone is awake anyway. Each sample reads a handful of sysfs files.
 */
class RecorderService : IRecorderService.Stub() {

    private val lines = ArrayDeque<String>()
    private val wake = Semaphore(0)
    @Volatile private var intervalMillis = DEFAULT_INTERVAL_MILLIS
    private var socFiles: List<File>? = null
    private var batteryFileWorks = true
    private var lastCompact = 0L

    init {
        load()
        Thread(::loop, "tempest-recorder").apply {
            isDaemon = true
            start()
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun setInterval(seconds: Int) {
        val millis = seconds.coerceIn(15, 900) * 1000L
        if (millis == intervalMillis) return
        intervalMillis = millis
        wake.release()
    }

    override fun samplesSince(sinceMillis: Long): String = synchronized(lines) {
        lines.filter { (it.substringBefore(',').toLongOrNull() ?: 0L) > sinceMillis }.joinToString("\n")
    }

    override fun count(): Int = synchronized(lines) { lines.size }

    override fun clear() {
        synchronized(lines) { lines.clear() }
        FILE.delete()
    }

    private fun loop() {
        while (true) {
            runCatching { record() }
            // Semaphore timeouts run on the monotonic clock, which stands still in deep sleep.
            wake.drainPermits()
            wake.tryAcquire(intervalMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun record() {
        val now = System.currentTimeMillis()
        val battery = batteryCelsius()
        val soc = socCelsius()
        if (battery == null && soc == null) return
        val line = "$now,${battery.format()},${soc.format()}"
        synchronized(lines) {
            lines.addLast(line)
            while ((lines.firstOrNull()?.substringBefore(',')?.toLongOrNull() ?: now) < now - KEEP_MILLIS) lines.removeFirst()
        }
        persist(line, now)
    }

    private fun batteryCelsius(): Float? {
        if (batteryFileWorks) {
            readLong(BATTERY_TEMP)?.let { return (it / 10f).takeIf { c -> c in -40f..120f } }
            batteryFileWorks = false
        }
        // Slower fallback for phones that hide the sysfs file even from the shell.
        val dump = runCatching {
            ProcessBuilder("dumpsys", "battery").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        }.getOrNull() ?: return null
        return Regex("""temperature:\s*(-?\d+)""").find(dump)?.groupValues?.get(1)?.toIntOrNull()
            ?.let { it / 10f }?.takeIf { it in -40f..120f }
    }

    /** Same choice as the app: the hottest of the zones that look like part of the SoC. */
    private fun socCelsius(): Float? {
        val files = socFiles ?: discoverSocZones().also { socFiles = it }
        if (files.isNotEmpty()) return files.mapNotNull { file -> readLong(file)?.let(ZoneKinds::celsius) }.maxOrNull()
        // Zones hidden even from the shell (ColorOS): ask the thermal HAL instead.
        return runCatching {
            ProcessBuilder("dumpsys", "thermalservice").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        }.getOrNull()?.let(ZoneKinds::halSoc)
    }

    private fun discoverSocZones(): List<File> {
        val zones = File(THERMAL_DIR).listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.filter { readLong(File(it, "temp"))?.let(ZoneKinds::celsius) != null }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
        val types = zones.map { runCatching { File(it, "type").readText().trim() }.getOrDefault("") }
        val picked = ZoneKinds.socCandidates(types)
        return zones.filterIndexed { i, _ -> i in picked }.map { File(it, "temp") }
    }

    private fun readLong(file: File): Long? = runCatching { file.readText().trim().toLong() }.getOrNull()

    private fun Float?.format() = this?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty()

    private fun load() {
        val cutoff = System.currentTimeMillis() - KEEP_MILLIS
        runCatching {
            FILE.readLines().filter { (it.substringBefore(',').toLongOrNull() ?: 0L) >= cutoff }.forEach(lines::addLast)
        }
    }

    /** Appends one line; once an hour rewrites the file so it never holds more than a day. */
    private fun persist(line: String, now: Long) = runCatching {
        DIR.mkdirs()
        if (now - lastCompact > COMPACT_MILLIS) {
            lastCompact = now
            val snapshot = synchronized(lines) { lines.toList() }
            val temp = File(DIR, "history.tmp")
            temp.writeText(snapshot.joinToString("\n", postfix = "\n"))
            temp.renameTo(FILE)
        } else {
            FILE.appendText(line + "\n")
        }
    }

    private companion object {
        val DIR = File("/data/local/tmp/tempest")
        val FILE = File(DIR, "history.csv")
        val BATTERY_TEMP = File("/sys/class/power_supply/battery/temp")
        const val THERMAL_DIR = "/sys/class/thermal"
        const val DEFAULT_INTERVAL_MILLIS = 60_000L
        const val KEEP_MILLIS = HistoryRepository.KEEP_MILLIS
        const val COMPACT_MILLIS = 60 * 60_000L
    }
}

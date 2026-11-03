package io.github.davidx3d.tempest.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import io.github.davidx3d.tempest.data.privilege.PrivilegeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

/**
 * Everything the CPU tab shows about the chip and the phone, CPU-Z style. Static facts are read
 * once in the background; memory, GPU load and uptime tick every second while someone watches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepository(
    private val context: Context,
    private val privilege: PrivilegeManager,
    onlineLookup: Flow<Boolean>,
    scope: CoroutineScope,
) {

    private val _info = MutableStateFlow<DeviceInfo?>(null)
    val info: StateFlow<DeviceInfo?> = _info.asStateFlow()
    private val lookup = SocLookup(context)

    init {
        scope.launch(Dispatchers.IO) { _info.value = runCatching { load() }.getOrNull() }
        // Chips missing from the catalog are looked up once the user allows it.
        scope.launch(Dispatchers.IO) {
            onlineLookup.distinctUntilChanged().collectLatest { enabled ->
                if (!enabled) return@collectLatest
                val soc = info.first { it != null }?.soc ?: return@collectLatest
                if (soc.nameSource != NameSource.Raw) return@collectLatest
                val name = listOfNotNull(soc.code, soc.hardware, soc.platform).firstNotNullOfOrNull { lookup.find(it) }
                    ?: return@collectLatest
                _info.update { current ->
                    current?.copy(soc = current.soc.copy(name = name.name, process = name.process ?: current.soc.process, nameSource = NameSource.Online))
                }
            }
        }
    }

    fun live(privileged: Flow<Boolean>): Flow<LiveChip> = privileged
        .distinctUntilChanged()
        .flatMapLatest { root ->
            flow {
                var previous: Map<Int, CpuTimes>? = null
                while (true) {
                    val gpu = readGpu(root)
                    val times = readCpuTimes(root)
                    val load = previous?.let { cpuLoad(it, times) }.orEmpty()
                    previous = times
                    emit(
                        LiveChip(
                            memory = readMemory(),
                            gpuLoadPercent = gpu.first,
                            gpuMhz = gpu.second,
                            uptimeMillis = SystemClock.elapsedRealtime(),
                            cpuLoadPercent = load[ALL_CORES],
                            coreLoadPercent = load - ALL_CORES,
                        ),
                    )
                    delay(LIVE_MS)
                }
            }
        }
        .flowOn(Dispatchers.IO)

    private fun load(): DeviceInfo {
        val props = systemProperties()
        val cpuinfo = readText("/proc/cpuinfo").orEmpty()
        val hardware = cpuinfo.lineSequence().firstOrNull { it.startsWith("Hardware") }?.substringAfter(':')?.trim()
        val platform = props["ro.board.platform"]
        val socModel = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val socMaker = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val gpu = readGl()
        val vendor = SocCatalog.vendor(socMaker, socModel, platform, hardware, Build.HARDWARE, gpu.renderer)
        val known = SocCatalog.name(socModel, platform, hardware, Build.HARDWARE)

        return DeviceInfo(
            soc = SocInfo(
                vendor = vendor,
                name = known?.name ?: socModel ?: hardware ?: platform ?: Build.HARDWARE,
                nameSource = if (known != null) NameSource.Catalog else NameSource.Raw,
                code = socModel ?: platform,
                process = known?.process,
                hardware = hardware ?: Build.HARDWARE,
                platform = platform,
            ),
            clusters = clusters(cpuinfo),
            coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(coreIndices().size),
            governor = readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"),
            abis = Build.SUPPORTED_ABIS.toList(),
            gpu = gpu.copy(vulkan = vulkanVersion()),
            display = display(),
            system = SystemInfo(
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                device = Build.DEVICE,
                board = Build.BOARD,
                androidVersion = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
                buildId = Build.DISPLAY,
                kernel = readText("/proc/version")?.split(' ')?.getOrNull(2) ?: System.getProperty("os.version"),
                baseband = Build.getRadioVersion()?.takeIf { it.isNotBlank() },
                bootloader = Build.BOOTLOADER.takeIf { it.isNotBlank() && it != Build.UNKNOWN },
                javaVm = System.getProperty("java.vm.version"),
                treble = props["ro.treble.enabled"]?.let { it == "true" },
                abSlot = props["ro.boot.slot_suffix"]?.removePrefix("_")?.uppercase()?.takeIf { it.isNotBlank() },
            ),
            storage = StatFs(Environment.getDataDirectory().path).let { StorageInfo(it.totalBytes, it.availableBytes) },
        )
    }

    private fun coreIndices(): List<Int> =
        File(CPU_DIR).listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
            ?.map { it.name.removePrefix("cpu").toInt() }?.sorted().orEmpty()

    /** One cluster per cpufreq policy; falls back to grouping cores by their max clock. */
    private fun clusters(cpuinfo: String): List<CpuCluster> {
        val parts = coreParts(cpuinfo)
        val policies = File("$CPU_DIR/cpufreq").listFiles { f -> f.name.startsWith("policy") }
            ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: 0 }
            .orEmpty()
        val groups = if (policies.isNotEmpty()) {
            policies.mapNotNull { policy ->
                val cores = readText("${policy.path}/related_cpus")?.split(' ')?.mapNotNull { it.toIntOrNull() }
                    ?: return@mapNotNull null
                Triple(cores, readText("${policy.path}/cpuinfo_min_freq")?.toLongOrNull(), readText("${policy.path}/cpuinfo_max_freq")?.toLongOrNull())
            }
        } else {
            coreIndices()
                .groupBy { readText("$CPU_DIR/cpu$it/cpufreq/cpuinfo_max_freq")?.toLongOrNull() }
                .map { (max, cores) -> Triple(cores, readText("$CPU_DIR/cpu${cores.first()}/cpufreq/cpuinfo_min_freq")?.toLongOrNull(), max) }
        }
        // Prime cores last, like CPU-Z: little, mid, big, prime.
        return groups
            .map { (cores, min, max) -> CpuCluster(cores, cores.firstNotNullOfOrNull { parts[it] }, min, max) }
            .sortedWith(compareBy({ it.maxKhz ?: 0 }, { it.cores.first() }))
    }

    /** processor index -> core name, from the "CPU implementer" and "CPU part" lines. */
    private fun coreParts(cpuinfo: String): Map<Int, String> {
        val out = HashMap<Int, String>()
        cpuinfo.split(Regex("\\n\\s*\\n")).forEach { block ->
            val fields = block.lineSequence()
                .mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0].trim() to it[1].trim() }
            val index = fields["processor"]?.toIntOrNull() ?: return@forEach
            val implementer = fields["CPU implementer"]?.removePrefix("0x")?.toIntOrNull(16)
            val part = fields["CPU part"]?.removePrefix("0x")?.toIntOrNull(16)
            CoreNames.of(implementer, part)?.let { out[index] = it }
        }
        return out
    }

    /** Asks the GPU driver for its name through a throwaway 1×1 OpenGL ES context. */
    private fun readGl(): GpuInfo = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE),
            0, configs, 0, 1, count, 0,
        )
        val config = configs[0] ?: error("no EGL config")
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        try {
            EGL14.eglMakeCurrent(display, surface, surface, ctx)
            GpuInfo(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                glVersion = GLES20.glGetString(GLES20.GL_VERSION)?.removePrefix("OpenGL ES ")?.substringBefore(' ')
                    ?.let { "OpenGL ES $it" },
                vulkan = null,
            )
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display, ctx)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglTerminate(display)
        }
    }.getOrElse { GpuInfo(null, null, null, null) }

    private fun vulkanVersion(): String? {
        val feature = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION } ?: return null
        val v = feature.version
        return "Vulkan ${v shr 22}.${(v shr 12) and 0x3ff}"
    }

    private fun display(): DisplayInfo {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val mode = display.mode
        val w = mode.physicalWidth
        val h = mode.physicalHeight
        val inches = if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            val x = minOf(w, h) / metrics.xdpi
            val y = maxOf(w, h) / metrics.ydpi
            sqrt(x * x + y * y).takeIf { it in 2f..20f }
        } else {
            null
        }
        return DisplayInfo(
            widthPx = minOf(w, h),
            heightPx = maxOf(w, h),
            densityDpi = metrics.densityDpi,
            refreshHz = display.refreshRate,
            maxRefreshHz = display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate,
            diagonalInches = inches,
            hdr = display.isHdr,
        )
    }

    private fun readMemory(): MemoryInfo? {
        val fields = readText("/proc/meminfo")?.lineSequence()
            ?.mapNotNull { line ->
                val key = line.substringBefore(':')
                val kb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                key to kb * 1024
            }?.toMap() ?: return null
        val total = fields["MemTotal"] ?: return null
        return MemoryInfo(
            totalBytes = total,
            availableBytes = fields["MemAvailable"] ?: fields["MemFree"] ?: 0,
            swapTotalBytes = fields["SwapTotal"] ?: 0,
            swapFreeBytes = fields["SwapFree"] ?: 0,
            cachedBytes = fields["Cached"] ?: 0,
        )
    }

    /** (load %, clock MHz) from the Adreno, Mali or MediaTek GED driver, whichever answers. */
    private suspend fun readGpu(root: Boolean): Pair<Int?, Int?> {
        val files = if (root) {
            privilege.run(GPU_FILES.joinToString(" ") { "echo \"$it|\$(cat $it 2>/dev/null)\";" })
                ?.lineSequence()
                ?.mapNotNull { line -> line.split('|', limit = 2).takeIf { it.size == 2 && it[1].isNotBlank() } }
                ?.associate { it[0] to it[1].trim() }
                .orEmpty()
        } else {
            GPU_FILES.mapNotNull { path -> readText(path)?.let { path to it } }.toMap()
        }
        val load = files[ADRENO_BUSY]?.substringBefore(' ')?.trim()?.toIntOrNull()
            ?: files[GED_LOAD]?.substringBefore(' ')?.toIntOrNull()
            ?: files[MALI_LOAD]?.substringBefore(' ')?.toIntOrNull()
        val mhz = files[ADRENO_CLOCK]?.toLongOrNull()?.let { (it / 1_000_000).toInt() }
            ?: files[GED_FREQ]?.split(' ')?.lastOrNull()?.toLongOrNull()?.let { (it / 1000).toInt() }
            ?: files[MALI_FREQ]?.toLongOrNull()?.let { (it / 1_000_000).toInt() }
        return load?.coerceIn(0, 100) to mhz?.takeIf { it > 0 }
    }

    /** Busy and total jiffies per core from /proc/stat; key [ALL_CORES] is the whole CPU. */
    private suspend fun readCpuTimes(privileged: Boolean): Map<Int, CpuTimes> {
        val text = readText("/proc/stat")?.takeIf { it.startsWith("cpu") }
            ?: (if (privileged) privilege.run("cat /proc/stat") else null)
            ?: return emptyMap()
        return text.lineSequence().filter { it.startsWith("cpu") }.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val id = parts[0].removePrefix("cpu").let { if (it.isEmpty()) ALL_CORES else it.toIntOrNull() } ?: return@mapNotNull null
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return@mapNotNull null
            // user nice system idle iowait irq softirq steal
            val idle = values[3] + values.getOrElse(4) { 0 }
            id to CpuTimes(total = values.take(8).sum(), idle = idle)
        }.toMap()
    }

    private fun cpuLoad(before: Map<Int, CpuTimes>, after: Map<Int, CpuTimes>): Map<Int, Int> =
        after.mapNotNull { (id, now) ->
            val then = before[id] ?: return@mapNotNull null
            val total = now.total - then.total
            if (total <= 0) return@mapNotNull null
            id to (((total - (now.idle - then.idle)) * 100f) / total).toInt().coerceIn(0, 100)
        }.toMap()

    private class CpuTimes(val total: Long, val idle: Long)

    /** `getprop` lists every system property as "[key]: [value]". */
    private fun systemProperties(): Map<String, String> = runCatching {
        val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val match = PROP.matchEntire(line) ?: return@mapNotNull null
                match.groupValues[1] to match.groupValues[2]
            }.toMap()
        }.also { process.destroy() }
    }.getOrDefault(emptyMap())

    private fun readText(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val CPU_DIR = "/sys/devices/system/cpu"
        const val LIVE_MS = 1_000L
        const val ALL_CORES = -1
        val PROP = Regex("""\[(.+?)]: \[(.*)]""")
        const val ADRENO_BUSY = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
        const val ADRENO_CLOCK = "/sys/class/kgsl/kgsl-3d0/gpuclk"
        const val GED_LOAD = "/sys/kernel/ged/hal/gpu_utilization"
        const val GED_FREQ = "/sys/kernel/ged/hal/current_freqency"
        const val MALI_LOAD = "/sys/class/misc/mali0/device/utilization"
        const val MALI_FREQ = "/sys/class/misc/mali0/device/cur_freq"
        val GPU_FILES = listOf(ADRENO_BUSY, ADRENO_CLOCK, GED_LOAD, GED_FREQ, MALI_LOAD, MALI_FREQ)
    }
}

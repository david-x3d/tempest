package io.github.davidx3d.tempest.data.device

/** Facts that do not change while the app runs, gathered once in the background. */
data class DeviceInfo(
    val soc: SocInfo,
    val clusters: List<CpuCluster>,
    val coreCount: Int,
    val governor: String?,
    val abis: List<String>,
    val gpu: GpuInfo,
    val display: DisplayInfo,
    val system: SystemInfo,
    val storage: StorageInfo,
)

data class SocInfo(
    val vendor: SocVendor,
    /** "Snapdragon 8 Gen 3", or the raw code when the chip is not in [SocCatalog]. */
    val name: String,
    val code: String?,
    val process: String?,
    val hardware: String?,
    val platform: String?,
)

/** Cores that share one frequency policy, e.g. "4× Cortex-A520 up to 2,0 GHz". */
data class CpuCluster(
    val cores: List<Int>,
    val coreName: String?,
    val minKhz: Long?,
    val maxKhz: Long?,
)

data class GpuInfo(
    val renderer: String?,
    val vendor: String?,
    val glVersion: String?,
    val vulkan: String?,
)

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshHz: Float,
    val maxRefreshHz: Float,
    val diagonalInches: Float?,
    val hdr: Boolean,
)

data class SystemInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val androidVersion: String,
    val sdk: Int,
    val securityPatch: String?,
    val buildId: String,
    val kernel: String?,
    val baseband: String?,
    val bootloader: String?,
    val javaVm: String?,
    val treble: Boolean?,
    val abSlot: String?,
)

data class StorageInfo(val totalBytes: Long, val freeBytes: Long)

/** Readings that change all the time. */
data class LiveChip(
    val memory: MemoryInfo? = null,
    val gpuLoadPercent: Int? = null,
    val gpuMhz: Int? = null,
    val uptimeMillis: Long = 0,
)

data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
    val cachedBytes: Long,
)

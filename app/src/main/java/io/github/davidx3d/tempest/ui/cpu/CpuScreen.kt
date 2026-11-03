package io.github.davidx3d.tempest.ui.cpu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.device.CpuCluster
import io.github.davidx3d.tempest.data.device.DeviceInfo
import io.github.davidx3d.tempest.data.device.NameSource
import io.github.davidx3d.tempest.data.device.LiveChip
import io.github.davidx3d.tempest.data.device.SocVendor
import io.github.davidx3d.tempest.data.thermal.CpuCore
import io.github.davidx3d.tempest.data.privilege.Access
import io.github.davidx3d.tempest.data.privilege.PrivilegeMode
import io.github.davidx3d.tempest.ui.bytes
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.clock
import io.github.davidx3d.tempest.ui.components.HeatBar
import io.github.davidx3d.tempest.ui.components.InfoRow
import io.github.davidx3d.tempest.ui.components.SectionCard
import io.github.davidx3d.tempest.ui.components.SocEmblem
import io.github.davidx3d.tempest.ui.components.TonalPill
import io.github.davidx3d.tempest.ui.components.rememberWaveArmed
import io.github.davidx3d.tempest.ui.currentLocale
import io.github.davidx3d.tempest.ui.formatNumber
import io.github.davidx3d.tempest.ui.formatUnit
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale

@Composable
fun CpuScreen(
    contentPadding: PaddingValues,
    viewModel: CpuViewModel = viewModel(factory = CpuViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val info = state.info

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.cpu_title), style = MaterialTheme.typography.displaySmall) }

        if (info == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
            }
            return@LazyColumn
        }

        item { SocCard(info, state) }
        item { CoresCard(state.cores, info.coreCount, state.live.coreLoadPercent) }
        if (info.clusters.isNotEmpty()) item { ClustersCard(info) }
        item { GpuCard(info, state.live) }
        item { MemoryCard(info, state.live) }
        item { DisplayCard(info) }
        item { SystemCard(info, state.live, state.access) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SocCard(info: DeviceInfo, state: CpuUiState) {
    val soc = info.soc
    val maxKhz = info.clusters.mapNotNull { it.maxKhz }.maxOrNull()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocEmblem(soc.vendor)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(soc.name, style = MaterialTheme.typography.headlineSmall)
                val subtitle = listOfNotNull(
                    soc.vendor.displayName.takeIf { soc.vendor != SocVendor.Unknown },
                    soc.code?.uppercase()?.takeIf { !soc.name.contains(it, ignoreCase = true) },
                    soc.process,
                    stringResource(R.string.cpu_name_online).takeIf { soc.nameSource == NameSource.Online },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.socCelsius?.let { temp ->
                val heat by animateColorAsState(LocalHeatScale.current.color(temp), label = "socHeat")
                TonalPill(celsius(temp), container = heat.copy(alpha = 0.22f), content = MaterialTheme.colorScheme.onSurface)
            }
            TonalPill(pluralStringResource(R.plurals.cpu_core_count, info.coreCount, info.coreCount))
            maxKhz?.let { TonalPill(stringResource(R.string.cpu_up_to, clock(it))) }
            info.abis.firstOrNull()?.let { TonalPill(it) }
        }
        state.live.cpuLoadPercent?.let { load ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(stringResource(R.string.cpu_load), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(formatUnit(R.string.unit_percent, load.toFloat(), 0), style = MaterialTheme.typography.titleSmall)
                }
                HeatBar(load / 100f, MaterialTheme.colorScheme.secondary)
            }
        }
        state.clockLoad?.let { load ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(stringResource(R.string.cpu_clock_load), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(formatUnit(R.string.unit_percent, load * 100f, 0), style = MaterialTheme.typography.titleSmall)
                }
                HeatBar(load, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Two cores per row: a small wavy ring for current/max clock and the live frequency. */
@Composable
private fun CoresCard(cores: List<CpuCore>, coreCount: Int, loads: Map<Int, Int>) {
    val byIndex = cores.associateBy { it.index }
    val indices = (0 until maxOf(coreCount, (cores.maxOfOrNull { it.index } ?: -1) + 1)).toList()
    SectionCard(title = stringResource(R.string.cpu_cores_title), subtitle = stringResource(R.string.cpu_cores_body)) {
        indices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index -> CoreTile(index, byIndex[index], loads[index], Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoreTile(index: Int, core: CpuCore?, loadPercent: Int?, modifier: Modifier = Modifier) {
    val fraction = core?.let { c -> (c.currentKhz.toFloat() / (c.maxKhz?.takeIf { it > 0 } ?: c.currentKhz)).coerceIn(0f, 1f) } ?: 0f
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "core$index")
    val armed = rememberWaveArmed()
    val color = if (core == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    val stroke = with(LocalDensity.current) { Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round) }
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularWavyProgressIndicator(
                progress = { shown },
                modifier = Modifier.size(40.dp),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                stroke = stroke,
                trackStroke = stroke,
                amplitude = { if (armed) 0.3f + 0.7f * it else 0f },
                wavelength = 8.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.cpu_core, index),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    core?.let { clock(it.currentKhz) } ?: stringResource(R.string.cpu_core_offline),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (core != null && loadPercent != null) {
                    Text(
                        formatUnit(R.string.unit_percent, loadPercent.toFloat(), 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClustersCard(info: DeviceInfo) {
    SectionCard(title = stringResource(R.string.cpu_clusters)) {
        info.clusters.forEach { cluster -> ClusterRow(cluster) }
        InfoRow(stringResource(R.string.cpu_governor), info.governor)
        InfoRow(stringResource(R.string.cpu_abi), info.abis.joinToString(", "))
    }
}

@Composable
private fun ClusterRow(cluster: CpuCluster) {
    val name = cluster.coreName ?: stringResource(R.string.cpu_core_generic)
    val range = when {
        cluster.minKhz != null && cluster.maxKhz != null -> "${clock(cluster.minKhz)} – ${clock(cluster.maxKhz)}"
        cluster.maxKhz != null -> clock(cluster.maxKhz)
        else -> null
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${cluster.cores.size}×",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.cpu_cluster_cores, coreRange(cluster.cores)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            range?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** "0–3" for consecutive cores, "0, 2, 5" otherwise. */
private fun coreRange(cores: List<Int>): String {
    val sorted = cores.sorted()
    return if (sorted.size > 1 && sorted.last() - sorted.first() == sorted.size - 1) {
        "${sorted.first()}–${sorted.last()}"
    } else {
        sorted.joinToString(", ")
    }
}

@Composable
private fun GpuCard(info: DeviceInfo, live: LiveChip) {
    val gpu = info.gpu
    SectionCard(title = stringResource(R.string.gpu_title), subtitle = gpu.renderer) {
        live.gpuLoadPercent?.let { load ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(stringResource(R.string.gpu_load), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(formatUnit(R.string.unit_percent, load.toFloat(), 0), style = MaterialTheme.typography.titleSmall)
                }
                HeatBar(load / 100f, MaterialTheme.colorScheme.tertiary)
            }
        }
        InfoRow(stringResource(R.string.gpu_clock), live.gpuMhz?.let { clock(it * 1000L) })
        InfoRow(stringResource(R.string.gpu_vendor), gpu.vendor)
        InfoRow(stringResource(R.string.gpu_opengl), gpu.glVersion)
        InfoRow(stringResource(R.string.gpu_vulkan), gpu.vulkan)
    }
}

@Composable
private fun MemoryCard(info: DeviceInfo, live: LiveChip) {
    SectionCard(title = stringResource(R.string.memory_title)) {
        live.memory?.let { mem ->
            UsageBlock(stringResource(R.string.memory_ram), mem.totalBytes - mem.availableBytes, mem.totalBytes)
            InfoRow(stringResource(R.string.memory_available), bytes(mem.availableBytes))
            InfoRow(stringResource(R.string.memory_cached), bytes(mem.cachedBytes))
            if (mem.swapTotalBytes > 0) {
                InfoRow(
                    stringResource(R.string.memory_swap),
                    stringResource(R.string.memory_used, bytes(mem.swapTotalBytes - mem.swapFreeBytes), bytes(mem.swapTotalBytes)),
                )
            }
        }
        UsageBlock(stringResource(R.string.storage_title), info.storage.totalBytes - info.storage.freeBytes, info.storage.totalBytes)
    }
}

@Composable
private fun UsageBlock(label: String, used: Long, total: Long) {
    if (total <= 0) return
    val fraction = (used.toFloat() / total).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.9f -> MaterialTheme.colorScheme.error
        fraction > 0.75f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.memory_used, bytes(used), bytes(total)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeatBar(fraction, color)
    }
}

@Composable
private fun DisplayCard(info: DeviceInfo) {
    val d = info.display
    val locale = currentLocale()
    SectionCard(title = stringResource(R.string.display_title)) {
        InfoRow(stringResource(R.string.display_resolution), stringResource(R.string.display_resolution_value, d.widthPx, d.heightPx))
        InfoRow(stringResource(R.string.display_density), stringResource(R.string.unit_dpi, d.densityDpi))
        InfoRow(stringResource(R.string.display_size), d.diagonalInches?.let { stringResource(R.string.unit_inch, formatNumber(it, locale, 1)) })
        val current = stringResource(R.string.unit_hz, formatNumber(d.refreshHz, locale, 0))
        InfoRow(
            stringResource(R.string.display_refresh),
            if (d.maxRefreshHz > d.refreshHz + 1f) {
                stringResource(R.string.display_refresh_value, current, stringResource(R.string.unit_hz, formatNumber(d.maxRefreshHz, locale, 0)))
            } else {
                current
            },
        )
        InfoRow(stringResource(R.string.display_hdr), stringResource(if (d.hdr) R.string.value_yes else R.string.value_no))
    }
}

@Composable
private fun SystemCard(info: DeviceInfo, live: LiveChip, access: Access) {
    val s = info.system
    SectionCard(title = stringResource(R.string.system_title)) {
        InfoRow(stringResource(R.string.system_model), "${s.brand.replaceFirstChar { it.uppercase() }} ${s.model}")
        InfoRow(stringResource(R.string.system_device), "${s.device} · ${s.board}")
        InfoRow(stringResource(R.string.system_android), stringResource(R.string.system_android_value, s.androidVersion, s.sdk))
        InfoRow(stringResource(R.string.system_patch), s.securityPatch)
        InfoRow(stringResource(R.string.system_build), s.buildId)
        InfoRow(stringResource(R.string.system_kernel), s.kernel)
        InfoRow(stringResource(R.string.system_baseband), s.baseband)
        InfoRow(stringResource(R.string.system_bootloader), s.bootloader)
        InfoRow(stringResource(R.string.system_treble), s.treble?.let { stringResource(if (it) R.string.value_yes else R.string.value_no) })
        InfoRow(stringResource(R.string.system_slot), s.abSlot)
        InfoRow(stringResource(R.string.system_vm), s.javaVm)
        InfoRow(
            stringResource(R.string.system_root),
            stringResource(
                when {
                    !access.granted -> R.string.value_no
                    access.mode == PrivilegeMode.Shizuku -> R.string.access_shizuku
                    else -> R.string.access_root
                },
            ),
        )
        if (live.uptimeMillis > 0) InfoRow(stringResource(R.string.system_uptime), uptime(live.uptimeMillis))
    }
}

@Composable
private fun uptime(millis: Long): String {
    val minutes = millis / 60_000
    val days = (minutes / (60 * 24)).toInt()
    val hours = ((minutes / 60) % 24).toInt()
    val mins = (minutes % 60).toInt()
    return if (days > 0) {
        stringResource(R.string.duration_days_hours_minutes, days, hours, mins)
    } else {
        stringResource(R.string.duration_hours_minutes, hours, mins)
    }
}

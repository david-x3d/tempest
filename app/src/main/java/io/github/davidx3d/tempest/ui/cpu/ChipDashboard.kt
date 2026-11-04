package io.github.davidx3d.tempest.ui.cpu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.device.DeviceInfo
import io.github.davidx3d.tempest.data.thermal.CpuCore
import io.github.davidx3d.tempest.ui.bytes
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.clock
import io.github.davidx3d.tempest.ui.formatUnit
import io.github.davidx3d.tempest.ui.plus
import io.github.davidx3d.tempest.ui.components.*
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale

@Composable
internal fun ChipDashboard(state: CpuUiState, padding: PaddingValues) {
    val info = state.info
    var selectedCore by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = padding + PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chip_lab), style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.chip_lab_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (info != null) FilledTonalIconButton(onClick = {
                    val summary = listOf(info.soc.name, info.soc.process, "${info.coreCount} cores", info.gpu.renderer, "${info.system.brand} ${info.system.model}").filterNotNull().joinToString("\n")
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Tempest", summary))
                }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.chip_copy)) }
            }
        }
        if (info == null) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            return@LazyColumn
        }
        item { SiliconHero(info, state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SignalPod(stringResource(R.string.cpu_load), state.live.cpuLoadPercent?.let { formatUnit(R.string.unit_percent, it.toFloat(), 0) }, state.live.cpuLoadPercent?.div(100f), Modifier.weight(1f))
                SignalPod(stringResource(R.string.thermal_soc), state.socCelsius?.let { celsius(it) }, state.socCelsius?.let { LocalHeatScale.current.fraction(it) }, Modifier.weight(1f))
            }
        }
        item { CoreMap(info, state, selectedCore) { selectedCore = it } }
        item {
            Text(stringResource(R.string.chip_resources), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Surface(shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 12.dp, bottomEnd = 36.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.gpu_title), style = MaterialTheme.typography.labelLarge)
                    Text(info.gpu.renderer ?: "—", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.live.gpuMhz?.let { TonalPill(clock(it * 1000L)) }
                        state.live.gpuLoadPercent?.let { TonalPill(formatUnit(R.string.unit_percent, it.toFloat(), 0)) }
                    }
                    state.live.gpuLoadPercent?.let { HeatBar(it / 100f, MaterialTheme.colorScheme.onTertiaryContainer) }
                }
            }
        }
        item {
            val memory = state.live.memory
            Surface(shape = RoundedCornerShape(topStart = 12.dp, topEnd = 36.dp, bottomStart = 36.dp, bottomEnd = 36.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.memory_ram), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(memory?.let { bytes(it.totalBytes) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                    }
                    memory?.takeIf { it.totalBytes > 0 }?.let {
                        HeatBar(1f - it.availableBytes.toFloat() / it.totalBytes, MaterialTheme.colorScheme.onSecondaryContainer)
                        InfoRow(stringResource(R.string.memory_available), bytes(it.availableBytes))
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.chip_explore), style = MaterialTheme.typography.headlineSmall)
            ExpressiveWave(Modifier.padding(top = 8.dp))
        }
        item { ExpandableSpecs(stringResource(R.string.cpu_clusters)) { ClustersCard(info) } }
        item { ExpandableSpecs(stringResource(R.string.gpu_title)) { GpuCard(info, state.live) } }
        item { ExpandableSpecs(stringResource(R.string.memory_title)) { MemoryCard(info, state.live) } }
        item { ExpandableSpecs(stringResource(R.string.display_title)) { DisplayCard(info) } }
        item { ExpandableSpecs(stringResource(R.string.system_title)) { SystemCard(info, state.live, state.access) } }
    }
}

@Composable
private fun SiliconHero(info: DeviceInfo, state: CpuUiState) {
    val trace = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    Surface(shape = RoundedCornerShape(48.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val cy = size.height * 0.3f
                for (i in -3..3) {
                    val y = cy + i * 14.dp.toPx()
                    drawLine(trace, Offset(0f, y), Offset(size.width * 0.28f, y), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(trace, Offset(size.width * 0.72f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SocEmblem(info.soc.vendor, size = 100.dp)
                Text(info.soc.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.soc.process?.let { TonalPill(it, container = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) }
                    TonalPill(stringResource(R.string.chip_core_total, info.coreCount), container = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                }
                info.clusters.mapNotNull { it.maxKhz }.maxOrNull()?.let {
                    Text(stringResource(R.string.cpu_up_to, clock(it)), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SignalPod(label: String, value: String?, fraction: Float?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—", style = MaterialTheme.typography.headlineMedium)
            if (fraction != null) HeatBar(fraction.coerceIn(0f, 1f), MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreMap(info: DeviceInfo, state: CpuUiState, selected: Int, onSelect: (Int) -> Unit) {
    val cores = state.cores.associateBy { it.index }
    val count = maxOf(info.coreCount, (state.cores.maxOfOrNull { it.index } ?: -1) + 1)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chip_core_map), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TonalPill(stringResource(R.string.chip_core_total, count))
        }
        Text(stringResource(R.string.chip_core_map_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(count) { index ->
                val core = cores[index]
                Surface(onClick = { onSelect(index) }, modifier = Modifier.weight(1f), shape = CircleShape,
                    color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleLarge)
                        Text(core?.let { clock(it.currentKhz) } ?: "—", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        val core = cores[selected]
        val cluster = info.clusters.firstOrNull { selected in it.cores }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(cluster?.coreName ?: stringResource(R.string.cpu_core, selected), style = MaterialTheme.typography.titleLarge)
                }
                InfoRow(stringResource(R.string.gpu_clock), core?.let { clock(it.currentKhz) } ?: "—")
                InfoRow(stringResource(R.string.cpu_load), state.live.coreLoadPercent[selected]?.let { formatUnit(R.string.unit_percent, it.toFloat(), 0) } ?: "—")
                val max = core?.maxKhz?.takeIf { it > 0 }
                if (core != null && max != null) HeatBar(core.currentKhz.toFloat() / max, MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.chip_clock_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExpandableSpecs(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = { expanded = !expanded }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = stringResource(if (expanded) R.string.chip_collapse else R.string.chip_expand))
            }
        }
        AnimatedVisibility(expanded) { content() }
    }
}

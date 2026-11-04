package io.github.davidx3d.tempest.ui.components

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.history.Sample
import io.github.davidx3d.tempest.data.history.nearest
import io.github.davidx3d.tempest.data.history.stats
import io.github.davidx3d.tempest.ui.celsius
import io.github.davidx3d.tempest.ui.duration
import io.github.davidx3d.tempest.ui.theme.LocalHeatScale

/** How far back the chart looks. */
enum class HistoryRange(val millis: Long) {
    Minutes30(30 * 60_000L),
    Hours3(3 * 3_600_000L),
    Hours12(12 * 3_600_000L),
    Hours24(24 * 3_600_000L),
}

/**
 * The wave chart with a time range picker, min/average/max, time above the limit and a
 * touch readout. When [csv] is given, a share-as-CSV button is shown.
 */
@Composable
fun HistoryCard(
    samples: List<Sample>,
    threshold: Float,
    modifier: Modifier = Modifier,
    csv: (() -> String)? = null,
    recording: Boolean = false,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val shareTitle = stringResource(R.string.history_export_title)
    var range by rememberSaveable { mutableStateOf(HistoryRange.Minutes30) }
    var scrubTime by remember { mutableStateOf<Long?>(null) }

    val end = samples.lastOrNull()?.timeMillis ?: 0L
    val visible = remember(samples, range) { samples.filter { it.timeMillis >= end - range.millis } }
    val start = visible.firstOrNull()?.timeMillis ?: end
    val stats = remember(visible, threshold) { visible.stats(threshold) }
    val selected = scrubTime?.let { visible.nearest(it) }
    LaunchedEffect(selected?.timeMillis) {
        if (selected != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.history_title),
        subtitle = stringResource(if (recording) R.string.history_note_background else R.string.history_note),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            HistoryRange.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = range == option,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        range = option
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, HistoryRange.entries.size),
                    icon = {},
                ) { Text(option.label(), maxLines = 1) }
            }
        }

        if (visible.size < 2 || stats == null) {
            Text(
                stringResource(R.string.history_collecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            // The readout swaps between the summary and the value under the finger.
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
                if (selected != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            celsius(selected.celsius),
                            style = MaterialTheme.typography.headlineMedium,
                            color = LocalHeatScale.current.color(selected.celsius),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            time(selected.timeMillis),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(stringResource(R.string.history_min), stats.min)
                        Stat(stringResource(R.string.history_avg), stats.average)
                        Stat(stringResource(R.string.history_max), stats.max)
                    }
                }
            }
            WaveChart(
                samples = visible,
                threshold = threshold,
                startMillis = start,
                endMillis = end,
                selected = selected,
                onSelect = { scrubTime = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(start, (start + end) / 2, end).forEach {
                    Text(
                        time(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (stats.aboveMillis >= 60_000L) {
                        stringResource(R.string.history_above, celsius(threshold, 0), duration(stats.aboveMillis))
                    } else {
                        stringResource(R.string.history_threshold, celsius(threshold, 0))
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (csv != null && samples.size >= 2) {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/csv")
                        .putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        .putExtra(Intent.EXTRA_TEXT, csv())
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.history_export), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Float, color: Color = LocalHeatScale.current.color(value)) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(celsius(value), style = MaterialTheme.typography.titleLarge, color = color)
    }
}

/** "14:32" or "2:32 PM", following the phone's 24-hour setting. */
@Composable
private fun time(millis: Long): String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    return format.format(java.util.Date(millis))
}

@Composable
private fun HistoryRange.label(): String = when (this) {
    HistoryRange.Minutes30 -> stringResource(R.string.duration_minutes, 30)
    HistoryRange.Hours3 -> stringResource(R.string.duration_hours, 3)
    HistoryRange.Hours12 -> stringResource(R.string.duration_hours, 12)
    HistoryRange.Hours24 -> stringResource(R.string.duration_hours, 24)
}

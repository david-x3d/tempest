package io.github.davidx3d.tempest.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.davidx3d.tempest.R
import io.github.davidx3d.tempest.data.history.HistoryRepository
import io.github.davidx3d.tempest.data.history.Sample
import io.github.davidx3d.tempest.ui.celsius

@Composable
fun HistoryCard(samples: List<Sample>, threshold: Float, modifier: Modifier = Modifier) {
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.history_title),
        subtitle = pluralStringResource(
            R.plurals.history_window,
            HistoryRepository.WINDOW_MINUTES,
            HistoryRepository.WINDOW_MINUTES,
        ),
    ) {
        if (samples.size < 2) {
            Text(
                stringResource(R.string.history_collecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            WaveChart(
                samples = samples,
                threshold = threshold,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            val values = samples.map { it.celsius }
            Row {
                Text(
                    stringResource(R.string.history_range, celsius(values.min()), celsius(values.max())),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.history_threshold, celsius(threshold, 0)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            stringResource(R.string.history_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

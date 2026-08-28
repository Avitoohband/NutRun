package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun ActiveWorkoutRestTimer(
    endAtMillis: Long,
    nowMillis: Long,
    onAddTime: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember(endAtMillis) { mutableLongStateOf(nowMillis) }
    LaunchedEffect(endAtMillis) {
        while (currentTime < endAtMillis) {
            delay(1_000)
            currentTime = System.currentTimeMillis()
        }
    }
    val remainingSeconds = ((endAtMillis - currentTime + 999) / 1_000).coerceAtLeast(0).toInt()
    if (remainingSeconds <= 0) return
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val stateDescription = "Rest time remaining, $minutes minutes and $seconds seconds"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active-rest-timer-sticky")
            .semantics {
                contentDescription = stateDescription
                this.stateDescription = stateDescription
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text("Rest", fontWeight = FontWeight.Bold)
                Text(
                    "%d:%02d".format(minutes, seconds),
                    modifier = Modifier.testTag("active-rest-timer-time")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onAddTime,
                    modifier = Modifier.testTag("active-rest-timer-add")
                ) {
                    Text("+30s")
                }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("active-rest-timer-skip")
                ) {
                    Text("Skip")
                }
            }
        }
    }
}

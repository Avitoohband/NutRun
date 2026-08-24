package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TutorialWelcomeDialog(
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onSkip,
        modifier = modifier.testTag("tutorial-welcome-dialog"),
        title = { Text("Welcome to NutRun") },
        text = {
            Text(
                "Take a quick five-step tour of Today, Training, Nutrition, Walk, and Progress. You can replay it anytime from Profile → Help."
            )
        },
        confirmButton = {
            Button(
                onClick = onStart,
                modifier = Modifier.testTag("tutorial-welcome-start")
            ) {
                Text("Start tutorial")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("tutorial-welcome-skip")
            ) {
                Text("Skip for now")
            }
        }
    )
}

@Composable
fun TutorialOverviewContent(
    accountId: String,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onBackFromTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable(accountId) { mutableIntStateOf(0) }
    val page = tutorialPages[step]
    val progressLabel = "Step ${step + 1} of ${tutorialPages.size}"

    NutRunScreen(
        title = "Tutorial",
        onBack = onBackFromTutorial,
        modifier = modifier.testTag("tutorial-screen")
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = NutRunSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
        ) {
            Text(
                progressLabel,
                modifier = Modifier
                    .testTag("tutorial-step-indicator")
                    .semantics { contentDescription = progressLabel },
                fontWeight = FontWeight.SemiBold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier.padding(NutRunSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(page.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(page.summary, fontWeight = FontWeight.SemiBold)
                    Text(
                        page.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(NutRunSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.testTag("tutorial-back")
                    ) {
                        Text("Back")
                    }
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("tutorial-skip")
                ) {
                    Text("Skip")
                }
                if (step < tutorialPages.lastIndex) {
                    Button(
                        onClick = { step += 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutorial-next")
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutorial-done")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

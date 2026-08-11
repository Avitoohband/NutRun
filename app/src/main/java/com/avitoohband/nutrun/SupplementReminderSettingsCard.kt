package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SupplementReminderDraft(
    val enabled: Boolean,
    val time: String
)

internal val SupplementReminderDraftsSaver =
    Saver<Map<String, SupplementReminderDraft>, ArrayList<String>>(
        save = { drafts ->
            ArrayList<String>(drafts.size * 3).apply {
                drafts.forEach { (id, draft) ->
                    add(id)
                    add(if (draft.enabled) "1" else "0")
                    add(draft.time)
                }
            }
        },
        restore = { values ->
            values.chunked(3)
                .filter { it.size == 3 }
                .associate { (id, enabled, time) ->
                    id to SupplementReminderDraft(enabled == "1", time)
                }
        }
    )

internal fun reconcileSupplementReminderDrafts(
    drafts: Map<String, SupplementReminderDraft>,
    supplements: List<Supplement>
): Map<String, SupplementReminderDraft> =
    supplements.associate { supplement ->
        supplement.id to (
            drafts[supplement.id]
                ?: SupplementReminderDraft(
                    enabled = supplement.reminderEnabled,
                    time = formatReminderMinute(supplement.reminderMinute)
                )
            )
    }

@Composable
fun SupplementReminderSettingsCard(
    masterEnabled: Boolean,
    onMasterEnabledChange: (Boolean) -> Unit,
    supplements: List<Supplement>,
    drafts: Map<String, SupplementReminderDraft>,
    onDraftsChange: (Map<String, SupplementReminderDraft>) -> Unit,
    onPermissionRequest: () -> Unit,
    onManageSupplements: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enableAll = supplements.any { supplement ->
        !(drafts[supplement.id]?.enabled ?: supplement.reminderEnabled)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Supplement reminders", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { enabled ->
                        onMasterEnabledChange(enabled)
                        if (enabled && !masterEnabled) onPermissionRequest()
                    },
                    modifier = Modifier.testTag("supplement-reminders-master")
                )
            }

            if (supplements.isEmpty()) {
                Text(
                    "No supplements configured",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(
                    onClick = {
                        val updated = drafts.toMutableMap()
                        supplements.forEach { supplement ->
                            val current = updated[supplement.id]
                                ?: SupplementReminderDraft(
                                    enabled = supplement.reminderEnabled,
                                    time = formatReminderMinute(supplement.reminderMinute)
                                )
                            updated[supplement.id] = current.copy(enabled = enableAll)
                        }
                        onDraftsChange(updated)
                        if (enableAll) onPermissionRequest()
                    },
                    modifier = Modifier.testTag("supplement-reminders-toggle-all")
                ) {
                    Text(if (enableAll) "Enable all" else "Disable all")
                }

                supplements.forEachIndexed { index, supplement ->
                    val draft = drafts[supplement.id]
                        ?: SupplementReminderDraft(
                            enabled = supplement.reminderEnabled,
                            time = formatReminderMinute(supplement.reminderMinute)
                        )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(supplement.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    supplement.dose,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = draft.enabled,
                                onCheckedChange = { enabled ->
                                    onDraftsChange(
                                        drafts + (supplement.id to draft.copy(enabled = enabled))
                                    )
                                    if (enabled && !draft.enabled) onPermissionRequest()
                                },
                                modifier = Modifier.testTag(
                                    "supplement-reminder-${supplement.id}-enabled"
                                )
                            )
                        }
                        ReminderTimeInput(
                            value = draft.time,
                            onValueChange = { time ->
                                onDraftsChange(
                                    drafts + (supplement.id to draft.copy(time = time))
                                )
                            },
                            label = "${supplement.name} reminder time",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "supplement-reminder-${supplement.id}-time"
                        )
                    }
                    if (index < supplements.lastIndex) HorizontalDivider()
                }
            }

            TextButton(
                onClick = onManageSupplements,
                modifier = Modifier.testTag("manage-supplements-from-notifications")
            ) {
                Text("Manage supplements")
            }
        }
    }
}

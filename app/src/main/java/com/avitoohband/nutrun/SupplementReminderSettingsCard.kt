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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SupplementReminderDraft(
    val enabled: Boolean,
    val time: String
)

internal data class SupplementReminderDraftState(
    val accountId: String? = null,
    val drafts: Map<String, SupplementReminderDraft> = emptyMap(),
    val dirtyIds: Set<String> = emptySet()
)

internal val SupplementReminderDraftStateSaver =
    Saver<SupplementReminderDraftState, ArrayList<String>>(
        save = { state ->
            ArrayList<String>(1 + state.drafts.size * 4).apply {
                add(state.accountId.orEmpty())
                state.drafts.forEach { (id, draft) ->
                    add(id)
                    add(if (draft.enabled) "1" else "0")
                    add(draft.time)
                    add(if (id in state.dirtyIds) "1" else "0")
                }
            }
        },
        restore = { values ->
            val restored = values.drop(1)
                .chunked(4)
                .filter { it.size == 4 }
            SupplementReminderDraftState(
                accountId = values.firstOrNull()?.takeIf(String::isNotEmpty),
                drafts = restored.associate { (id, enabled, time) ->
                    id to SupplementReminderDraft(enabled == "1", time)
                },
                dirtyIds = restored.filter { it[3] == "1" }.mapTo(mutableSetOf()) { it[0] }
            )
        }
    )

internal fun resolveSupplementReminderDraftOwner(
    state: SupplementReminderDraftState,
    sessionResolved: Boolean,
    accountId: String?
): SupplementReminderDraftState {
    if (!sessionResolved || state.accountId == accountId) return state
    return SupplementReminderDraftState(accountId = accountId)
}

internal fun resolveSupplementReminderDraftState(
    state: SupplementReminderDraftState,
    screenAccountId: String?,
    readyAccountId: String?,
    supplements: List<Supplement>
): SupplementReminderDraftState {
    if (screenAccountId == null || readyAccountId != screenAccountId) {
        return SupplementReminderDraftState(accountId = screenAccountId)
    }
    val sameAccount = state.accountId == screenAccountId
    val retainedDirtyIds = if (sameAccount) {
        state.dirtyIds.intersect(supplements.mapTo(mutableSetOf(), Supplement::id))
    } else {
        emptySet()
    }
    val drafts = supplements.associate { supplement ->
        val persisted = supplement.reminderDraft()
        supplement.id to if (supplement.id in retainedDirtyIds) {
            state.drafts[supplement.id] ?: persisted
        } else {
            persisted
        }
    }
    return SupplementReminderDraftState(
        accountId = screenAccountId,
        drafts = drafts,
        dirtyIds = retainedDirtyIds
    )
}

internal fun applySupplementReminderDraftChanges(
    state: SupplementReminderDraftState,
    updated: Map<String, SupplementReminderDraft>,
    supplements: List<Supplement>
): SupplementReminderDraftState {
    val persisted = supplements.associate { it.id to it.reminderDraft() }
    val dirtyIds = updated.keys.filterTo(mutableSetOf()) { id ->
        updated[id] != persisted[id]
    }
    return state.copy(
        drafts = updated,
        dirtyIds = dirtyIds
    )
}

internal fun reconcileSupplementReminderDrafts(
    drafts: Map<String, SupplementReminderDraft>,
    supplements: List<Supplement>
): Map<String, SupplementReminderDraft> = resolveSupplementReminderDraftState(
    state = SupplementReminderDraftState(
        accountId = "legacy",
        drafts = drafts,
        dirtyIds = drafts.keys
    ),
    screenAccountId = "legacy",
    readyAccountId = "legacy",
    supplements = supplements
).drafts

private fun Supplement.reminderDraft() = SupplementReminderDraft(
    enabled = reminderEnabled,
    time = formatReminderMinute(reminderMinute)
)

@Composable
fun SupplementReminderSettingsCard(
    masterEnabled: Boolean,
    onMasterEnabledChange: (Boolean) -> Unit,
    supplements: List<Supplement>,
    drafts: Map<String, SupplementReminderDraft>,
    onDraftsChange: (Map<String, SupplementReminderDraft>) -> Unit,
    onPermissionRequest: () -> Unit,
    onManageSupplements: () -> Unit,
    loading: Boolean = false,
    collapsedSummary: String? = null,
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
                    enabled = !loading,
                    modifier = Modifier
                        .testTag("supplement-reminders-master")
                        .semantics {
                            role = Role.Switch
                            stateDescription = if (masterEnabled) "On" else "Off"
                            contentDescription = "Supplement reminders master switch"
                        }
                )
            }

            Text(
                "The master switch pauses all supplement reminders without changing individual schedules.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            if (loading) {
                NutRunLoadingState(message = "Loading supplement reminders...")
            } else if (!masterEnabled && collapsedSummary != null) {
                Text(
                    collapsedSummary,
                    modifier = Modifier.testTag("supplement-reminder-summary"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (supplements.isEmpty()) {
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
                                modifier = Modifier
                                    .testTag("supplement-reminder-${supplement.id}-enabled")
                                    .semantics {
                                        role = Role.Switch
                                        stateDescription = if (draft.enabled) "On" else "Off"
                                        contentDescription = "${supplement.name} reminder"
                                    }
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
                enabled = !loading,
                modifier = Modifier.testTag("manage-supplements-from-notifications")
            ) {
                Text("Manage supplements")
            }
        }
    }
}

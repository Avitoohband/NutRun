package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementReminderUiRulesTest {
    @Test
    fun draftReconciliationRetainsExistingIdsAndOnlyAddsOrRemovesMembershipChanges() {
        val retained = Supplement(
            id = "retained",
            name = "Retained",
            dose = "100 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = false,
            reminderMinute = 480
        )
        val added = Supplement(
            id = "added",
            name = "Added",
            dose = "200 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = true,
            reminderMinute = 615
        )
        val drafts = mapOf(
            retained.id to SupplementReminderDraft(enabled = true, time = "09:45"),
            "removed" to SupplementReminderDraft(enabled = true, time = "22:10")
        )

        val reconciled = reconcileSupplementReminderDrafts(
            drafts = drafts,
            supplements = listOf(retained, added)
        )

        assertEquals(setOf("retained", "added"), reconciled.keys)
        assertEquals(SupplementReminderDraft(true, "09:45"), reconciled.getValue("retained"))
        assertEquals(SupplementReminderDraft(true, "10:15"), reconciled.getValue("added"))
    }

    @Test
    fun transientSameIdDefaultsAreNeverSeededAndUneditedValuesYieldToRestoredAccountRows() {
        val transientDefault = Supplement(
            id = "shared-id",
            name = "Transient",
            dose = "1 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = false,
            reminderMinute = 480
        )
        val restored = transientDefault.copy(
            name = "Persisted",
            reminderEnabled = true,
            reminderMinute = 10 * 60 + 15
        )
        val transientState = SupplementReminderDraftState(
            accountId = "account-a",
            drafts = mapOf(
                "shared-id" to SupplementReminderDraft(enabled = false, time = "08:00")
            ),
            dirtyIds = emptySet()
        )

        val loading = resolveSupplementReminderDraftState(
            state = transientState,
            screenAccountId = "account-a",
            readyAccountId = null,
            supplements = listOf(transientDefault)
        )
        val ready = resolveSupplementReminderDraftState(
            state = transientState,
            screenAccountId = "account-a",
            readyAccountId = "account-a",
            supplements = listOf(restored)
        )

        assertEquals("account-a", loading.accountId)
        assertTrue(loading.drafts.isEmpty())
        assertEquals(emptySet<String>(), loading.dirtyIds)
        assertEquals(
            SupplementReminderDraft(enabled = true, time = "10:15"),
            ready.drafts.getValue("shared-id")
        )
        assertTrue(ready.dirtyIds.isEmpty())
    }

    @Test
    fun accountSwitchDiscardsDirtyDraftEvenWhenTheNewAccountUsesTheSameSupplementId() {
        val accountADraft = SupplementReminderDraft(enabled = true, time = "22:10")
        val accountB = Supplement(
            id = "shared-id",
            name = "B persisted",
            dose = "2 mg",
            schedule = SupplementSchedule(RecurrenceType.DAILY),
            reminderEnabled = false,
            reminderMinute = 7 * 60 + 5
        )
        val state = SupplementReminderDraftState(
            accountId = "account-a",
            drafts = mapOf("shared-id" to accountADraft),
            dirtyIds = setOf("shared-id")
        )

        val switching = resolveSupplementReminderDraftState(
            state = state,
            screenAccountId = "account-b",
            readyAccountId = null,
            supplements = listOf(accountB)
        )
        val restored = resolveSupplementReminderDraftState(
            state = switching,
            screenAccountId = "account-b",
            readyAccountId = "account-b",
            supplements = listOf(accountB)
        )

        assertEquals("account-b", switching.accountId)
        assertTrue(switching.drafts.isEmpty())
        assertEquals(
            SupplementReminderDraft(enabled = false, time = "07:05"),
            restored.drafts.getValue("shared-id")
        )
        assertTrue(restored.dirtyIds.isEmpty())
    }

    @Test
    fun restoredAccountDraftSurvivesUnresolvedNullThenAAndClearsOnlyForConfirmedB() {
        val restored = SupplementReminderDraftState(
            accountId = "account-a",
            drafts = mapOf(
                "shared-id" to SupplementReminderDraft(enabled = true, time = "22:10")
            ),
            dirtyIds = setOf("shared-id")
        )

        val unresolved = resolveSupplementReminderDraftOwner(
            state = restored,
            sessionResolved = false,
            accountId = null
        )
        val confirmedA = resolveSupplementReminderDraftOwner(
            state = unresolved,
            sessionResolved = true,
            accountId = "account-a"
        )
        val confirmedB = resolveSupplementReminderDraftOwner(
            state = confirmedA,
            sessionResolved = true,
            accountId = "account-b"
        )

        assertEquals(restored, unresolved)
        assertEquals(restored, confirmedA)
        assertEquals("account-b", confirmedB.accountId)
        assertTrue(confirmedB.drafts.isEmpty())
        assertTrue(confirmedB.dirtyIds.isEmpty())
    }

    @Test
    fun sameReadyAccountRetainsOnlyDirtyIdsDuringMembershipReconciliation() {
        val clean = supplement("clean", enabled = true, minute = 600)
        val dirty = supplement("dirty", enabled = false, minute = 480)
        val state = SupplementReminderDraftState(
            accountId = "account-a",
            drafts = mapOf(
                "clean" to SupplementReminderDraft(false, "06:00"),
                "dirty" to SupplementReminderDraft(true, "09:45"),
                "removed" to SupplementReminderDraft(true, "22:00")
            ),
            dirtyIds = setOf("dirty", "removed")
        )

        val reconciled = resolveSupplementReminderDraftState(
            state,
            screenAccountId = "account-a",
            readyAccountId = "account-a",
            supplements = listOf(clean, dirty)
        )

        assertEquals(SupplementReminderDraft(true, "10:00"), reconciled.drafts.getValue("clean"))
        assertEquals(SupplementReminderDraft(true, "09:45"), reconciled.drafts.getValue("dirty"))
        assertEquals(setOf("dirty"), reconciled.dirtyIds)

        val changed = applySupplementReminderDraftChanges(
            state = reconciled,
            updated = reconciled.drafts + mapOf(
                "clean" to SupplementReminderDraft(false, "06:30"),
                "dirty" to SupplementReminderDraft(false, "08:00")
            ),
            supplements = listOf(clean, dirty)
        )

        assertEquals(setOf("clean"), changed.dirtyIds)
    }

    @Test
    fun notificationPermissionIsRequestedOnlyForDisabledToEnabledTransitions() {
        assertTrue(shouldRequestSupplementReminderPermission(previousEnabled = null, enabled = true))
        assertTrue(shouldRequestSupplementReminderPermission(previousEnabled = false, enabled = true))
        assertFalse(shouldRequestSupplementReminderPermission(previousEnabled = true, enabled = true))
        assertFalse(shouldRequestSupplementReminderPermission(previousEnabled = true, enabled = false))
        assertFalse(shouldRequestSupplementReminderPermission(previousEnabled = false, enabled = false))
    }

    @Test
    fun reminderOnlyEditPreservesEveryNDaysScheduleCompletionAndUnrelatedData() {
        val original = Supplement(
            id = "interval",
            name = "Iron",
            dose = "18 mg",
            schedule = SupplementSchedule(
                type = RecurrenceType.EVERY_N_DAYS,
                startDate = LocalDate.of(2025, 12, 29),
                intervalDays = 4,
                weekdays = setOf(DayOfWeek.SUNDAY)
            ),
            completedOn = LocalDate.of(2026, 8, 10),
            reminderEnabled = false,
            reminderMinute = 480
        )
        val model = modelWith(original)

        model.updateSupplement(
            id = original.id,
            name = original.name,
            dose = original.dose,
            schedule = resolveSupplementEditSchedule(
                existing = original.schedule,
                selectedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                scheduleChanged = false,
                today = LocalDate.of(2026, 8, 11)
            ),
            reminderEnabled = true,
            reminderMinute = 9 * 60 + 35
        )

        assertEquals(
            original.copy(reminderEnabled = true, reminderMinute = 9 * 60 + 35),
            model.supplements.single()
        )
    }

    @Test
    fun reminderOnlyEditPreservesWeekdayScheduleCompletionAndUnrelatedData() {
        val original = Supplement(
            id = "weekdays",
            name = "Zinc",
            dose = "15 mg",
            schedule = SupplementSchedule(
                type = RecurrenceType.WEEKDAYS,
                startDate = LocalDate.of(2026, 1, 5),
                intervalDays = 3,
                weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
            ),
            completedOn = LocalDate.of(2026, 8, 9),
            reminderEnabled = true,
            reminderMinute = 700
        )
        val model = modelWith(original)

        model.updateSupplement(
            id = original.id,
            name = original.name,
            dose = original.dose,
            schedule = resolveSupplementEditSchedule(
                existing = original.schedule,
                selectedDays = original.schedule.weekdays,
                scheduleChanged = false,
                today = LocalDate.of(2026, 8, 11)
            ),
            reminderEnabled = true,
            reminderMinute = 745
        )

        assertEquals(
            original.copy(reminderMinute = 745),
            model.supplements.single()
        )
    }

    private fun supplement(id: String, enabled: Boolean, minute: Int) = Supplement(
        id = id,
        name = id,
        dose = "1 mg",
        schedule = SupplementSchedule(RecurrenceType.DAILY),
        reminderEnabled = enabled,
        reminderMinute = minute
    )

    private fun modelWith(supplement: Supplement): TrainingViewModel =
        TrainingViewModel(null, null).also { model ->
            model.supplements.clear()
            model.supplements += supplement
        }
}

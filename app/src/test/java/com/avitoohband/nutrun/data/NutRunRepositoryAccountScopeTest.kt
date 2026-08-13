package com.avitoohband.nutrun.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NutRunRepositoryAccountScopeTest {
    @Test
    fun missingAndMismatchedSettingsRowsProduceAccountTaggedDefaults() {
        val staleHydration = HydrationPlanEntity(userId = "account-a", goalMl = 9_999)
        val staleTraining = TrainingReminderSettingsEntity(
            userId = "account-a",
            previousDayMinute = 123
        )
        val staleSupplements = SupplementReminderSettingsEntity(
            userId = "account-a",
            enabled = true
        )

        val hydration = hydrationPlanSnapshotForAccount("account-b", staleHydration)
        val training = trainingReminderSettingsSnapshotForAccount("account-b", staleTraining)
        val supplements = supplementReminderSettingsSnapshotForAccount(
            "account-b",
            staleSupplements
        )

        assertEquals("hydration:account-b", hydration.id)
        assertEquals("account-b", hydration.userId)
        assertEquals(2_000, hydration.goalMl)
        assertEquals("training-reminders:account-b", training.id)
        assertEquals("account-b", training.userId)
        assertEquals(20 * 60, training.previousDayMinute)
        assertEquals("supplement-reminders:account-b", supplements.id)
        assertEquals("account-b", supplements.userId)
        assertFalse(supplements.enabled)
    }

    @Test
    fun expectedAccountRemainsTheWriteOwnerWhenSessionSwitchesAfterValidation() = runBlocking {
        var activeAccount = "account-a"
        var persistedAccount: String? = null

        withExpectedRepositoryAccount(
            expectedAccountId = "account-a",
            currentAccountId = {
                val validated = activeAccount
                activeAccount = "account-b"
                validated
            },
            write = { accountId ->
                persistedAccount = accountId
            }
        )

        assertEquals("account-b", activeAccount)
        assertEquals("account-a", persistedAccount)
    }
}

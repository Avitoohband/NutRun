package com.avitoohband.nutrun

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsSaveTest {
    @Test
    fun remainingSettingsDoNotStartUntilIndividualPersistenceIsDurable() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val save = async {
            orchestrateNotificationSettingsSave(
                persistIndividuals = {
                    events += "individual-start"
                    gate.await()
                    events += "individual-durable"
                    NotificationSettingsSaveResult.Success("account-a")
                },
                persistRemainingSettings = {
                    events += "remaining-start"
                    NotificationSettingsSaveResult.Success("account-a")
                }
            )
        }
        yield()

        assertFalse(save.isCompleted)
        assertEquals(listOf("individual-start"), events)
        gate.complete(Unit)

        assertEquals(NotificationSettingsSaveResult.Success("account-a"), save.await())
        assertEquals(
            listOf("individual-start", "individual-durable", "remaining-start"),
            events
        )
    }

    @Test
    fun individualFailureNeverStartsTheOtherSettingsWrites() = runBlocking {
        var remainingStarted = false
        val failure = NotificationSettingsSaveResult.Failed(
            expectedAccountId = "account-a",
            stage = NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
            message = "disk full"
        )

        val result = orchestrateNotificationSettingsSave(
            persistIndividuals = { failure },
            persistRemainingSettings = {
                remainingStarted = true
                NotificationSettingsSaveResult.Success("account-a")
            }
        )

        assertEquals(failure, result)
        assertFalse(remainingStarted)
    }

    @Test
    fun onlySuccessIsEligibleForNavigation() {
        assertTrue(NotificationSettingsSaveResult.Success("account-a").allowsNavigation)
        assertFalse(NotificationSettingsSaveResult.NotReady("account-a").allowsNavigation)
        assertFalse(
            NotificationSettingsSaveResult.AccountChanged(
                expectedAccountId = "account-a",
                actualAccountId = "account-b",
                stage = NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS
            ).allowsNavigation
        )
    }

    @Test
    fun accountChangeMessageReportsCompletedAndUnstartedStages() {
        val result = NotificationSettingsSaveResult.AccountChanged(
            expectedAccountId = "account-a",
            actualAccountId = "account-b",
            stage = NotificationSettingsSaveStage.TRAINING,
            completedStages = setOf(
                NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS,
                NotificationSettingsSaveStage.HYDRATION
            )
        )

        val message = result.errorMessage().orEmpty()

        assertTrue(message.contains("Supplement and hydration settings were saved"))
        assertTrue(message.contains("Training and master settings were not started"))
    }
}

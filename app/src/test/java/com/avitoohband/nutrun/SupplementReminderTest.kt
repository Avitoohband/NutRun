package com.avitoohband.nutrun

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementReminderTest {
    @Test
    fun oldPayloadDefaultsToDisabledAtEight() {
        val payload = encodedPayloadWithoutReminderFields()

        val restored = decodeTrainingState(payload, builtInExerciseCatalog())!!

        assertFalse(restored.supplements.first().reminderEnabled)
        assertEquals(480, restored.supplements.first().reminderMinute)
    }

    @Test
    fun explicitReminderValuesRoundTrip() {
        val item = defaultSupplements().first().copy(
            reminderEnabled = true,
            reminderMinute = 13 * 60 + 15
        )

        val restored = decodeTrainingState(encodedPayload(listOf(item)), builtInExerciseCatalog())!!

        assertTrue(restored.supplements.single().reminderEnabled)
        assertEquals(795, restored.supplements.single().reminderMinute)
    }

    private fun encodedPayloadWithoutReminderFields(): String = JSONObject(
        encodedPayload(defaultSupplements())
    ).apply {
        getJSONArray("supplements").getJSONObject(0).apply {
            remove("reminderEnabled")
            remove("reminderMinute")
        }
    }.toString()

    private fun encodedPayload(supplements: List<Supplement>): String = encodeTrainingState(
        supplements = supplements,
        sessions = emptyList(),
        history = emptyList(),
        selectedSessionId = null,
        activeWorkoutSessionId = null,
        isWorkoutPaused = false,
        completedExerciseIds = emptyMap(),
        suggestionDecision = SuggestionDecision.PENDING,
        suggestedWeightKg = 42.5
    )
}

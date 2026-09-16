package com.avitoohband.nutrun

import org.junit.Assert.assertThrows
import org.junit.Test

class TrainingPayloadLimitsTest {
    @Test
    fun payloadLimitRejectsBeforePersistenceInsteadOfTruncating() {
        assertThrows(IllegalArgumentException::class.java) {
            requireTrainingPayloadWithinLimit("x".repeat(101), maxBytes = 100)
        }
    }

    @Test
    fun payloadLimitCountsUtfEightBytes() {
        requireTrainingPayloadWithinLimit("é".repeat(50), maxBytes = 100)
        assertThrows(IllegalArgumentException::class.java) {
            requireTrainingPayloadWithinLimit("é".repeat(51), maxBytes = 100)
        }
    }
}

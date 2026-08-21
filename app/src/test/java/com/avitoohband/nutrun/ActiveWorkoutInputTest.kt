package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutInputTest {
    @Test
    fun blankOptionalStrengthValuesAreValid() {
        val result = validateWorkoutSetInput(
            input = WorkoutSetInput(weight = "", reps = "", minutes = "", rpe = ""),
            durationTarget = false,
            metric = true
        )

        assertTrue(result.isValid)
        assertEquals(ValidatedWorkoutSetInput(), result.value)
        assertNull(result.weightError)
        assertNull(result.repsError)
        assertNull(result.minutesError)
        assertNull(result.rpeError)
    }

    @Test
    fun strengthBoundariesProduceCanonicalValues() {
        val minimum = validateWorkoutSetInput(
            WorkoutSetInput(weight = "0", reps = "0", minutes = "", rpe = "0"),
            durationTarget = false,
            metric = true
        )
        val maximum = validateWorkoutSetInput(
            WorkoutSetInput(weight = "2000", reps = "1000", minutes = "", rpe = "10"),
            durationTarget = false,
            metric = true
        )

        assertTrue(minimum.isValid)
        assertEquals(0, minimum.value?.reps)
        assertEquals(0.0, minimum.value?.weightKg ?: -1.0, 0.0001)
        assertEquals(0.0, minimum.value?.rpe ?: -1.0, 0.0001)
        assertTrue(maximum.isValid)
        assertEquals(1_000, maximum.value?.reps)
        assertEquals(2_000.0, maximum.value?.weightKg ?: -1.0, 0.0001)
        assertEquals(10.0, maximum.value?.rpe ?: -1.0, 0.0001)
    }

    @Test
    fun invalidStrengthFieldsReturnIndependentErrorsAndNoValue() {
        val result = validateWorkoutSetInput(
            WorkoutSetInput(weight = "-1", reps = "1001", minutes = "", rpe = "11"),
            durationTarget = false,
            metric = true
        )

        assertFalse(result.isValid)
        assertNull(result.value)
        assertEquals("Weight cannot be negative.", result.weightError)
        assertEquals("Reps must be between 0 and 1000.", result.repsError)
        assertEquals("RPE must be between 0 and 10.", result.rpeError)
    }

    @Test
    fun malformedValuesReportTheFieldThatNeedsCorrection() {
        val result = validateWorkoutSetInput(
            WorkoutSetInput(weight = "heavy", reps = "eight", minutes = "", rpe = "hard"),
            durationTarget = false,
            metric = true
        )

        assertEquals("Enter a valid weight.", result.weightError)
        assertEquals("Enter a whole number of reps.", result.repsError)
        assertEquals("Enter a valid RPE.", result.rpeError)
    }

    @Test
    fun durationAcceptsCommaDecimalsAndFullDayBoundary() {
        val comma = validateWorkoutSetInput(
            WorkoutSetInput(weight = "", reps = "", minutes = "1,5", rpe = "8,5"),
            durationTarget = true,
            metric = true
        )
        val maximum = validateWorkoutSetInput(
            WorkoutSetInput(weight = "", reps = "", minutes = "1440", rpe = ""),
            durationTarget = true,
            metric = true
        )

        assertTrue(comma.isValid)
        assertEquals(90, comma.value?.durationSeconds)
        assertEquals(8.5, comma.value?.rpe ?: -1.0, 0.0001)
        assertTrue(maximum.isValid)
        assertEquals(86_400, maximum.value?.durationSeconds)
    }

    @Test
    fun durationOutsideRangeHasAnInlineError() {
        val result = validateWorkoutSetInput(
            WorkoutSetInput(weight = "", reps = "", minutes = "1440.1", rpe = ""),
            durationTarget = true,
            metric = true
        )

        assertFalse(result.isValid)
        assertEquals("Minutes must be between 0 and 1440.", result.minutesError)
    }

    @Test
    fun imperialWeightIsConvertedToCanonicalKilograms() {
        val result = validateWorkoutSetInput(
            WorkoutSetInput(weight = "220.46226218", reps = "8", minutes = "", rpe = "8"),
            durationTarget = false,
            metric = false
        )

        assertTrue(result.isValid)
        assertEquals(100.0, result.value?.weightKg ?: -1.0, 0.0001)
    }
}

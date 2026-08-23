package com.avitoohband.nutrun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatedInputsTest {
    private val weightRule = DecimalRule(
        label = "Weight",
        minInclusive = 20.0,
        maxInclusive = 500.0,
        required = true
    )

    @Test
    fun optionalEmptyDecimalIsAccepted() {
        val optionalRule = weightRule.copy(required = false)
        val result = validateDecimalInput("", optionalRule)
        assertNull(result.value)
        assertNull(result.error)
    }

    @Test
    fun requiredEmptyDecimalIsRejected() {
        val result = validateDecimalInput("", weightRule)
        assertNull(result.value)
        assertEquals("Weight is required.", result.error)
    }

    @Test
    fun parsesCommaDecimal() {
        assertEquals(75.5, validateDecimalInput("75,5", weightRule).value)
    }

    @Test
    fun parsesPeriodDecimal() {
        assertEquals(75.5, validateDecimalInput("75.5", weightRule).value)
    }

    @Test
    fun rejectsAboveMax() {
        assertEquals(
            "Weight must be between 20 and 500.",
            validateDecimalInput("501", weightRule).error
        )
    }

    @Test
    fun rejectsBelowMin() {
        assertEquals(
            "Weight must be between 20 and 500.",
            validateDecimalInput("19", weightRule).error
        )
    }

    @Test
    fun rejectsNegativeValues() {
        assertEquals(
            "Weight must be between 20 and 500.",
            validateDecimalInput("-1", weightRule).error
        )
    }

    @Test
    fun rejectsNonNumericInput() {
        assertEquals("Enter a valid number for Weight.", validateDecimalInput("abc", weightRule).error)
    }

    @Test
    fun rejectsIntegerOnlyDecimals() {
        assertEquals(
            "Weight must be a whole number.",
            validateDecimalInput("75.5", weightRule, integerOnly = true).error
        )
    }

    @Test
    fun acceptsIntegerOnlyWholeNumbers() {
        assertEquals(75.0, validateDecimalInput("75", weightRule, integerOnly = true).value)
    }

    @Test
    fun optionalDateWithinRangeIsAccepted() {
        val range = LocalDate.of(2000, 1, 1)..LocalDate.of(2024, 12, 31)
        assertNull(validateDateInRange(LocalDate.of(2020, 6, 15), range, "Birth date", required = false))
    }

    @Test
    fun requiredMissingDateIsRejected() {
        val range = LocalDate.of(2000, 1, 1)..LocalDate.now()
        assertEquals(
            "Birth date is required.",
            validateDateInRange(null, range, "Birth date", required = true)
        )
    }

    @Test
    fun rejectsFutureBirthDate() {
        val today = LocalDate.of(2026, 8, 23)
        val range = LocalDate.of(1900, 1, 1)..today
        assertEquals(
            "Birth date must be on or before 2026-08-23.",
            validateDateInRange(today.plusDays(1), range, "Birth date", required = true)
        )
    }

    @Test
    fun acceptsLeapDayWithinRange() {
        val range = LocalDate.of(2020, 1, 1)..LocalDate.of(2020, 12, 31)
        assertNull(validateDateInRange(LocalDate.of(2020, 2, 29), range, "Birth date", required = true))
    }

    @Test
    fun rejectsDateOutsideRange() {
        val range = LocalDate.of(2020, 1, 1)..LocalDate.of(2020, 12, 31)
        assertEquals(
            "Workout date must be on or after 2020-01-01.",
            validateDateInRange(LocalDate.of(2019, 12, 31), range, "Workout date", required = true)
        )
    }
}

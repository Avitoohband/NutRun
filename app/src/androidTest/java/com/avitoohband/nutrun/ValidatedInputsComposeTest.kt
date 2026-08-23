package com.avitoohband.nutrun

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ValidatedInputsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val weightRule = DecimalRule(
        label = "Weight",
        minInclusive = 20.0,
        maxInclusive = 500.0,
        required = true
    )

    @Test
    fun numberFieldShowsInlineValidationError() {
        composeRule.setContent {
            var value by mutableStateOf("501")
            MaterialTheme {
                ValidatedNumberField(
                    value = value,
                    onValueChange = { value = it },
                    rule = weightRule,
                    testTag = "weight-input"
                )
            }
        }

        composeRule.onNodeWithText("Weight must be between 20 and 500.").assertIsDisplayed()
    }

    @Test
    fun numberFieldAcceptsCommaDecimalInput() {
        var latestValue = ""
        composeRule.setContent {
            var value by mutableStateOf("")
            MaterialTheme {
                ValidatedNumberField(
                    value = value,
                    onValueChange = {
                        value = it
                        latestValue = it
                    },
                    rule = weightRule,
                    testTag = "weight-input"
                )
            }
        }

        composeRule.onNodeWithTag("weight-input").performTextInput("75,5")
        composeRule.runOnIdle {
            assertEquals("75,5", latestValue)
            assertEquals(75.5, validateDecimalInput(latestValue, weightRule).value)
        }
    }

    @Test
    fun dateFieldOpensPickerAndConfirmsSelection() {
        val allowedRange = LocalDate.of(2020, 1, 1)..LocalDate.of(2026, 8, 23)
        var selected: LocalDate? = null
        composeRule.setContent {
            var value by mutableStateOf<LocalDate?>(null)
            MaterialTheme {
                ValidatedDateField(
                    value = value,
                    onValueChange = {
                        value = it
                        selected = it
                    },
                    label = "Birth date",
                    allowedRange = allowedRange,
                    testTag = "birth-date"
                )
            }
        }

        composeRule.onNodeWithTag("birth-date-calendar").performClick()
        composeRule.onNodeWithTag("birth-date-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("birth-date-confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(LocalDate.of(2020, 1, 1), selected)
        }
    }

    @Test
    fun dateFieldShowsRangeErrorForInvalidSelection() {
        val allowedRange = LocalDate.of(2020, 1, 1)..LocalDate.of(2020, 12, 31)
        composeRule.setContent {
            MaterialTheme {
                ValidatedDateField(
                    value = LocalDate.of(2019, 12, 31),
                    onValueChange = {},
                    label = "Workout date",
                    allowedRange = allowedRange,
                    testTag = "workout-date"
                )
            }
        }

        composeRule.onNodeWithText("Workout date must be on or after 2020-01-01.").assertIsDisplayed()
    }

    @Test
    fun integerOnlyNumberFieldRejectsDecimals() {
        composeRule.setContent {
            var value by mutableStateOf("75.5")
            MaterialTheme {
                ValidatedNumberField(
                    value = value,
                    onValueChange = { value = it },
                    rule = weightRule,
                    integerOnly = true,
                    testTag = "reps-input"
                )
            }
        }

        composeRule.onNodeWithText("Weight must be a whole number.").assertIsDisplayed()
    }

    @Test
    fun optionalEmptyNumberFieldShowsNoError() {
        val optionalRule = weightRule.copy(required = false)
        composeRule.setContent {
            MaterialTheme {
                ValidatedNumberField(
                    value = "",
                    onValueChange = {},
                    rule = optionalRule,
                    testTag = "optional-weight"
                )
            }
        }

        composeRule.runOnIdle {
            assertNull(validateDecimalInput("", optionalRule).error)
        }
        composeRule.onNodeWithText("Weight is required.").assertDoesNotExist()
    }
}

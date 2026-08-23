package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class DecimalRule(
    val label: String,
    val minInclusive: Double,
    val maxInclusive: Double,
    val required: Boolean
)

data class ValidatedDecimal(
    val value: Double? = null,
    val error: String? = null
)

fun validateDecimalInput(
    value: String,
    rule: DecimalRule,
    integerOnly: Boolean = false
): ValidatedDecimal {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return if (rule.required) {
            ValidatedDecimal(error = "${rule.label} is required.")
        } else {
            ValidatedDecimal()
        }
    }

    val normalized = trimmed.replace(',', '.')
    val parsed = normalized.toDoubleOrNull()
    if (parsed == null || parsed.isNaN() || parsed.isInfinite()) {
        return ValidatedDecimal(error = "Enter a valid number for ${rule.label}.")
    }
    if (integerOnly && parsed % 1.0 != 0.0) {
        return ValidatedDecimal(error = "${rule.label} must be a whole number.")
    }
    if (parsed < rule.minInclusive || parsed > rule.maxInclusive) {
        return ValidatedDecimal(
            error = "${rule.label} must be between ${formatDecimal(rule.minInclusive)} and ${formatDecimal(rule.maxInclusive)}."
        )
    }
    return ValidatedDecimal(value = parsed)
}

fun validateDateInRange(
    date: LocalDate?,
    allowedRange: ClosedRange<LocalDate>,
    label: String,
    required: Boolean
): String? {
    if (date == null) {
        return if (required) "$label is required." else null
    }
    if (date !in allowedRange) {
        return when {
            date < allowedRange.start ->
                "$label must be on or after ${allowedRange.start}."
            date > allowedRange.endInclusive ->
                "$label must be on or before ${allowedRange.endInclusive}."
            else -> "$label is invalid."
        }
    }
    return null
}

private fun formatDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

private fun LocalDate.toEpochMillisAtStartOfDay(zoneId: ZoneId = ZoneId.systemDefault()): Long {
    return atStartOfDay(zoneId).toInstant().toEpochMilli()
}

private fun epochMillisToLocalDate(
    millis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate {
    return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidatedDateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    allowedRange: ClosedRange<LocalDate>,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    required: Boolean = true,
    testTag: String = "validated-date-field"
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val displayFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    var showPicker by remember { mutableStateOf(false) }
    val pickerAnchorDate = when {
        value == null -> allowedRange.start
        value < allowedRange.start -> allowedRange.start
        value > allowedRange.endInclusive -> allowedRange.endInclusive
        else -> value!!
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = pickerAnchorDate.toEpochMillisAtStartOfDay(zoneId),
        yearRange = IntRange(
            allowedRange.start.year,
            allowedRange.endInclusive.year
        )
    )
    val rangeError = validateDateInRange(value, allowedRange, label, required)
    val displayedError = error ?: rangeError
    val displayValue = value?.format(displayFormatter) ?: ""

    OutlinedTextField(
        value = displayValue,
        onValueChange = {},
        modifier = modifier
            .testTag(testTag)
            .semantics { contentDescription = label },
        enabled = enabled,
        readOnly = true,
        label = { Text(label) },
        isError = displayedError != null,
        supportingText = displayedError?.let { message ->
            { Text(message) }
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    datePickerState.selectedDateMillis = pickerAnchorDate.toEpochMillisAtStartOfDay(zoneId)
                    showPicker = true
                },
                modifier = Modifier.testTag("$testTag-calendar"),
                enabled = enabled
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Choose $label")
            }
        }
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            onValueChange(epochMillisToLocalDate(selectedMillis, zoneId))
                        }
                        showPicker = false
                    },
                    modifier = Modifier.testTag("$testTag-confirm")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                modifier = Modifier.testTag("$testTag-picker")
            )
        }
    }
}

@Composable
fun ValidatedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    rule: DecimalRule,
    modifier: Modifier = Modifier,
    integerOnly: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    testTag: String = "validated-number-field"
) {
    val validation = validateDecimalInput(value, rule, integerOnly)
    val displayedError = error ?: validation.error
    val keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal

    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            val filtered = if (integerOnly) {
                entered.filter { it.isDigit() }
            } else {
                entered.filter { it.isDigit() || it == '.' || it == ',' }
            }
            onValueChange(filtered)
        },
        modifier = modifier
            .testTag(testTag)
            .semantics { contentDescription = rule.label },
        enabled = enabled,
        label = { Text(rule.label) },
        singleLine = true,
        isError = displayedError != null,
        supportingText = displayedError?.let { message ->
            { Text(message) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

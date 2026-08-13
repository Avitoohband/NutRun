package com.avitoohband.nutrun

import android.text.format.DateFormat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType

private val REMINDER_TIME_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

fun parseReminderMinute(value: String): Int? {
    if (!REMINDER_TIME_PATTERN.matches(value)) return null
    val hour = value.substring(0, 2).toInt()
    val minute = value.substring(3, 5).toInt()
    return hour * 60 + minute
}

fun formatReminderMinute(minute: Int): String {
    require(minute in 0 until 24 * 60) { "Reminder minute must be within a day." }
    return "%02d:%02d".format(minute / 60, minute % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String = "reminder-time-input",
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val initialMinute = parseReminderMinute(value) ?: 8 * 60
    val pickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    var showPicker by remember { mutableStateOf(false) }
    val isValid = parseReminderMinute(value) != null

    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            onValueChange(
                entered
                    .filter { character -> character.isDigit() || character == ':' }
                    .take(5)
            )
        },
        modifier = modifier.testTag(testTag),
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("08:00") },
        singleLine = true,
        isError = !isValid,
        supportingText = if (!isValid) {
            { Text("Use a valid 24-hour time (HH:mm).") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        trailingIcon = {
            IconButton(
                onClick = {
                    val selectedMinute = parseReminderMinute(value) ?: 8 * 60
                    pickerState.hour = selectedMinute / 60
                    pickerState.minute = selectedMinute % 60
                    showPicker = true
                },
                modifier = Modifier.testTag("$testTag-clock"),
                enabled = enabled
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = "Choose $label")
            }
        }
    )

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = pickerState,
                        modifier = Modifier.testTag("$testTag-picker")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(
                            formatReminderMinute(pickerState.hour * 60 + pickerState.minute)
                        )
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
        )
    }
}

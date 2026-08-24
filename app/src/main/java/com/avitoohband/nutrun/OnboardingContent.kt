package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.avitoohband.nutrun.domain.ActivityLevel
import com.avitoohband.nutrun.domain.BiologicalSex
import com.avitoohband.nutrun.domain.HealthGoal
import com.avitoohband.nutrun.domain.UnitSystem
import com.avitoohband.nutrun.domain.UserProfile
import com.avitoohband.nutrun.domain.calculateHealthEstimate
import java.time.LocalDate

@Composable
fun OnboardingOverviewContent(
    accountId: String,
    email: String,
    onSave: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable(accountId) { mutableIntStateOf(0) }
    var birthDateEpoch by rememberSaveable(accountId) {
        mutableLongStateOf(LocalDate.of(1995, 1, 1).toEpochDay())
    }
    var sex by rememberSaveable(accountId) { mutableStateOf(BiologicalSex.MALE) }
    var height by rememberSaveable(accountId) { mutableStateOf("175") }
    var weight by rememberSaveable(accountId) { mutableStateOf("75") }
    var activity by rememberSaveable(accountId) { mutableStateOf(ActivityLevel.MODERATE) }
    var goal by rememberSaveable(accountId) { mutableStateOf(HealthGoal.MAINTAIN) }
    var units by rememberSaveable(accountId) { mutableStateOf(UnitSystem.METRIC) }
    var target by rememberSaveable(accountId) { mutableStateOf("") }
    var error by rememberSaveable(accountId) { mutableStateOf<String?>(null) }

    val birthDate = LocalDate.ofEpochDay(birthDateEpoch)
    val birthDateRange = FormValidationRules.birthDateRange()
    val metric = units == UnitSystem.METRIC
    val heightValidation = validateDecimalInput(height, FormValidationRules.heightRule(metric))
    val weightValidation = validateDecimalInput(weight, FormValidationRules.weightRule(metric))
    val targetValidation = validateDecimalInput(
        target,
        FormValidationRules.optionalCalorieTargetRule,
        integerOnly = true
    )
    val heightCm = heightValidation.value?.let { convertHeightInputToCm(it, metric) }
    val weightKg = weightValidation.value?.let { convertWeightInputToKg(it, metric) }
    val estimate = if (heightCm != null && weightKg != null) {
        runCatching {
            calculateHealthEstimate(birthDate, sex, heightCm, weightKg, activity, goal)
        }.getOrNull()
    } else {
        null
    }

    NutRunScreen(
        title = "Set up profile",
        modifier = modifier.testTag("onboarding-screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = NutRunSpacing.lg),
            contentPadding = PaddingValues(vertical = NutRunSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
        ) {
            item {
                Text(
                    "Step ${step + 1} of 3",
                    modifier = Modifier.testTag("onboarding-step-indicator"),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when (step) {
                        0 -> "Basics"
                        1 -> "Measurements"
                        else -> "Goal"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("These details calculate your starting BMI and energy estimates.")
            }
            when (step) {
                0 -> {
                    item {
                        ValidatedDateField(
                            value = birthDate,
                            onValueChange = { selected -> selected?.let { birthDateEpoch = it.toEpochDay() } },
                            label = "Birth date",
                            allowedRange = birthDateRange,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "onboarding-birth-date"
                        )
                    }
                    item {
                        NutRunChoiceRow(
                            "Biological sex",
                            BiologicalSex.entries,
                            sex,
                            { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                            { sex = it }
                        )
                    }
                }
                1 -> {
                    item {
                        NutRunChoiceRow(
                            "Units",
                            UnitSystem.entries,
                            units,
                            { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                            { selected ->
                                if (selected != units) {
                                    val currentHeightCm = heightValidation.value?.let {
                                        convertHeightInputToCm(it, metric)
                                    }
                                    val currentWeightKg = weightValidation.value?.let {
                                        convertWeightInputToKg(it, metric)
                                    }
                                    units = selected
                                    currentHeightCm?.let {
                                        height = formatHeightForUnits(it, selected == UnitSystem.METRIC)
                                    }
                                    currentWeightKg?.let {
                                        weight = formatWeightForUnits(it, selected == UnitSystem.METRIC)
                                    }
                                }
                            }
                        )
                    }
                    item {
                        ValidatedNumberField(
                            value = height,
                            onValueChange = { height = it },
                            rule = FormValidationRules.heightRule(metric),
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "onboarding-height"
                        )
                    }
                    item {
                        ValidatedNumberField(
                            value = weight,
                            onValueChange = { weight = it },
                            rule = FormValidationRules.weightRule(metric),
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "onboarding-weight"
                        )
                    }
                }
                else -> {
                    item {
                        NutRunChoiceRow(
                            "Activity",
                            ActivityLevel.entries,
                            activity,
                            { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) },
                            { activity = it }
                        )
                    }
                    item {
                        NutRunChoiceRow(
                            "Goal",
                            HealthGoal.entries,
                            goal,
                            { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                            { goal = it }
                        )
                    }
                    item {
                        ValidatedNumberField(
                            value = target,
                            onValueChange = { target = it },
                            rule = FormValidationRules.optionalCalorieTargetRule,
                            modifier = Modifier.fillMaxWidth(),
                            integerOnly = true,
                            testTag = "onboarding-calorie-target"
                        )
                    }
                    estimate?.let { health ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(NutRunSpacing.md)) {
                                    Text("Health estimate preview", fontWeight = FontWeight.SemiBold)
                                    Text("BMI ${"%.1f".format(health.bmi)}")
                                    Text("Suggested target ${health.calorieTarget} kcal")
                                    TextButton(
                                        onClick = { target = health.calorieTarget.toString() },
                                        modifier = Modifier.testTag("onboarding-use-recommended-target")
                                    ) {
                                        Text("Use recommended target")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            error?.let { message ->
                item {
                    NutRunInlineMessage(message, NutRunMessageKind.ERROR, testTag = "onboarding-error")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step -= 1 },
                            modifier = Modifier.testTag("onboarding-back")
                        ) {
                            Text("Back")
                        }
                    }
                    if (step < 2) {
                        Button(
                            onClick = {
                                error = when (step) {
                                    0 -> validateDateInRange(birthDate, birthDateRange, "Birth date", required = true)
                                    else -> heightValidation.error ?: weightValidation.error
                                }
                                if (error == null) step += 1
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding-next")
                        ) {
                            Text("Next")
                        }
                    } else {
                        Button(
                            onClick = {
                                val dateError = validateDateInRange(birthDate, birthDateRange, "Birth date", required = true)
                                val firstError = dateError
                                    ?: heightValidation.error
                                    ?: weightValidation.error
                                    ?: targetValidation.error
                                if (firstError != null) {
                                    error = firstError
                                    return@Button
                                }
                                error = null
                                val resolvedHeightCm = convertHeightInputToCm(heightValidation.value!!, metric)
                                val resolvedWeightKg = convertWeightInputToKg(weightValidation.value!!, metric)
                                val resolvedEstimate = calculateHealthEstimate(
                                    birthDate,
                                    sex,
                                    resolvedHeightCm,
                                    resolvedWeightKg,
                                    activity,
                                    goal
                                )
                                onSave(
                                    UserProfile(
                                        email,
                                        birthDate,
                                        sex,
                                        resolvedHeightCm,
                                        resolvedWeightKg,
                                        activity,
                                        goal,
                                        units,
                                        targetValidation.value?.toInt() ?: resolvedEstimate.calorieTarget
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("finish-onboarding")
                        ) {
                            Text("Finish setup")
                        }
                    }
                }
            }
            item {
                Text(
                    "Estimates are general guidance and are not medical advice.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

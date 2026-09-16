package com.avitoohband.nutrun

import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

fun encodeGtgState(state: GtgState): JSONObject = JSONObject()
    .put("gtgPlans", JSONArray().apply { state.plans.distinctBy(GtgPlan::id).forEach { put(it.toJson()) } })
    .put("gtgLogs", JSONArray().apply { state.logs.distinctBy(GtgLog::id).forEach { put(it.toJson()) } })

fun decodeGtgState(root: JSONObject): GtgState {
    val plans = root.optJSONArray("gtgPlans")
        ?.jsonObjects()
        ?.mapNotNull { runCatching { it.toGtgPlan() }.getOrNull() }
        ?.distinctBy(GtgPlan::id)
        .orEmpty()
    val planIds = plans.map(GtgPlan::id).toSet()
    val logs = root.optJSONArray("gtgLogs")
        ?.jsonObjects()
        ?.mapNotNull { runCatching { it.toGtgLog() }.getOrNull() }
        ?.filter { it.planId in planIds }
        ?.distinctBy(GtgLog::id)
        .orEmpty()
    return GtgState(plans, logs)
}

private fun GtgPlan.toJson() = JSONObject()
    .put("id", id)
    .put("exerciseId", exerciseId)
    .put("exerciseName", exerciseName)
    .put("measure", measure.name)
    .put("targetPerSet", targetPerSet)
    .put("dailySetGoal", dailySetGoal)
    .put("weekdays", JSONArray(weekdays.map(DayOfWeek::name)))
    .putNullableValue("weightKg", weightKg)
    .put("archived", archived)

private fun GtgLog.toJson() = JSONObject()
    .put("id", id)
    .put("planId", planId)
    .put("exerciseId", exerciseId)
    .put("exerciseName", exerciseName)
    .put("measure", measure.name)
    .putNullableValue("reps", reps)
    .putNullableValue("durationSeconds", durationSeconds)
    .putNullableValue("weightKg", weightKg)
    .putNullableValue("rir", rir)
    .put("notes", notes)
    .put("performedAtMillis", performedAtMillis)
    .put("zoneId", zoneId)
    .put("performedOn", performedOn.toString())
    .put("dailySetGoalSnapshot", dailySetGoalSnapshot)

private fun JSONObject.toGtgPlan() = GtgPlan(
    id = getString("id"),
    exerciseId = getString("exerciseId"),
    exerciseName = getString("exerciseName"),
    measure = GtgMeasure.valueOf(getString("measure")),
    targetPerSet = getInt("targetPerSet"),
    dailySetGoal = getInt("dailySetGoal"),
    weekdays = getJSONArray("weekdays").strings().map(DayOfWeek::valueOf).toSet(),
    weightKg = nullableDouble("weightKg"),
    archived = optBoolean("archived", false)
)

private fun JSONObject.toGtgLog() = GtgLog(
    id = getString("id"),
    planId = getString("planId"),
    exerciseId = getString("exerciseId"),
    exerciseName = getString("exerciseName"),
    measure = GtgMeasure.valueOf(getString("measure")),
    reps = nullableInt("reps"),
    durationSeconds = nullableInt("durationSeconds"),
    weightKg = nullableDouble("weightKg"),
    rir = nullableInt("rir"),
    notes = optString("notes", ""),
    performedAtMillis = getLong("performedAtMillis"),
    zoneId = getString("zoneId"),
    performedOn = LocalDate.parse(getString("performedOn")),
    dailySetGoalSnapshot = getInt("dailySetGoalSnapshot")
)

private fun JSONObject.putNullableValue(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.nullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

private fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONArray.jsonObjects(): List<JSONObject> =
    (0 until length()).map(::getJSONObject)

private fun JSONArray.strings(): List<String> =
    (0 until length()).map(::getString)

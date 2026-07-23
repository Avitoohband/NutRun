package com.avitoohband.nutrun.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.avitoohband.nutrun.BuildConfig
import com.avitoohband.nutrun.auth.FirebaseTokenProvider
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.HydrationPlanEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import com.avitoohband.nutrun.data.UserProfileEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WalkPointEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.data.WeightEntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        private const val WORK_NAME = "nutrun-cloud-sync"
    }
}

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.BACKEND_BASE_URL.isBlank()) return Result.success()
        val tokenProvider = FirebaseTokenProvider(applicationContext)
        val userId = tokenProvider.currentUserId() ?: return Result.success()
        val token = tokenProvider.idToken() ?: return Result.retry()
        val dao = NutRunDatabase.getInstance(applicationContext).dao()
        val operations = dao.pendingSync(userId)
        if (operations.isEmpty()) return Result.success()

        for (operation in operations) {
            val response = runCatching {
                sendOperation(
                    token = token,
                    idempotencyKey = operation.id,
                    body = JSONObject()
                        .put("entityType", operation.entityType)
                        .put("entityId", operation.entityId)
                        .put("operation", operation.operation)
                        .put("clientUpdatedAtMillis", operation.createdAtMillis)
                        .put(
                            "payload",
                            operation.payloadJson?.let(::JSONObject) ?: JSONObject.NULL
                        )
                )
            }.getOrNull()
            when (response) {
                in 200..299 -> dao.completeSync(operation)
                401 -> return Result.retry()
                in 400..499 -> {
                    dao.recordSyncAttempt(operation.id)
                    return Result.failure()
                }
                else -> {
                    dao.recordSyncAttempt(operation.id)
                    return Result.retry()
                }
            }
        }
        if (dao.pendingSync(userId, 1).isNotEmpty()) return Result.retry()
        return runCatching {
            pullSnapshot(token, userId)
        }.fold(
            onSuccess = { snapshot ->
                dao.applyRemoteSnapshot(
                    userId = userId,
                    profile = snapshot.profile,
                    weights = snapshot.weights,
                    foods = snapshot.foods,
                    water = snapshot.water,
                    hydrationPlan = snapshot.hydrationPlan,
                    walks = snapshot.walks,
                    walkPoints = snapshot.walkPoints,
                    trainingState = snapshot.trainingState
                )
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }

    private suspend fun sendOperation(
        token: String,
        idempotencyKey: String,
        body: JSONObject
    ): Int = withContext(Dispatchers.IO) {
        val connection = URI("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/v1/sync")
            .toURL()
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 8_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Idempotency-Key", idempotencyKey)
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun pullSnapshot(token: String, userId: String): RemoteSnapshot =
        withContext(Dispatchers.IO) {
            val connection = URI("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/v1/sync/snapshot")
                .toURL()
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode !in 200..299) {
                    error("Snapshot download returned ${connection.responseCode}.")
                }
                val snapshot = parseSnapshot(
                    userId,
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                )
                snapshot.copy(
                    walkPoints = snapshot.walks.take(20).flatMap { walk ->
                        pullRoute(token, userId, walk.id)
                    }
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun parseSnapshot(userId: String, root: JSONObject): RemoteSnapshot {
        val profile = root.optJSONObject("profile")?.let { value ->
            UserProfileEntity(
                id = "profile:$userId",
                userId = userId,
                email = value.getString("email"),
                birthDate = value.getString("birthDate"),
                biologicalSex = value.getString("biologicalSex"),
                heightCm = value.getDouble("heightCm"),
                weightKg = value.getDouble("weightKg"),
                activityLevel = value.getString("activityLevel"),
                goal = value.getString("goal"),
                unitSystem = value.getString("unitSystem"),
                calorieTarget = value.getInt("calorieTarget"),
                updatedAtMillis = value.optLong("updatedAtMillis")
            )
        }
        val weights = root.array("weightEntries").objects().map { value ->
            WeightEntryEntity(
                id = value.getString("id"),
                userId = userId,
                weightKg = value.getDouble("weightKg"),
                recordedAtMillis = value.getLong("recordedAtMillis"),
                pendingSync = false
            )
        }
        val foods = root.array("foodLogs").objects().map { value ->
            FoodLogEntity(
                id = value.getString("id"),
                userId = userId,
                localDate = value.getString("date"),
                mealType = value.getString("mealType"),
                catalogId = value.nullableString("catalogId"),
                name = value.getString("name"),
                brand = value.nullableString("brand"),
                servingGrams = value.getDouble("servingGrams"),
                calories = value.getInt("calories"),
                proteinGrams = value.optDouble("proteinGrams"),
                carbohydrateGrams = value.optDouble("carbohydrateGrams"),
                fatGrams = value.optDouble("fatGrams"),
                updatedAtMillis = value.optLong("updatedAtMillis"),
                pendingSync = false
            )
        }
        val water = root.array("waterLogs").objects().map { value ->
            WaterLogEntity(
                id = value.getString("id"),
                userId = userId,
                localDate = value.getString("date"),
                amountMl = value.getInt("amountMl"),
                loggedAtMillis = value.getLong("loggedAtMillis"),
                pendingSync = false
            )
        }
        val hydration = root.optJSONObject("hydrationPlan")?.let { value ->
            HydrationPlanEntity(
                id = "hydration:$userId",
                userId = userId,
                goalMl = value.optInt("goalMl", 2_000),
                servingMl = value.optInt("servingMl", 250),
                wakingStartMinute = value.optInt("wakingStartMinute", 8 * 60),
                wakingEndMinute = value.optInt("wakingEndMinute", 22 * 60),
                intervalMinutes = value.optInt("intervalMinutes", 120),
                remindersEnabled = value.optBoolean("remindersEnabled", true)
            )
        }
        val walks = root.array("walks").objects().map { value ->
            WalkSessionEntity(
                id = value.getString("id"),
                userId = userId,
                state = "FINISHED",
                startedAtMillis = value.getLong("startedAtMillis"),
                endedAtMillis = value.optNullableLong("endedAtMillis"),
                accumulatedDurationMillis = value.optLong("durationMillis"),
                resumedAtMillis = null,
                distanceMeters = value.optDouble("distanceMeters"),
                stepBaseline = null,
                stepOffset = value.optNullableLong("steps") ?: 0,
                steps = value.optNullableLong("steps"),
                pendingSync = false
            )
        }
        val training = root.array("trainingState").objects().maxByOrNull {
            it.optLong("updatedAtMillis")
        }?.let { value ->
            value.remove("id")
            value.remove("updatedAt")
            TrainingStateEntity(
                userId = userId,
                payloadJson = value.toString(),
                updatedAtMillis = value.optLong("updatedAtMillis"),
                pendingSync = false
            )
        }
        return RemoteSnapshot(
            profile,
            weights,
            foods,
            water,
            hydration,
            walks,
            emptyList(),
            training
        )
    }

    private fun pullRoute(token: String, userId: String, walkId: String): List<WalkPointEntity> {
        val connection = URI(
            "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/v1/walks/$walkId/route"
        ).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return emptyList()
            if (connection.responseCode !in 200..299) {
                error("Route download returned ${connection.responseCode}.")
            }
            val points = JSONObject(
                connection.inputStream.bufferedReader().use { it.readText() }
            ).array("points")
            points.objects().mapIndexed { index, value ->
                WalkPointEntity(
                    id = "remote:$walkId:$index",
                    userId = userId,
                    sessionId = walkId,
                    latitude = value.getDouble("latitude"),
                    longitude = value.getDouble("longitude"),
                    accuracyMeters = value.optDouble("accuracyMeters").toFloat(),
                    recordedAtMillis = value.getLong("recordedAtMillis")
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}

private data class RemoteSnapshot(
    val profile: UserProfileEntity?,
    val weights: List<WeightEntryEntity>,
    val foods: List<FoodLogEntity>,
    val water: List<WaterLogEntity>,
    val hydrationPlan: HydrationPlanEntity?,
    val walks: List<WalkSessionEntity>,
    val walkPoints: List<WalkPointEntity>,
    val trainingState: TrainingStateEntity?
)

private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).map(::getJSONObject)

private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else getLong(name)

package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.WalkSessionEntity
import com.avitoohband.nutrun.data.WaterLogEntity
import com.avitoohband.nutrun.data.WeightEntryEntity
import com.avitoohband.nutrun.domain.WalkState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun weightSeriesUsesLatestEntryPerDayAndFiltersRange() {
        val entries = listOf(
            weightEntry("w1", 80.0, utcMillis(2026, 8, 20, 8)),
            weightEntry("w2", 79.5, utcMillis(2026, 8, 20, 20)),
            weightEntry("w3", 78.0, utcMillis(2026, 8, 22, 9)),
            weightEntry("w4", 90.0, utcMillis(2026, 8, 1, 9))
        )
        val series = weightSeries(entries, ProgressRange.DAYS_7, zone, today)

        assertEquals(2, series.points.size)
        assertEquals(LocalDate.of(2026, 8, 20), series.points[0].date)
        assertEquals(79.5, series.points[0].value, 0.001)
        assertEquals(LocalDate.of(2026, 8, 22), series.points[1].date)
        assertEquals(78.0, series.points[1].value, 0.001)
    }

    @Test
    fun weightSeriesExcludesFutureDates() {
        val entries = listOf(weightEntry("w1", 80.0, utcMillis(2026, 8, 25, 9)))
        val series = weightSeries(entries, ProgressRange.ALL, zone, today)
        assertTrue(series.points.isEmpty())
    }

    @Test
    fun workoutFrequencyCountsSessionsPerDay() {
        val workouts = listOf(
            workout("a", today, volumeKg = 100.0),
            workout("b", today, volumeKg = 50.0),
            workout("c", today.minusDays(1), volumeKg = 75.0)
        )
        val series = workoutFrequencySeries(workouts, ProgressRange.DAYS_7, today)

        assertEquals(2, series.points.size)
        assertEquals(2.0, series.points.last().value, 0.001)
    }

    @Test
    fun trainingVolumeAggregatesCompletedSetVolumePerDay() {
        val workouts = listOf(
            workout(
                "a",
                today,
                sets = listOf(
                    completedSet(weightKg = 50.0, reps = 10),
                    completedSet(weightKg = 40.0, reps = 8)
                )
            )
        )
        val series = trainingVolumeSeries(workouts, ProgressRange.DAYS_7, today)

        assertEquals(820.0, series.points.single().value, 0.001)
    }

    @Test
    fun trainingVolumeIgnoresIncompleteSets() {
        val workouts = listOf(
            workout(
                "a",
                today,
                sets = listOf(
                    completedSet(weightKg = 50.0, reps = 10),
                    WorkoutSetLog(
                        id = "s2",
                        targetId = "t1",
                        exerciseId = "ex1",
                        setNumber = 2,
                        reps = 10,
                        weightKg = 50.0,
                        completed = false
                    )
                )
            )
        )
        val series = trainingVolumeSeries(workouts, ProgressRange.DAYS_7, today)
        assertEquals(500.0, series.points.single().value, 0.001)
    }

    @Test
    fun walkingDistanceSeriesTotalsFinishedWalksPerDay() {
        val walks = listOf(
            walk("walk1", dateMillis(2026, 8, 24, 10), 1_500.0),
            walk("walk2", dateMillis(2026, 8, 24, 18), 500.0),
            walk("walk3", dateMillis(2026, 8, 23, 9), 2_000.0, WalkState.ACTIVE.name)
        )
        val series = walkingDistanceSeries(walks, ProgressRange.DAYS_7, zone, today)

        assertEquals(1, series.points.size)
        assertEquals(2_000.0, series.points.single().value, 0.001)
    }

    @Test
    fun calorieAdherenceClampsAboveGoalToTwoHundredPercent() {
        val logs = listOf(
            foodLog(today.toString(), calories = 4_000)
        )
        val series = calorieAdherenceSeries(logs, calorieTarget = 2_000, ProgressRange.DAYS_7, today)
        assertEquals(ADHERENCE_DISPLAY_MAX_PERCENT, series.points.single().value, 0.001)
    }

    @Test
    fun hydrationAdherenceReturnsZeroWhenGoalIsZero() {
        val logs = listOf(waterLog(today.toString(), amountMl = 500))
        val series = hydrationAdherenceSeries(logs, goalMl = 0, ProgressRange.DAYS_7, today)
        assertEquals(0.0, series.points.single().value, 0.001)
    }

    @Test
    fun exerciseProgressSeriesUsesOnlyCompletedSetsForExercise() {
        val workouts = listOf(
            workout(
                "a",
                today,
                sets = listOf(
                    completedSet(exerciseId = "bench", weightKg = 60.0, reps = 8),
                    completedSet(exerciseId = "squat", weightKg = 100.0, reps = 5)
                )
            ),
            workout(
                "b",
                today.minusDays(2),
                sets = listOf(
                    completedSet(exerciseId = "bench", weightKg = 55.0, reps = 10)
                )
            )
        )
        val series = exerciseProgressSeries(
            workouts,
            exerciseId = "bench",
            metric = ExerciseProgressMetric.MAX_WEIGHT,
            range = ProgressRange.DAYS_30,
            today = today
        )

        assertEquals(2, series.points.size)
        assertEquals(60.0, series.points.last().value, 0.001)
    }

    @Test
    fun exerciseEstimatedOneRepMaxUsesCompletedWeightedSets() {
        val workouts = listOf(
            workout(
                "a",
                today,
                sets = listOf(completedSet(weightKg = 100.0, reps = 5))
            )
        )
        val series = exerciseProgressSeries(
            workouts,
            exerciseId = "ex1",
            metric = ExerciseProgressMetric.ESTIMATED_ONE_REP_MAX,
            range = ProgressRange.DAYS_7,
            today = today
        )
        assertEquals(116.666, series.points.single().value, 0.01)
    }

    @Test
    fun progressRangeStartBoundaries() {
        assertEquals(LocalDate.of(2026, 8, 18), progressRangeStart(ProgressRange.DAYS_7, today))
        assertEquals(LocalDate.of(2026, 7, 26), progressRangeStart(ProgressRange.DAYS_30, today))
        assertEquals(null, progressRangeStart(ProgressRange.ALL, today))
    }

    @Test
    fun emptyInputsReturnEmptySeriesWithoutThrowing() {
        assertTrue(weightSeries(emptyList(), ProgressRange.DAYS_7, zone, today).points.isEmpty())
        assertTrue(workoutFrequencySeries(emptyList(), ProgressRange.DAYS_7, today).points.isEmpty())
        assertTrue(
            exerciseProgressSeries(
                emptyList(),
                "ex1",
                ExerciseProgressMetric.VOLUME,
                ProgressRange.DAYS_7,
                today
            ).points.isEmpty()
        )
    }

  private fun weightEntry(id: String, weightKg: Double, recordedAtMillis: Long) =
        WeightEntryEntity(
            id = id,
            userId = "user",
            weightKg = weightKg,
            recordedAtMillis = recordedAtMillis
        )

    private fun foodLog(localDate: String, calories: Int) = FoodLogEntity(
        id = "food-$localDate",
        userId = "user",
        localDate = localDate,
        mealType = "LUNCH",
        catalogId = null,
        name = "Meal",
        brand = null,
        servingGrams = 100.0,
        calories = calories,
        proteinGrams = 0.0,
        carbohydrateGrams = 0.0,
        fatGrams = 0.0,
        updatedAtMillis = 0L
    )

    private fun waterLog(localDate: String, amountMl: Int) = WaterLogEntity(
        id = "water-$localDate",
        userId = "user",
        localDate = localDate,
        amountMl = amountMl,
        loggedAtMillis = 0L
    )

    private fun walk(id: String, startedAtMillis: Long, distanceMeters: Double, state: String = WalkState.FINISHED.name) =
        WalkSessionEntity(
            id = id,
            userId = "user",
            state = state,
            startedAtMillis = startedAtMillis,
            endedAtMillis = startedAtMillis + 1_000,
            resumedAtMillis = null,
            distanceMeters = distanceMeters,
            stepBaseline = null,
            steps = null
        )

    private fun workout(
        id: String,
        performedOn: LocalDate,
        volumeKg: Double = 0.0,
        sets: List<WorkoutSetLog> = emptyList()
    ): WorkoutRecord {
        val workoutSets = sets.ifEmpty {
            if (volumeKg == 0.0) emptyList() else listOf(completedSet(weightKg = volumeKg, reps = 1))
        }
        return WorkoutRecord(
            id = id,
            sessionId = "session-$id",
            sessionName = "Session $id",
            performedOn = performedOn,
            startedAtMillis = 0L,
            finishedAtMillis = 1_000L,
            completedTargetIds = emptySet(),
            completedLogicalTargets = 1,
            totalLogicalTargets = 1,
            sets = workoutSets
        )
    }

    private fun completedSet(
        exerciseId: String = "ex1",
        weightKg: Double = 50.0,
        reps: Int = 10
    ) = WorkoutSetLog(
        id = "set-${exerciseId}-${weightKg}-${reps}",
        targetId = "target-$exerciseId",
        exerciseId = exerciseId,
        setNumber = 1,
        reps = reps,
        weightKg = weightKg,
        completed = true
    )

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int): Long =
        java.time.ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun dateMillis(year: Int, month: Int, day: Int, hour: Int): Long =
        utcMillis(year, month, day, hour)
}

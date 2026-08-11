package com.avitoohband.nutrun

import java.time.LocalDate
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrainingViewModelTest {
    @Test
    fun durableReminderSaveWaitsForRepositoryBeforeMutatingOrReportingSuccess() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(session = SessionPreferences(authenticatedUserId = "account-a"))
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", trainingPayload("Stored"), 1L))
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        runtime.saveGate = CompletableDeferred()

        val save = async {
            model.persistSupplementReminders(
                accountId = "account-a",
                configurations = mapOf(
                    "stored-supplement" to SupplementReminderConfig(true, 600)
                )
            )
        }
        runtime.saveStarted.await()

        assertFalse(save.isCompleted)
        assertFalse(model.supplements.single().reminderEnabled)
        assertTrue(runtime.savedPayloads.isEmpty())
        runtime.saveGate!!.complete(Unit)

        assertEquals(NotificationSettingsSaveResult.Success("account-a"), save.await())
        assertTrue(model.supplements.single().reminderEnabled)
        assertEquals(600, model.supplements.single().reminderMinute)
        assertEquals(1, runtime.savedPayloads.size)
    }

    @Test
    fun durableReminderSaveFailureOrCancellationDoesNotMutateMemory() = runBlocking {
        listOf<Throwable>(
            IllegalStateException("disk full"),
            CancellationException("write cancelled")
        ).forEach { failure ->
            val runtime = FakeTrainingViewModelRuntime(
                session = SessionPreferences(authenticatedUserId = "account-a")
            )
            runtime.trainingStates.tryEmit(
                TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
            )
            runtime.saveFailure = failure
            val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

            val result = model.persistSupplementReminders(
                accountId = "account-a",
                configurations = mapOf(
                    "stored-supplement" to SupplementReminderConfig(true, 600)
                )
            )

            assertTrue(result is NotificationSettingsSaveResult.Failed)
            val failed = result as NotificationSettingsSaveResult.Failed
            assertEquals(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS, failed.stage)
            assertTrue(failed.message.contains(failure.message.orEmpty()))
            assertFalse(model.supplements.single().reminderEnabled)
            assertTrue(runtime.savedPayloads.isEmpty())
        }
    }

    @Test
    fun durableReminderSaveDetectsAccountSwitchAndDoesNotApplyAToB() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", trainingPayload("Stored"), 1L))
        runtime.saveGate = CompletableDeferred()
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

        val save = async {
            model.persistSupplementReminders(
                accountId = "account-a",
                configurations = mapOf(
                    "stored-supplement" to SupplementReminderConfig(true, 600)
                )
            )
        }
        runtime.saveStarted.await()
        runtime.selectAccount("account-b")
        yield()
        runtime.saveGate!!.complete(Unit)

        val result = save.await()
        assertTrue(result is NotificationSettingsSaveResult.AccountChanged)
        val changed = result as NotificationSettingsSaveResult.AccountChanged
        assertEquals("account-a", changed.expectedAccountId)
        assertEquals("account-b", changed.actualAccountId)
        assertEquals(
            setOf(NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS),
            changed.completedStages
        )
        assertEquals(1, runtime.savedPayloads.size)
    }

    @Test
    fun durableReminderSaveWaitsForAnOlderWholePayloadBeforeWriting() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
        )
        val olderGate = CompletableDeferred<Unit>()
        val olderStarted = runtime.prepareSave(olderGate, nonCancellable = true)
        val reminderStarted = runtime.prepareSave()
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

        model.updateUsesMetricUnits(false)
        olderStarted.await()
        val reminderSave = async {
            model.persistSupplementReminders(
                accountId = "account-a",
                configurations = mapOf(
                    "stored-supplement" to SupplementReminderConfig(true, 600)
                )
            )
        }
        yield()

        try {
            assertFalse(reminderStarted.isCompleted)
        } finally {
            olderGate.complete(Unit)
        }

        assertEquals(NotificationSettingsSaveResult.Success("account-a"), reminderSave.await())
        val persisted = decodeTrainingState(runtime.savedPayloads.last(), model.exerciseLibrary)!!
        assertFalse(persisted.usesMetricUnits)
        assertTrue(persisted.supplements.single().reminderEnabled)
    }

    @Test
    fun durableReminderSaveRepeatsAnIncompleteRepositoryOperationAfterRoomFeedback() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
        )
        val ordinaryRepositoryGate = CompletableDeferred<Unit>()
        val roomEmissionCompleted = CompletableDeferred<Unit>()
        runtime.prepareSave(
            gate = ordinaryRepositoryGate,
            emitToTrainingStatesBeforeCompletion = true,
            roomEmissionCompleted = roomEmissionCompleted
        )
        val durableRepositoryGate = CompletableDeferred<Unit>()
        val durableRepositoryStarted = runtime.prepareSave(durableRepositoryGate)
        val scheduleGate = CompletableDeferred<Unit>()
        runtime.scheduleGate = scheduleGate
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        val configurations = mapOf(
            "stored-supplement" to SupplementReminderConfig(true, 600)
        )

        model.updateSupplementReminder("stored-supplement", true, 600)
        roomEmissionCompleted.await()
        val durableSave = async(start = CoroutineStart.UNDISPATCHED) {
            model.persistSupplementReminders("account-a", configurations)
        }

        withTimeout(5_000) {
            while (!durableSave.isCompleted && !durableRepositoryStarted.isCompleted) yield()
        }
        assertTrue(durableRepositoryStarted.isCompleted)
        assertFalse(durableSave.isCompleted)
        assertTrue(runtime.savedPayloads.isEmpty())

        durableRepositoryGate.complete(Unit)
        withTimeout(5_000) {
            while (!durableSave.isCompleted && !runtime.scheduleStarted.isCompleted) yield()
        }
        assertTrue(runtime.scheduleStarted.isCompleted)
        assertFalse(durableSave.isCompleted)

        scheduleGate.complete(Unit)
        assertEquals(NotificationSettingsSaveResult.Success("account-a"), durableSave.await())
        assertEquals(2, runtime.attemptedPayloads.size)
        assertEquals(1, runtime.savedPayloads.size)
        assertEquals(1, runtime.supplementSchedules.size)
        ordinaryRepositoryGate.complete(Unit)
        Unit
    }

    @Test
    fun durableReminderSaveTakesOverReschedulingWithoutRepeatingCompletedRepositoryWork() =
        runBlocking {
            val runtime = FakeTrainingViewModelRuntime(
                session = SessionPreferences(authenticatedUserId = "account-a")
            )
            runtime.trainingStates.tryEmit(
                TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
            )
            val ordinaryRepositoryGate = CompletableDeferred<Unit>()
            val roomEmissionCompleted = CompletableDeferred<Unit>()
            runtime.prepareSave(
                gate = ordinaryRepositoryGate,
                nonCancellable = true,
                emitToTrainingStatesBeforeCompletion = true,
                roomEmissionCompleted = roomEmissionCompleted
            )
            val duplicateRepositoryStarted = runtime.prepareSave()
            val scheduleGate = CompletableDeferred<Unit>()
            runtime.scheduleGate = scheduleGate
            val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
            val configurations = mapOf(
                "stored-supplement" to SupplementReminderConfig(true, 600)
            )

            model.updateSupplementReminder("stored-supplement", true, 600)
            roomEmissionCompleted.await()
            val durableSave = async(start = CoroutineStart.UNDISPATCHED) {
                model.persistSupplementReminders("account-a", configurations)
            }

            assertFalse(durableSave.isCompleted)
            ordinaryRepositoryGate.complete(Unit)
            withTimeout(5_000) {
                while (
                    !durableSave.isCompleted &&
                    !runtime.scheduleStarted.isCompleted &&
                    !duplicateRepositoryStarted.isCompleted
                ) {
                    yield()
                }
            }
            assertFalse(duplicateRepositoryStarted.isCompleted)
            assertTrue(runtime.scheduleStarted.isCompleted)
            assertFalse(durableSave.isCompleted)

            scheduleGate.complete(Unit)
            assertEquals(NotificationSettingsSaveResult.Success("account-a"), durableSave.await())
            assertEquals(1, runtime.attemptedPayloads.size)
            assertEquals(1, runtime.savedPayloads.size)
            assertEquals(1, runtime.supplementSchedules.size)
        }

    @Test
    fun newerMutationWaitsForReminderSaveAndPersistsAFreshCombinedSnapshot() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
        )
        val reminderGate = CompletableDeferred<Unit>()
        val reminderStarted = runtime.prepareSave(reminderGate, nonCancellable = true)
        val newerStarted = runtime.prepareSave()
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

        val reminderSave = async {
            model.persistSupplementReminders(
                accountId = "account-a",
                configurations = mapOf(
                    "stored-supplement" to SupplementReminderConfig(true, 600)
                )
            )
        }
        reminderStarted.await()
        model.updateUsesMetricUnits(false)
        yield()

        try {
            assertFalse(newerStarted.isCompleted)
        } finally {
            reminderGate.complete(Unit)
        }
        assertEquals(NotificationSettingsSaveResult.Success("account-a"), reminderSave.await())
        newerStarted.await()
        withTimeout(5_000) {
            while (runtime.savedPayloads.size < 2) yield()
        }

        val persisted = decodeTrainingState(runtime.savedPayloads.last(), model.exerciseLibrary)!!
        assertFalse(persisted.usesMetricUnits)
        assertTrue(persisted.supplements.single().reminderEnabled)
    }

    @Test
    fun retryingUnchangedReminderDraftSkipsDuplicateWriteAndExplicitScheduling() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        val configurations = mapOf(
            "stored-supplement" to SupplementReminderConfig(true, 600)
        )
        runtime.prepareSave(emitToTrainingStatesBeforeCompletion = true)

        assertEquals(
            NotificationSettingsSaveResult.Success("account-a"),
            model.persistSupplementReminders("account-a", configurations)
        )
        assertEquals(
            NotificationSettingsSaveResult.Success("account-a"),
            model.persistSupplementReminders("account-a", configurations)
        )

        assertEquals(1, runtime.savedPayloads.size)
        assertEquals(1, runtime.supplementSchedules.size)
    }

    @Test
    fun completedReminderOperationIsIsolatedByAccount() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        val configurations = mapOf(
            "stored-supplement" to SupplementReminderConfig(true, 600)
        )

        assertEquals(
            NotificationSettingsSaveResult.Success("account-a"),
            model.persistSupplementReminders("account-a", configurations)
        )
        runtime.selectAccount("account-b")
        runtime.trainingStates.emit(
            TrainingStateEntity("account-b", trainingPayload("Stored"), 2L)
        )
        withTimeout(5_000) {
            while (model.supplementReminderReadyAccountId != "account-b") yield()
        }

        assertEquals(
            NotificationSettingsSaveResult.Success("account-b"),
            model.persistSupplementReminders("account-b", configurations)
        )
        assertEquals(2, runtime.savedPayloads.size)
        assertEquals(listOf("account-a", "account-b"), runtime.supplementSchedules.map { it.first })
    }

    @Test
    fun coalescedTrainingPersistenceRetainsSupplementRescheduleIntent() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(session = SessionPreferences(authenticatedUserId = "account-a"))
        runtime.trainingStates.tryEmit(null)
        runtime.saveGate = CompletableDeferred()
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

        model.addSupplement("Magnesium", "200 mg", SupplementSchedule(RecurrenceType.DAILY))
        model.addSession("New session", java.time.DayOfWeek.TUESDAY)
        runtime.saveGate!!.complete(Unit)

        assertEquals(1, runtime.supplementSchedules.size)
        assertEquals("account-a", runtime.supplementSchedules.single().first)
        assertEquals(1, runtime.savedPayloads.size)
    }
    @Test
    fun bulkReminderTogglePreservesTimes() {
        val model = TrainingViewModel(null, null)
        val times = model.supplements.associate { it.id to it.reminderMinute }

        model.setAllSupplementReminders(true)

        assertTrue(model.supplements.all(Supplement::reminderEnabled))
        assertEquals(times, model.supplements.associate { it.id to it.reminderMinute })
    }

    @Test
    fun newlyAddedSupplementDefaultsToEnabledAtEight() {
        val model = TrainingViewModel(null, null)

        model.addSupplement(
            name = "Magnesium",
            dose = "200 mg",
            schedule = SupplementSchedule(RecurrenceType.WEEKDAYS, weekdays = setOf(java.time.DayOfWeek.MONDAY))
        )

        val added = model.supplements.last()
        assertTrue(added.reminderEnabled)
        assertEquals(480, added.reminderMinute)
    }

    @Test
    fun individualReminderMutationUpdatesOnlyTheSelectedSupplement() {
        val model = TrainingViewModel(null, null)
        val target = model.supplements.first()
        val untouched = model.supplements.last()

        model.updateSupplementReminder(target.id, enabled = true, minute = 725)

        assertTrue(model.supplements.first { it.id == target.id }.reminderEnabled)
        assertEquals(725, model.supplements.first { it.id == target.id }.reminderMinute)
        assertEquals(untouched, model.supplements.first { it.id == untouched.id })
    }

    @Test
    fun bulkReminderConfigurationsUpdateEachMatchingSupplement() {
        val model = TrainingViewModel(null, null)
        val first = model.supplements.first()
        val last = model.supplements.last()

        model.updateSupplementReminders(
            mapOf(
                first.id to SupplementReminderConfig(enabled = true, minute = 600),
                last.id to SupplementReminderConfig(enabled = false, minute = 1_320)
            )
        )

        assertEquals(
            SupplementReminderConfig(enabled = true, minute = 600),
            model.supplements.first { it.id == first.id }.reminderConfig()
        )
        assertEquals(
            SupplementReminderConfig(enabled = false, minute = 1_320),
            model.supplements.first { it.id == last.id }.reminderConfig()
        )
    }

    @Test
    fun editingSupplementUpdatesItsReminderConfiguration() {
        val model = TrainingViewModel(null, null)
        val target = model.supplements.first()

        model.updateSupplement(
            id = target.id,
            name = "Updated",
            dose = "400 mg",
            schedule = target.schedule,
            reminderEnabled = true,
            reminderMinute = 1_000
        )

        val updated = model.supplements.first { it.id == target.id }
        assertEquals("Updated", updated.name)
        assertTrue(updated.reminderEnabled)
        assertEquals(1_000, updated.reminderMinute)
    }

    @Test
    fun reminderMutationsRejectMinutesOutsideTheDay() {
        val model = TrainingViewModel(null, null)
        val target = model.supplements.first()

        try {
            model.updateSupplementReminder(target.id, enabled = true, minute = 1_440)
            fail("Expected an invalid reminder minute to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun emptyTrainingSessionCannotStart() {
        val model = TrainingViewModel(null, null)
        model.addSession("Regular", java.time.DayOfWeek.SUNDAY)
        val emptySession = requireNotNull(model.selectedSession())

        model.startWorkout(emptySession.id)

        assertNull(model.activeSession())
        assertNull(model.activeWorkoutSessionId)
    }

    @Test
    fun progressionSuggestionRecalculatesAfterFinishedWorkoutWithoutChangingTarget() {
        val (model, session, target) = weightedTargetFixture()

        assertNull(model.progressionSuggestion(target))

        finishSuccessfulWorkout(model, session, target)

        val suggestion = requireNotNull(model.progressionSuggestion(target))
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(60.0, suggestion.currentWeightKg, 0.001)
        assertEquals(62.5, suggestion.suggestedWeightKg, 0.001)
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)
        assertTrue(model.activeSetLogs.isEmpty())
        assertEquals(
            listOf(target.id),
            model.progressionSuggestions(session).map { (suggestedTarget, _) -> suggestedTarget.id }
        )
    }

    @Test
    fun progressionSuggestionRecalculatesAfterWorkoutEditAndDelete() {
        val (model, session, target) = weightedTargetFixture()
        finishSuccessfulWorkout(model, session, target)
        val completedWorkout = model.workoutHistory.single()
        val highRpeWorkout = completedWorkout.copy(
            sets = completedWorkout.sets.map { set ->
                if (set.targetId == target.id) set.copy(rpe = 9.0) else set
            }
        )

        model.updateWorkoutRecord(highRpeWorkout)

        val editedSuggestion = requireNotNull(model.progressionSuggestion(target))
        assertEquals(ProgressionAction.KEEP, editedSuggestion.action)
        assertEquals(60.0, editedSuggestion.suggestedWeightKg, 0.001)
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)

        model.deleteWorkoutRecord(highRpeWorkout.id)

        assertNull(model.progressionSuggestion(target))
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)
    }

    @Test
    fun progressionSuggestionRecalculatesForUnitChangesWithoutChangingActiveLogs() {
        val (model, session, target) = weightedTargetFixture()
        finishSuccessfulWorkout(model, session, target)
        model.startWorkout(session.id)
        val prefilledLogs = model.activeSetLogs[target.id].orEmpty()

        val metricSuggestion = requireNotNull(model.progressionSuggestion(target))
        model.updateUsesMetricUnits(false)
        val imperialSuggestion = requireNotNull(model.progressionSuggestion(target))

        assertEquals(62.5, metricSuggestion.suggestedWeightKg, 0.001)
        assertEquals(62.268, imperialSuggestion.suggestedWeightKg, 0.001)
        assertEquals(prefilledLogs, model.activeSetLogs[target.id])
        assertEquals(60.0, sessionTarget(model, session, target).weightKg!!, 0.001)
    }

    @Test
    fun duplicateExercisesAreRejectedAndRemovalIsSessionOnly() {
        val model = TrainingViewModel(null, null)
        model.selectSession("session-monday-push-biceps")
        val exercise = model.exerciseLibrary.first { candidate ->
            model.selectedSession()!!.exercises.none { it.exercise.id == candidate.id }
        }
        val originalCatalogSize = model.exerciseLibrary.size

        model.addExerciseToSelectedSession(exercise)
        model.addExerciseToSelectedSession(exercise)

        val matches = model.selectedSession()!!.exercises.filter { it.exercise.id == exercise.id }
        assertEquals(1, matches.size)
        model.removeExerciseFromSelectedSession(matches.single().id)
        assertTrue(model.selectedSession()!!.exercises.none { it.exercise.id == exercise.id })
        assertEquals(originalCatalogSize, model.exerciseLibrary.size)
    }

    @Test
    fun finishingWorkoutRetainsSummaryUntilDismissed() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first()
        model.startWorkout(session.id)
        model.toggleExerciseComplete(session.exercises.first().id, true)

        model.finishWorkout()

        assertNull(model.activeSession())
        assertNotNull(model.lastWorkoutSummary)
        assertEquals(1, model.lastWorkoutSummary!!.completedExercises)
        model.dismissWorkoutSummary()
        assertNull(model.lastWorkoutSummary)
    }

    @Test
    fun acceptingProgressionUpdatesFutureTarget() {
        val model = TrainingViewModel(null, null)

        model.decideSuggestion(SuggestionDecision.ACCEPTED, 45.0)

        val target = model.sessions.flatMap { it.exercises }.first { it.exercise.id == "lat-pulldown" }
        assertEquals(45.0, target.weightKg!!, 0.001)
        assertFalse(model.history.isEmpty())
    }

    @Test
    fun cardioAlternativesAreMutuallyExclusiveAndCountAsOneTarget() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-sunday-cardio" }
        val walk = session.exercises.first()
        val swim = session.exercises.last()

        model.startWorkout(session.id)
        model.toggleExerciseComplete(walk.id, true)
        model.toggleExerciseComplete(swim.id, true)

        assertFalse(model.completedExerciseIds[walk.id] == true)
        assertTrue(model.completedExerciseIds[swim.id] == true)
        model.finishWorkout()
        assertEquals(1, model.lastWorkoutSummary!!.completedExercises)
        assertEquals(1, model.lastWorkoutSummary!!.totalExercises)
        assertTrue(model.history.first().startsWith(formatToday()))
    }

    @Test
    fun completedSetsCreateStructuredHistoryAndBecomePreviousValues() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val target = session.exercises.first()

        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = target.id,
            setNumber = 1,
            reps = 8,
            weightKg = 60.0,
            durationSeconds = null,
            rpe = 8.0,
            completed = true
        )
        model.finishWorkout()

        val workout = model.workoutHistory.single()
        val loggedSet = workout.sets.first {
            it.targetId == target.id && it.setNumber == 1
        }
        assertEquals(session.id, workout.sessionId)
        assertEquals(480.0, workout.totalVolumeKg, 0.001)
        assertEquals(target.exercise.name, loggedSet.exerciseName)
        assertEquals(8, loggedSet.reps)
        assertEquals(60.0, loggedSet.weightKg!!, 0.001)
        assertEquals(8.0, loggedSet.rpe!!, 0.001)
        assertEquals(60.0, model.personalRecords().single().bestWeightKg, 0.001)
        assertEquals(1, model.previousSets(target.exercise.id).count { it.completed })
    }

    @Test
    fun movedAndSkippedSessionsChangeOnlyTheSelectedWeek() {
        val model = TrainingViewModel(null, null)
        val week = trainingWeek(LocalDate.of(2026, 7, 20))
        val monday = week.first()
        val tuesday = monday.plusDays(1)
        val mondaySession = model.sessions.first { it.weekday == java.time.DayOfWeek.MONDAY }

        model.rescheduleSession(mondaySession.id, monday, tuesday)

        assertFalse(model.sessionsForDate(monday).any { it.id == mondaySession.id })
        assertTrue(model.sessionsForDate(tuesday).any { it.id == mondaySession.id })
        model.skipSession(mondaySession.id, monday)
        assertFalse(model.sessionsForDate(monday).any { it.id == mondaySession.id })
        assertFalse(model.sessionsForDate(tuesday).any { it.id == mondaySession.id })
    }

    @Test
    fun configurableRestTimerUsesTheSavedDefault() {
        val model = TrainingViewModel(null, null)
        model.updateDefaultRestTimerSeconds(120)
        val beforeStart = System.currentTimeMillis()

        model.startRestTimer()

        val remaining = model.restTimerEndAtMillis!! - beforeStart
        assertEquals(120, model.defaultRestTimerSeconds)
        assertTrue(remaining in 119_000L..121_000L)
    }

    @Test
    fun weightUnitCanSwitchBetweenKilogramsAndPounds() {
        val model = TrainingViewModel(null, null)

        model.updateUsesMetricUnits(false)

        assertFalse(model.usesMetricUnits)
        assertEquals("132.3 lb", displayWeight(60.0, model.usesMetricUnits))

        model.updateUsesMetricUnits(true)

        assertTrue(model.usesMetricUnits)
        assertEquals("60 kg", displayWeight(60.0, model.usesMetricUnits))
    }

    @Test
    fun workoutHistoryCanBeEditedAndDeleted() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val target = session.exercises.first()
        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = target.id,
            setNumber = 1,
            reps = 8,
            weightKg = 60.0,
            durationSeconds = null,
            rpe = 8.0,
            completed = true
        )
        model.finishWorkout()
        val original = model.workoutHistory.single()
        val editedDate = LocalDate.of(2026, 7, 28)
        val edited = original.copy(
            sessionName = "Edited push workout",
            performedOn = editedDate,
            sets = original.sets.map { set ->
                if (set.targetId == target.id && set.setNumber == 1) {
                    set.copy(reps = 10, weightKg = 65.0)
                } else {
                    set
                }
            }
        )

        model.updateWorkoutRecord(edited)

        val saved = model.workoutHistory.single()
        val savedSet = saved.sets.first {
            it.targetId == target.id && it.setNumber == 1
        }
        assertEquals("Edited push workout", saved.sessionName)
        assertEquals(editedDate, saved.performedOn)
        assertEquals(10, savedSet.reps)
        assertEquals(65.0, savedSet.weightKg!!, 0.001)
        assertTrue(model.history.first().contains("Edited push workout"))

        model.deleteWorkoutRecord(saved.id)

        assertTrue(model.workoutHistory.isEmpty())
        assertTrue(model.history.none { it.contains("Edited push workout") })
    }

    @Test
    fun cancellingWorkoutDiscardsActiveProgressWithoutHistory() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first()
        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = session.exercises.first().id,
            setNumber = 1,
            reps = 8,
            weightKg = 40.0,
            durationSeconds = null,
            rpe = 7.0,
            completed = true
        )

        model.cancelWorkout()

        assertNull(model.activeSession())
        assertTrue(model.activeSetLogs.isEmpty())
        assertTrue(model.completedExerciseIds.isEmpty())
        assertTrue(model.workoutHistory.isEmpty())
        assertNull(model.restTimerEndAtMillis)
    }

    private fun weightedTargetFixture(): Triple<TrainingViewModel, TrainingSession, ExerciseTarget> {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val originalTarget = session.exercises.first()
        model.selectSession(session.id)
        model.updateSelectedExercise(
            targetId = originalTarget.id,
            sets = originalTarget.sets,
            reps = originalTarget.reps,
            weightKg = 60.0,
            durationMinutes = originalTarget.durationMinutes,
            distanceKm = originalTarget.distanceKm
        )
        val updatedSession = requireNotNull(model.selectedSession())
        val target = updatedSession.exercises.first { it.id == originalTarget.id }
        return Triple(model, updatedSession, target)
    }

    private fun finishSuccessfulWorkout(
        model: TrainingViewModel,
        session: TrainingSession,
        target: ExerciseTarget
    ) {
        model.startWorkout(session.id)
        repeat(target.sets) { index ->
            model.updateWorkoutSet(
                targetId = target.id,
                setNumber = index + 1,
                reps = requireNotNull(target.maximumReps),
                weightKg = 60.0,
                durationSeconds = null,
                rpe = 8.0,
                completed = true
            )
        }
        model.finishWorkout()
    }

    private fun sessionTarget(
        model: TrainingViewModel,
        session: TrainingSession,
        target: ExerciseTarget
    ): ExerciseTarget = model.sessions
        .first { it.id == session.id }
        .exercises
        .first { it.id == target.id }

    private fun Supplement.reminderConfig() = SupplementReminderConfig(
        enabled = reminderEnabled,
        minute = reminderMinute
    )

    private fun trainingPayload(supplementName: String): String = encodeTrainingState(
        supplements = listOf(
            Supplement(
                id = "stored-supplement",
                name = supplementName,
                dose = "100 mg",
                schedule = SupplementSchedule(RecurrenceType.DAILY)
            )
        ),
        sessions = emptyList(),
        history = emptyList(),
        selectedSessionId = null,
        activeWorkoutSessionId = null,
        isWorkoutPaused = false,
        completedExerciseIds = emptyMap(),
        suggestionDecision = SuggestionDecision.PENDING,
        suggestedWeightKg = 42.5
    )

    private class FakeTrainingViewModelRuntime(
        session: SessionPreferences
    ) : TrainingViewModelRuntime {
        private val sessionState = MutableStateFlow(session)
        override val session: Flow<SessionPreferences> = sessionState
        val trainingStates = MutableSharedFlow<TrainingStateEntity?>(replay = 1)
        val attemptedPayloads = mutableListOf<String>()
        val savedPayloads = mutableListOf<String>()
        val supplementSchedules = mutableListOf<Pair<String, SupplementReminderSettingsEntity>>()
        var saveGate: CompletableDeferred<Unit>? = null
        val saveStarted = CompletableDeferred<Unit>()
        var scheduleGate: CompletableDeferred<Unit>? = null
        val scheduleStarted = CompletableDeferred<Unit>()
        private val saveControls = mutableListOf<SaveControl>()
        private var saveCallCount = 0
        var saveFailure: Throwable? = null

        fun prepareSave(
            gate: CompletableDeferred<Unit>? = null,
            nonCancellable: Boolean = false,
            emitToTrainingStatesBeforeCompletion: Boolean = false,
            roomEmissionCompleted: CompletableDeferred<Unit>? = null
        ): CompletableDeferred<Unit> {
            val started = CompletableDeferred<Unit>()
            saveControls += SaveControl(
                gate = gate,
                nonCancellable = nonCancellable,
                started = started,
                emitToTrainingStatesBeforeCompletion = emitToTrainingStatesBeforeCompletion,
                roomEmissionCompleted = roomEmissionCompleted
            )
            return started
        }

        override fun trainingState(userId: String): Flow<TrainingStateEntity?> = trainingStates

        override suspend fun currentUserId(): String? = sessionState.value.authenticatedUserId

        override suspend fun saveTrainingState(userId: String, payload: String) {
            require(sessionState.value.authenticatedUserId == userId)
            val control = saveControls.getOrNull(saveCallCount++)
            saveStarted.complete(Unit)
            control?.started?.complete(Unit)
            attemptedPayloads += payload
            if (control?.emitToTrainingStatesBeforeCompletion == true) {
                trainingStates.emit(TrainingStateEntity(userId, payload, 1L))
                control.roomEmissionCompleted?.complete(Unit)
            }
            val gate = control?.gate ?: saveGate
            if (gate != null) {
                if (control?.nonCancellable == true) {
                    withContext(NonCancellable) { gate.await() }
                } else {
                    gate.await()
                }
            }
            saveFailure?.let { throw it }
            savedPayloads += payload
        }

        fun selectAccount(accountId: String) {
            sessionState.value = SessionPreferences(authenticatedUserId = accountId)
        }

        override suspend fun currentTrainingReminderSettings(userId: String) = null

        override suspend fun currentSupplementReminderSettings(userId: String) =
            SupplementReminderSettingsEntity(userId = userId)

        override fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) = Unit

        override suspend fun scheduleSupplement(
            userId: String,
            settings: SupplementReminderSettingsEntity
        ) {
            scheduleStarted.complete(Unit)
            scheduleGate?.await()
            supplementSchedules += userId to settings
        }

        private data class SaveControl(
            val gate: CompletableDeferred<Unit>?,
            val nonCancellable: Boolean,
            val started: CompletableDeferred<Unit>,
            val emitToTrainingStatesBeforeCompletion: Boolean,
            val roomEmissionCompleted: CompletableDeferred<Unit>?
        )
    }
}

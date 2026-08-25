package com.avitoohband.nutrun

import java.time.LocalDate
import com.avitoohband.nutrun.data.SessionPreferences
import com.avitoohband.nutrun.data.SupplementReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingReminderSettingsEntity
import com.avitoohband.nutrun.data.TrainingStateEntity
import com.avitoohband.nutrun.reminders.ReminderSystem
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    fun createWorkoutTrimsNameAndStartsEmpty() {
        val model = TrainingViewModel(null, null)
        assertEquals(TrainingMutationResult.Success, model.createWorkout("  Push B  "))
        val created = model.workoutTemplates.last()
        assertEquals("Push B", created.name)
        assertTrue(created.exercises.isEmpty())
        assertTrue(created.id.startsWith("workout-"))
    }

    @Test
    fun blankWorkoutNamesAreRejectedWithoutChangingTemplates() {
        val model = TrainingViewModel(null, null)
        val before = model.workoutTemplates.toList()

        assertTrue(model.createWorkout("   ") is TrainingMutationResult.ValidationError)
        assertTrue(model.renameWorkout(before.first().id, "\t") is TrainingMutationResult.ValidationError)
        assertEquals(before, model.workoutTemplates)
    }

    @Test
    fun renameWorkoutTrimsTheReplacementName() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()

        assertEquals(TrainingMutationResult.Success, model.renameWorkout(template.id, "  Upper A  "))
        assertEquals("Upper A", model.workoutTemplates.first { it.id == template.id }.name)
    }

    @Test
    fun duplicateWorkoutUsesFreshTypedIdAndCollisionSafeNamesWithoutCopyingRuntimeState() {
        val model = TrainingViewModel(null, null)
        val source = model.workoutTemplates.first { it.exercises.isNotEmpty() && it.guidance.isNotEmpty() }
        val historyBefore = model.workoutHistory.toList()
        val activeBefore = model.activeWorkoutSessionId

        assertEquals(TrainingMutationResult.Success, model.duplicateWorkout(source.id))
        assertEquals(TrainingMutationResult.Success, model.duplicateWorkout(source.id))

        val firstCopy = model.workoutTemplates[model.workoutTemplates.lastIndex - 1]
        val secondCopy = model.workoutTemplates.last()
        assertEquals("${source.name} Copy", firstCopy.name)
        assertEquals("${source.name} Copy 2", secondCopy.name)
        assertTrue(firstCopy.id.isTypedUuid("workout-"))
        assertTrue(secondCopy.id.isTypedUuid("workout-"))
        assertTrue(firstCopy.id != secondCopy.id && firstCopy.id != source.id)
        assertEquals(source.exercises.map { it.copy(id = "") }, firstCopy.exercises.map { it.copy(id = "") })
        assertTrue(source.exercises.map(ExerciseTarget::id).toSet().intersect(firstCopy.exercises.map(ExerciseTarget::id).toSet()).isEmpty())
        assertEquals(source.guidance, firstCopy.guidance)
        assertEquals(WorkoutTemplateOrigin.USER_CREATED, firstCopy.origin)
        assertEquals(historyBefore, model.workoutHistory)
        assertEquals(activeBefore, model.activeWorkoutSessionId)
    }

    @Test
    fun duplicateWorkoutPersistsTheCopiedTemplate() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val source = WorkoutTemplate(
            id = "source-template",
            name = "Source",
            exercises = listOf(
                ExerciseTarget("source-target", builtInExerciseCatalog().first())
            ),
            guidance = listOf("Keep one repetition in reserve.")
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity(
                "account-a",
                encodeTrainingState(workoutTemplates = listOf(source)),
                1L
            )
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        assertEquals(TrainingMutationResult.Success, model.duplicateWorkout(source.id))
        withTimeout(5_000) { while (runtime.savedPayloads.isEmpty()) yield() }

        val persisted = requireNotNull(
            decodeTrainingState(runtime.savedPayloads.last(), builtInExerciseCatalog())
        )
        assertEquals(listOf("Source", "Source Copy"), persisted.workoutTemplates.map(WorkoutTemplate::name))
        assertTrue(persisted.workoutTemplates.last().id.isTypedUuid("workout-"))
    }
    @Test
    fun duplicateWorkoutRejectsUnknownTemplateWithoutMutation() {
        val model = TrainingViewModel(null, null)
        val before = model.workoutTemplates.toList()

        assertTrue(model.duplicateWorkout("missing") is TrainingMutationResult.ValidationError)
        assertEquals(before, model.workoutTemplates)
    }

    @Test
    fun saveWorkoutDraftAppliesNameTargetsAndCustomCatalogAtomically() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        val builtIn = model.exerciseLibrary.first { exercise ->
            template.exercises.none { it.exercise.id == exercise.id }
        }
        val custom = Exercise(
            id = "exercise-00000000-0000-0000-0000-000000000011",
            name = "Draft carry",
            category = "Custom",
            primaryMuscles = "Core",
            secondaryMuscles = "",
            instructions = "",
            safetyNote = ""
        )
        val targets = listOf(
            ExerciseTarget(id = "draft-built-in", exercise = builtIn, sets = 4),
            ExerciseTarget(id = "draft-custom", exercise = custom, sets = 2)
        )

        assertEquals(
            TrainingMutationResult.Success,
            model.saveWorkoutDraft(template.id, "  Draft workout  ", targets, listOf(custom))
        )
        val saved = model.workoutTemplates.first { it.id == template.id }
        assertEquals("Draft workout", saved.name)
        assertEquals(targets, saved.exercises)
        assertEquals(custom, model.customExercises.single { it.id == custom.id })
    }
    @Test
    fun setCountIsStoredPerTemplateAndRejectsZeroOrTwentyOne() {
        val model = TrainingViewModel(null, null)
        val first = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        val second = model.workoutTemplates.first { it.id != first.id && it.exercises.isNotEmpty() }
        val target = first.exercises.first()

        assertEquals(TrainingMutationResult.Success, model.updateTargetSets(first.id, target.id, 7))
        assertEquals(
            7,
            model.workoutTemplates.first { it.id == first.id }.exercises.first { it.id == target.id }.sets
        )
        assertEquals(second, model.workoutTemplates.first { it.id == second.id })
        assertTrue(model.updateTargetSets(first.id, target.id, 0) is TrainingMutationResult.ValidationError)
        assertTrue(model.updateTargetSets(first.id, target.id, 21) is TrainingMutationResult.ValidationError)
    }

    @Test
    fun customExerciseIsAddedToCatalogAndSelectedWorkout() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        val result = model.createCustomExerciseAndAdd(
            template.id,
            CustomExerciseDraft(name = "Suitcase march", primaryMuscles = "Core")
        )
        assertEquals(TrainingMutationResult.Success, result)
        val custom = model.customExercises.single { it.name == "Suitcase march" }
        assertTrue(custom.id.startsWith("exercise-"))
        assertEquals(
            custom.id,
            model.workoutTemplates.first { it.id == template.id }.exercises.last().exercise.id
        )
        assertTrue(model.exerciseLibrary.any { it.id == custom.id })
    }

    @Test
    fun replacingAssignmentsDeduplicatesInOrderAndClearsRestDay() {
        val model = TrainingViewModel(null, null)
        val push = model.workoutTemplates.first { it.id == "session-monday-push-biceps" }
        val walk = model.workoutTemplates.first { it.id == "session-sunday-cardio" }

        assertEquals(
            TrainingMutationResult.Success,
            model.replaceAssignments(java.time.DayOfWeek.MONDAY, listOf(push.id, walk.id, push.id))
        )

        val monday = model.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.MONDAY }
        assertEquals(listOf(push.id, walk.id), monday.templateIds)
        assertFalse(monday.isRestDay)
    }

    @Test
    fun settingRestDayClearsEveryAssignment() {
        val model = TrainingViewModel(null, null)

        assertEquals(TrainingMutationResult.Success, model.setRestDay(java.time.DayOfWeek.MONDAY))

        assertEquals(
            WeeklyDayPlan(java.time.DayOfWeek.MONDAY, isRestDay = true),
            model.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.MONDAY }
        )
    }

    @Test
    fun addingAnExerciseAlreadyInTemplateIsRejectedWithoutChangingTargets() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        val before = template.exercises
        model.selectSession(template.id)

        val result = model.addExerciseToSelectedSession(template.exercises.first().exercise)

        assertTrue(result is TrainingMutationResult.ValidationError)
        assertEquals(before, model.workoutTemplates.first { it.id == template.id }.exercises)
    }

    @Test
    fun blankAndCaseFoldedDuplicateCustomNamesDoNotMutateCatalogOrTargets() {
        val model = TrainingViewModel(null, null)
        val template = model.workoutTemplates.first()
        assertEquals(
            TrainingMutationResult.Success,
            model.createCustomExerciseAndAdd(template.id, CustomExerciseDraft(name = "Suitcase march"))
        )
        val catalogBefore = model.customExercises.toList()
        val targetsBefore = model.workoutTemplates.first { it.id == template.id }.exercises

        assertTrue(
            model.createCustomExerciseAndAdd(template.id, CustomExerciseDraft(name = "  ")) is
                TrainingMutationResult.ValidationError
        )
        assertTrue(
            model.createCustomExerciseAndAdd(template.id, CustomExerciseDraft(name = "SUITCASE MARCH")) is
                TrainingMutationResult.ValidationError
        )

        assertEquals(catalogBefore, model.customExercises)
        assertEquals(targetsBefore, model.workoutTemplates.first { it.id == template.id }.exercises)
    }

    @Test
    fun deletingTemplateRemovesCurrentReferencesButPreservesHistoryPastOverridesAndCustomExercises() {
        val model = TrainingViewModel(null, null)
        val today = LocalDate.of(2026, 8, 13)
        val deleted = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        val retained = model.workoutTemplates.first { it.id != deleted.id }
        val custom = customExercise(
            "exercise-00000000-0000-0000-0000-000000000099",
            "Retained custom"
        )
        model.customExercises += custom
        model.weeklyDayPlans.clear()
        model.weeklyDayPlans += WeeklyDayPlan(java.time.DayOfWeek.MONDAY, listOf(deleted.id, retained.id))
        val oldOverride = TrainingScheduleOverride(deleted.id, today.minusDays(1), today)
        val todayOverride = TrainingScheduleOverride(deleted.id, today, today.plusDays(1))
        val futureOverride = TrainingScheduleOverride(deleted.id, today.plusDays(1), null, skipped = true)
        val retainedOverride = TrainingScheduleOverride(retained.id, today.plusDays(2), today.plusDays(3))
        model.scheduleOverrides += listOf(oldOverride, todayOverride, futureOverride, retainedOverride)
        val record = WorkoutRecord(
            id = "history-1",
            sessionId = deleted.id,
            sessionName = deleted.name,
            performedOn = today.minusDays(2),
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            completedTargetIds = emptySet(),
            completedLogicalTargets = 0,
            totalLogicalTargets = deleted.exercises.size,
            sets = emptyList()
        )
        model.workoutHistory += record

        assertEquals(TrainingMutationResult.Success, model.deleteWorkout(deleted.id, today))

        assertTrue(model.workoutTemplates.none { it.id == deleted.id })
        assertEquals(
            listOf(retained.id),
            model.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.MONDAY }.templateIds
        )
        assertEquals(listOf(oldOverride, retainedOverride), model.scheduleOverrides)
        assertEquals(listOf(record), model.workoutHistory)
        assertEquals(listOf(custom), model.customExercises)
    }

    @Test
    fun deletingActiveTemplateReturnsConflictAndLeavesAllStateUnchanged() {
        val model = TrainingViewModel(null, null)
        val active = model.workoutTemplates.first { it.exercises.isNotEmpty() }
        model.selectSession(active.id)
        model.startWorkout(active.id)
        val templates = model.workoutTemplates.toList()
        val plans = model.weeklyDayPlans.toList()
        val overrides = model.scheduleOverrides.toList()
        val selected = model.selectedSessionId
        val activeId = model.activeWorkoutSessionId
        val logs = model.activeSetLogs.toMap()

        assertEquals(TrainingMutationResult.ActiveWorkoutConflict, model.deleteWorkout(active.id))

        assertEquals(templates, model.workoutTemplates)
        assertEquals(plans, model.weeklyDayPlans)
        assertEquals(overrides, model.scheduleOverrides)
        assertEquals(selected, model.selectedSessionId)
        assertEquals(activeId, model.activeWorkoutSessionId)
        assertEquals(logs, model.activeSetLogs)
    }

    @Test
    fun mutationsReturnNotReadyUntilTheAccountsFirstPayload() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        val before = model.workoutTemplates.toList()

        assertEquals(TrainingMutationResult.NotReady, model.createWorkout("Too early"))
        assertEquals(before, model.workoutTemplates)
        assertTrue(runtime.attemptedPayloads.isEmpty())
    }

    @Test
    fun switchingAccountsRestoresOnlyTheNewAccountsCustomExercisesAfterItsFirstPayload() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val accountAExercise = customExercise(
            "exercise-00000000-0000-0000-0000-0000000000a1",
            "Account A carry"
        )
        val accountBExercise = customExercise(
            "exercise-00000000-0000-0000-0000-0000000000b1",
            "Account B carry"
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity("account-a", encodeTrainingState(customExercises = listOf(accountAExercise)), 1L)
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }
        assertEquals(listOf(accountAExercise), model.customExercises)

        runtime.selectAccount("account-b")
        withTimeout(5_000) { while (model.trainingMutationsReady) yield() }
        assertTrue(model.customExercises.isEmpty())
        runtime.trainingStates.emit(
            TrainingStateEntity("account-b", encodeTrainingState(customExercises = listOf(accountBExercise)), 2L)
        )
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        assertEquals(listOf(accountBExercise), model.customExercises)
        assertTrue(model.exerciseLibrary.none { it.id == accountAExercise.id })
        assertTrue(model.exerciseLibrary.any { it.id == accountBExercise.id })
    }

    @Test
    fun failedMutationSaveRestoresExactSnapshotAndExposesDismissibleError() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        runtime.saveFailure = IllegalStateException("disk full")
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }
        val templates = model.workoutTemplates.toList()
        val plans = model.weeklyDayPlans.toList()
        val custom = model.customExercises.toList()
        val overrides = model.scheduleOverrides.toList()
        val selected = model.selectedSessionId
        val active = model.activeWorkoutSessionId
        val started = model.activeWorkoutStartedAtMillis
        val completed = model.completedExerciseIds.toMap()
        val logs = model.activeSetLogs.toMap()

        assertEquals(TrainingMutationResult.Success, model.renameWorkout(fixture.template.id, "Changed"))
        withTimeout(5_000) { while (model.mutationError == null) yield() }

        assertEquals(templates, model.workoutTemplates)
        assertEquals(plans, model.weeklyDayPlans)
        assertEquals(custom, model.customExercises)
        assertEquals(overrides, model.scheduleOverrides)
        assertEquals(selected, model.selectedSessionId)
        assertEquals(active, model.activeWorkoutSessionId)
        assertEquals(started, model.activeWorkoutStartedAtMillis)
        assertEquals(completed, model.completedExerciseIds)
        assertEquals(logs, model.activeSetLogs)
        assertEquals("disk full", model.mutationError)

        model.dismissMutationError()
        assertNull(model.mutationError)
    }

    @Test
    fun authoritativeSameAccountPayloadInvalidatesOlderMutationRollback() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val blockedGate = CompletableDeferred<Unit>()
        val blockedCompleted = CompletableDeferred<Unit>()
        val blockedStarted = runtime.prepareSave(
            gate = blockedGate,
            nonCancellable = true,
            failure = IllegalStateException("older write failed"),
            completion = blockedCompleted
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        assertEquals(TrainingMutationResult.Success, model.renameWorkout(fixture.template.id, "Optimistic"))
        blockedStarted.await()
        val restored = requireNotNull(decodeTrainingState(fixture.payload, builtInExerciseCatalog()))
        val authoritativeTemplate = restored.workoutTemplates.single().copy(name = "Authoritative")
        val authoritativePayload = encodeTrainingState(
            selectedSessionId = restored.selectedSessionId,
            activeWorkoutSessionId = restored.activeWorkoutSessionId,
            completedExerciseIds = restored.completedExerciseIds,
            scheduleOverrides = restored.scheduleOverrides,
            activeSetLogs = restored.activeSetLogs,
            activeWorkoutStartedAtMillis = restored.activeWorkoutStartedAtMillis,
            customExercises = restored.customExercises,
            workoutTemplates = listOf(authoritativeTemplate),
            weeklyDayPlans = restored.weeklyDayPlans
        )
        runtime.trainingStates.emit(TrainingStateEntity("account-a", authoritativePayload, 2L))
        withTimeout(5_000) {
            while (model.workoutTemplates.single().name != "Authoritative") yield()
        }

        blockedGate.complete(Unit)
        blockedCompleted.await()

        assertEquals("Authoritative", model.workoutTemplates.single().name)
        assertNull(model.mutationError)
    }

    @Test
    fun invalidCustomNumericDefaultsAreRejectedWithoutMutationOrSave() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }
        val templatesBefore = model.workoutTemplates.toList()
        val customBefore = model.customExercises.toList()
        val invalidDrafts = listOf(
            CustomExerciseDraft("Bad sets", defaultSets = 0),
            CustomExerciseDraft("Bad reps", defaultReps = 0),
            CustomExerciseDraft("Negative weight", defaultWeightKg = -0.1),
            CustomExerciseDraft("NaN weight", defaultWeightKg = Double.NaN),
            CustomExerciseDraft("Infinite weight", defaultWeightKg = Double.POSITIVE_INFINITY),
            CustomExerciseDraft("Zero duration", defaultDurationMinutes = 0),
            CustomExerciseDraft("Negative duration", defaultDurationMinutes = -1),
            CustomExerciseDraft("Zero distance", defaultDistanceKm = 0.0),
            CustomExerciseDraft("Negative distance", defaultDistanceKm = -0.1),
            CustomExerciseDraft("NaN distance", defaultDistanceKm = Double.NaN),
            CustomExerciseDraft("Infinite distance", defaultDistanceKm = Double.NEGATIVE_INFINITY)
        )

        invalidDrafts.forEach { draft ->
            assertTrue(
                model.createCustomExerciseAndAdd(fixture.template.id, draft) is
                    TrainingMutationResult.ValidationError
            )
        }

        assertEquals(templatesBefore, model.workoutTemplates)
        assertEquals(customBefore, model.customExercises)
        assertTrue(runtime.attemptedPayloads.isEmpty())
    }

    @Test
    fun encodingFailureRestoresExactMutationSnapshotAndExposesDismissibleError() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }
        model.customExercises += customExercise(
            "exercise-00000000-0000-0000-0000-0000000000e1",
            "Legacy invalid"
        ).copy(defaultWeightKg = Double.NaN)
        val templatesBefore = model.workoutTemplates.toList()
        val plansBefore = model.weeklyDayPlans.toList()
        val customBefore = model.customExercises.toList()
        val overridesBefore = model.scheduleOverrides.toList()
        val selectedBefore = model.selectedSessionId
        val activeBefore = model.activeWorkoutSessionId

        assertEquals(TrainingMutationResult.Success, model.renameWorkout(fixture.template.id, "Changed"))
        withTimeout(5_000) { while (model.mutationError == null) yield() }

        assertEquals(templatesBefore, model.workoutTemplates)
        assertEquals(plansBefore, model.weeklyDayPlans)
        assertEquals(customBefore, model.customExercises)
        assertEquals(overridesBefore, model.scheduleOverrides)
        assertEquals(selectedBefore, model.selectedSessionId)
        assertEquals(activeBefore, model.activeWorkoutSessionId)
        assertTrue(runtime.attemptedPayloads.isEmpty())
        assertTrue(model.mutationError.orEmpty().isNotBlank())
        model.dismissMutationError()
        assertNull(model.mutationError)
    }

    @Test
    fun unknownExerciseIdIsRejectedWithoutMutationOrSave() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }
        model.selectSession(fixture.template.id)
        withTimeout(5_000) { while (runtime.savedPayloads.isEmpty()) yield() }
        runtime.clearSaveHistory()
        val targetsBefore = model.workoutTemplates.single().exercises
        val unknown = customExercise(
            "exercise-00000000-0000-0000-0000-0000000000ff",
            "Unknown"
        )

        val result = model.addExerciseToSelectedSession(unknown)

        assertTrue(result is TrainingMutationResult.ValidationError)
        assertEquals(targetsBefore, model.workoutTemplates.single().exercises)
        assertTrue(runtime.attemptedPayloads.isEmpty())
    }

    @Test
    fun exerciseAddRequiresASelectedWorkoutBeforeResolvingTheTarget() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        val restored = requireNotNull(decodeTrainingState(fixture.payload, builtInExerciseCatalog()))
        runtime.trainingStates.tryEmit(
            TrainingStateEntity(
                userId = "account-a",
                payloadJson = encodeTrainingState(
                    customExercises = restored.customExercises,
                    workoutTemplates = restored.workoutTemplates,
                    weeklyDayPlans = restored.weeklyDayPlans
                ),
                updatedAtMillis = 1L
            )
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        val result = model.addExerciseToSelectedSession(
            model.exerciseLibrary.first { it.id == "bench-press" }
        )

        assertEquals(TrainingMutationResult.ValidationError("No workout is selected."), result)
        assertTrue(runtime.attemptedPayloads.isEmpty())
    }

    @Test
    fun exerciseAddUsesCanonicalLibraryMetadataAcrossSaveAndReload() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }
        model.selectSession(fixture.template.id)
        withTimeout(5_000) { while (runtime.savedPayloads.isEmpty()) yield() }
        runtime.clearSaveHistory()
        val canonical = model.exerciseLibrary.first { it.id == "bench-press" }
        val spoofed = canonical.copy(
            name = "Spoofed name",
            category = "Spoofed category",
            primaryMuscles = "Spoofed muscles",
            instructions = "Spoofed instructions"
        )

        assertEquals(TrainingMutationResult.Success, model.addExerciseToSelectedSession(spoofed))
        assertEquals(
            canonical,
            model.workoutTemplates.single().exercises.last().exercise
        )
        withTimeout(5_000) { while (runtime.savedPayloads.isEmpty()) yield() }
        val savedPayload = runtime.savedPayloads.last()
        val persisted = requireNotNull(decodeTrainingState(savedPayload, builtInExerciseCatalog()))
        assertEquals(canonical, persisted.workoutTemplates.single().exercises.last().exercise)

        val reloadRuntime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        reloadRuntime.trainingStates.tryEmit(TrainingStateEntity("account-a", savedPayload, 2L))
        val reloaded = TrainingViewModel(reloadRuntime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!reloaded.trainingMutationsReady) yield() }
        assertEquals(canonical, reloaded.workoutTemplates.single().exercises.last().exercise)
    }

    @Test
    fun decodeIgnoresTypedCustomFromCallerWhenAbsentFromAccountPayload() {
        val stale = customExercise(
            "exercise-00000000-0000-0000-0000-0000000000aa",
            "Other account exercise"
        )
        val template = WorkoutTemplate(
            id = "account-template",
            name = "Account workout",
            exercises = listOf(ExerciseTarget("stale-target", stale))
        )
        val payload = encodeTrainingState(
            workoutTemplates = listOf(template),
            weeklyDayPlans = listOf(WeeklyDayPlan(java.time.DayOfWeek.MONDAY, listOf(template.id)))
        )

        val restored = requireNotNull(
            decodeTrainingState(payload, builtInExerciseCatalog() + stale)
        )

        assertTrue(restored.customExercises.isEmpty())
        assertTrue(restored.workoutTemplates.single().exercises.isEmpty())
    }

    @Test
    fun olderFailedGenerationDoesNotRollbackNewerSuccessfulMutation() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val fixture = rollbackFixture()
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val firstGate = CompletableDeferred<Unit>()
        val firstCompleted = CompletableDeferred<Unit>()
        val firstStarted = runtime.prepareSave(
            gate = firstGate,
            nonCancellable = true,
            failure = IllegalStateException("older write failed"),
            completion = firstCompleted
        )
        val secondCompleted = CompletableDeferred<Unit>()
        val secondStarted = runtime.prepareSave(completion = secondCompleted)
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        assertEquals(TrainingMutationResult.Success, model.renameWorkout(fixture.template.id, "Generation N"))
        firstStarted.await()
        assertEquals(TrainingMutationResult.Success, model.renameWorkout(fixture.template.id, "Generation N plus 1"))
        yield()
        assertFalse(secondStarted.isCompleted)

        firstGate.complete(Unit)
        firstCompleted.await()
        withTimeout(5_000) {
            secondStarted.await()
            secondCompleted.await()
        }
        val persisted = requireNotNull(
            decodeTrainingState(runtime.savedPayloads.single(), builtInExerciseCatalog())
        )
        assertEquals("Generation N plus 1", persisted.workoutTemplates.single().name)

        assertEquals(
            "Generation N plus 1",
            model.workoutTemplates.single { it.id == fixture.template.id }.name
        )
        assertNull(model.mutationError)
    }

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

        model.updateDefaultRestTimerSeconds(120)
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
        assertEquals(120, persisted.defaultRestTimerSeconds)
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
        model.updateDefaultRestTimerSeconds(120)
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
        assertEquals(120, persisted.defaultRestTimerSeconds)
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
    fun ordinaryPersistenceRecoversTrainingFailureAndStillSchedulesSupplements() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(null)
        runtime.trainingScheduleFailure = IllegalStateException("training scheduler failed")
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

        model.addSupplement(
            "Magnesium",
            "200 mg",
            SupplementSchedule(RecurrenceType.DAILY)
        )

        withTimeout(5_000) {
            while (
                runtime.recoverySchedules.isEmpty() ||
                runtime.supplementSchedules.isEmpty()
            ) yield()
        }
        assertEquals(
            listOf(ReminderSystem.TRAINING, ReminderSystem.SUPPLEMENTS),
            runtime.scheduleAttempts
        )
        assertEquals(listOf("account-a" to ReminderSystem.TRAINING), runtime.recoverySchedules)
        assertEquals(1, runtime.supplementSchedules.size)
    }

    @Test
    fun ordinaryPersistenceRecoversSupplementFailureAfterTrainingScheduling() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(null)
        runtime.supplementScheduleFailure = IllegalStateException("supplement scheduler failed")
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))

        model.addSupplement(
            "Magnesium",
            "200 mg",
            SupplementSchedule(RecurrenceType.DAILY)
        )

        withTimeout(5_000) {
            while (runtime.recoverySchedules.isEmpty()) yield()
        }
        assertEquals(
            listOf(ReminderSystem.TRAINING, ReminderSystem.SUPPLEMENTS),
            runtime.scheduleAttempts
        )
        assertEquals(1, runtime.trainingSchedules.size)
        assertEquals(listOf("account-a" to ReminderSystem.SUPPLEMENTS), runtime.recoverySchedules)
    }

    @Test
    fun addSupplementIsRejectedBeforeNullOrStoredFirstPayload() {
        assertMutationRejectedBeforeNullAndStoredFirstPayload(
            snapshot = { it.supplements.toList() },
            mutate = {
                it.addSupplement(
                    "Pre-restore magnesium",
                    "200 mg",
                    SupplementSchedule(RecurrenceType.DAILY)
                )
            }
        )
    }

    @Test
    fun editSupplementIsRejectedBeforeNullOrStoredFirstPayload() {
        assertMutationRejectedBeforeNullAndStoredFirstPayload(
            snapshot = { it.supplements.toList() },
            mutate = { model ->
                val target = model.supplements.first()
                model.updateSupplement(
                    target.id,
                    "${target.name} edited",
                    target.dose,
                    target.schedule
                )
            }
        )
    }

    @Test
    fun removeSupplementIsRejectedBeforeNullOrStoredFirstPayload() {
        assertMutationRejectedBeforeNullAndStoredFirstPayload(
            snapshot = { it.supplements.toList() },
            mutate = { model -> model.removeSupplement(model.supplements.first().id) }
        )
    }

    @Test
    fun supplementCompletionIsRejectedBeforeNullOrStoredFirstPayload() {
        assertMutationRejectedBeforeNullAndStoredFirstPayload(
            snapshot = { it.supplements.toList() },
            mutate = { model ->
                val target = model.supplements.first()
                model.toggleSupplement(
                    target.id,
                    checked = !target.isCompletedOn(LocalDate.now())
                )
            }
        )
    }

    @Test
    fun trainingMutationIsRejectedBeforeNullOrStoredFirstPayload() {
        assertMutationRejectedBeforeNullAndStoredFirstPayload(
            snapshot = { it.sessions.toList() },
            mutate = { model ->
                model.addSession("Pre-restore session", java.time.DayOfWeek.FRIDAY)
            }
        )
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
    fun progressionSuggestionUsesTheProfileDerivedDisplayUnitWithoutChangingActiveLogs() {
        val (model, session, target) = weightedTargetFixture()
        finishSuccessfulWorkout(model, session, target)
        model.startWorkout(session.id)
        val prefilledLogs = model.activeSetLogs[target.id].orEmpty()

        val suggestion = requireNotNull(model.progressionSuggestion(target))

        assertEquals(62.5, suggestion.suggestedWeightKg, 0.001)
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
    fun copyPreviousSetCopiesTheMatchingCompletedSetWithoutCompletingTheNewSet() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.id == "session-monday-push-biceps" }
        val target = session.exercises.first()
        model.startWorkout(session.id)
        model.updateWorkoutSet(
            targetId = target.id,
            setNumber = 1,
            reps = 7,
            weightKg = 62.5,
            durationSeconds = null,
            rpe = 8.5,
            completed = true
        )
        model.finishWorkout()
        model.startWorkout(session.id)

        val result = model.copyPreviousSet(target.id, setNumber = 1)

        assertEquals(TrainingMutationResult.Success, result)
        val copied = model.activeSetLogs.getValue(target.id).first { it.setNumber == 1 }
        assertEquals(7, copied.reps)
        assertEquals(62.5, copied.weightKg ?: -1.0, 0.0001)
        assertEquals(8.5, copied.rpe ?: -1.0, 0.0001)
        assertNull(copied.durationSeconds)
        assertFalse(copied.completed)
    }

    @Test
    fun copyPreviousSetUsesExerciseIdentityAcrossDifferentTargetIds() {
        val model = TrainingViewModel(null, null)
        val sourceSession = model.sessions.first { it.id == "session-wednesday-pull-triceps" }
        val sourceTarget = sourceSession.exercises.first { it.exercise.id == "face-pull" }
        model.startWorkout(sourceSession.id)
        model.updateWorkoutSet(
            targetId = sourceTarget.id,
            setNumber = 1,
            reps = 9,
            weightKg = 55.0,
            durationSeconds = null,
            rpe = 7.5,
            completed = true
        )
        model.finishWorkout()
        val destination = model.workoutTemplates.first { template ->
            template.id != sourceSession.id &&
                template.exercises.any { it.exercise.id == sourceTarget.exercise.id }
        }
        val destinationTarget = destination.exercises.first {
            it.exercise.id == sourceTarget.exercise.id
        }
        model.startWorkout(destination.id)

        assertEquals(
            TrainingMutationResult.Success,
            model.copyPreviousSet(destinationTarget.id, setNumber = 1)
        )
        assertEquals(
            55.0,
            model.activeSetLogs.getValue(destinationTarget.id).first().weightKg ?: -1.0,
            0.0001
        )
    }

    @Test
    fun copyPreviousSetReportsWhenNoMatchingHistoryExists() {
        val model = TrainingViewModel(null, null)
        val session = model.sessions.first { it.exercises.isNotEmpty() }
        val target = session.exercises.first()
        model.startWorkout(session.id)

        assertEquals(
            TrainingMutationResult.ValidationError("No previous set is available."),
            model.copyPreviousSet(target.id, setNumber = 1)
        )
    }

    @Test
    fun sessionFlowPropagatesActiveWorkoutLayoutMode() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(
                authenticatedUserId = "account-a",
                activeWorkoutLayoutMode = ActiveWorkoutLayoutMode.GRID
            )
        )
        val model = TrainingViewModel(runtime, this)
        runtime.trainingStates.emit(TrainingStateEntity("account-a", "{}", 1L))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        assertEquals(ActiveWorkoutLayoutMode.GRID, model.activeWorkoutLayoutMode)
    }

    @Test
    fun setActiveWorkoutLayoutModePersistsThroughRuntime() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val model = TrainingViewModel(runtime, this)
        runtime.trainingStates.emit(TrainingStateEntity("account-a", "{}", 1L))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        model.updateActiveWorkoutLayoutMode(ActiveWorkoutLayoutMode.GRID)
        withTimeout(5_000) {
            while (model.activeWorkoutLayoutMode != ActiveWorkoutLayoutMode.GRID) yield()
        }

        assertEquals(ActiveWorkoutLayoutMode.GRID, model.activeWorkoutLayoutMode)
        assertEquals(ActiveWorkoutLayoutMode.GRID, runtime.session.first().activeWorkoutLayoutMode)
    }

    @Test
    fun setActiveWorkoutLayoutModeSurfacesPersistenceFailure() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.layoutModeFailure = RuntimeException("layout save failed")
        val model = TrainingViewModel(runtime, this)
        runtime.trainingStates.emit(TrainingStateEntity("account-a", "{}", 1L))
        withTimeout(5_000) { while (!model.trainingMutationsReady) yield() }

        model.updateActiveWorkoutLayoutMode(ActiveWorkoutLayoutMode.GRID)
        withTimeout(5_000) { while (model.mutationError == null) yield() }

        assertEquals(ActiveWorkoutLayoutMode.LIST, model.activeWorkoutLayoutMode)
        assertEquals("layout save failed", model.mutationError)
    }

    @Test
    fun zeroEffortValueCanCompleteAWorkoutSet() {
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
            rpe = 0.0,
            completed = true
        )

        val savedSet = model.activeSetLogs.getValue(target.id).first()
        assertTrue(savedSet.completed)
        assertEquals(0.0, savedSet.rpe!!, 0.001)
        assertTrue(model.restTimerEndAtMillis != null)
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

    private fun <T> assertMutationRejectedBeforeNullAndStoredFirstPayload(
        snapshot: (TrainingViewModel) -> T,
        mutate: (TrainingViewModel) -> Unit
    ) {
        runBlocking {
            val firstPayloads = listOf(
                null,
                TrainingStateEntity("account-a", trainingPayload("Stored"), 1L)
            )
            firstPayloads.forEach { firstPayload ->
                val runtime = FakeTrainingViewModelRuntime(
                    session = SessionPreferences(authenticatedUserId = "account-a")
                )
                val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
                val before = snapshot(model)

                assertFalse(model.trainingMutationsReady)
                mutate(model)

                assertEquals(before, snapshot(model))
                assertTrue(runtime.savedPayloads.isEmpty())
                runtime.trainingStates.emit(firstPayload)
                withTimeout(5_000) {
                    while (!model.trainingMutationsReady) yield()
                }

                mutate(model)
                withTimeout(5_000) {
                    while (runtime.savedPayloads.isEmpty()) yield()
                }
                assertTrue(model.trainingMutationsReady)
            }
        }
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

    @Test
    fun restoringV2CanonicalTrainingStatePreservesItAcrossCurrentViewModelSave() = runBlocking {
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        val custom = Exercise(
            id = "exercise-00000000-0000-0000-0000-000000000010",
            name = "Custom carry",
            category = "Custom",
            primaryMuscles = "Grip",
            secondaryMuscles = "",
            instructions = "Carry steadily.",
            safetyNote = ""
        )
        val assigned = WorkoutTemplate(
            id = "assigned-template",
            name = "Assigned",
            exercises = listOf(ExerciseTarget("assigned-target", custom, sets = 3, reps = 12))
        )
        val unassigned = WorkoutTemplate("unassigned-template", "Unassigned")
        val plans = listOf(
            WeeklyDayPlan(java.time.DayOfWeek.MONDAY, listOf(assigned.id)),
            WeeklyDayPlan(java.time.DayOfWeek.WEDNESDAY, listOf(assigned.id)),
            WeeklyDayPlan(java.time.DayOfWeek.SATURDAY, isRestDay = true)
        )
        runtime.trainingStates.tryEmit(
            TrainingStateEntity(
                "account-a",
                encodeTrainingState(
                    customExercises = listOf(custom),
                    workoutTemplates = listOf(assigned, unassigned),
                    weeklyDayPlans = plans
                ),
                1L
            )
        )
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) {
            while (!model.trainingMutationsReady) yield()
        }

        model.updateDefaultRestTimerSeconds(120)
        withTimeout(5_000) {
            while (runtime.savedPayloads.isEmpty()) yield()
        }
        val restored = requireNotNull(decodeTrainingState(runtime.savedPayloads.last(), model.exerciseLibrary))

        assertEquals(listOf(custom), restored.customExercises)
        assertEquals(listOf(assigned, unassigned), restored.workoutTemplates)
        assertEquals(plans, restored.weeklyDayPlans)
    }
    @Test
    fun compatibilitySessionMutationsPersistCanonicalTemplatesWithoutLosingV2Data() = runBlocking {
        verifyCanonicalCompatibilityMutation(
            mutate = { model ->
                model.addSession("Added workout", java.time.DayOfWeek.FRIDAY)
            },
            assertMutation = { restored, model ->
                val addedId = requireNotNull(model.selectedSessionId)
                val added = restored.workoutTemplates.single { it.id == addedId }
                assertTrue(addedId.isTypedUuid("workout-"))
                assertTrue(added.isUserCreated)
                assertEquals("Added workout", added.name)
                assertEquals(
                    listOf(addedId),
                    restored.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.FRIDAY }.templateIds
                )
            }
        )

        verifyCanonicalCompatibilityMutation(
            mutate = { model ->
                model.addExerciseToSelectedSession(model.exerciseLibrary.first { it.id == "bench-press" })
            },
            assertMutation = { restored, _ ->
                assertTrue(restored.workoutTemplates.assignedTemplate().exercises.any { it.exercise.id == "bench-press" })
            }
        )

        verifyCanonicalCompatibilityMutation(
            mutate = { model ->
                model.removeExerciseFromSelectedSession("assigned-target")
            },
            assertMutation = { restored, _ ->
                assertTrue(restored.workoutTemplates.assignedTemplate().exercises.none { it.id == "assigned-target" })
            }
        )

        verifyCanonicalCompatibilityMutation(
            mutate = { model ->
                model.updateSelectedExercise(
                    targetId = "assigned-target",
                    sets = 5,
                    reps = 15,
                    weightKg = 37.5,
                    durationMinutes = null,
                    distanceKm = null
                )
            },
            assertMutation = { restored, _ ->
                val target = restored.workoutTemplates.assignedTemplate().exercises.single { it.id == "assigned-target" }
                assertEquals(5, target.sets)
                assertEquals(15, target.reps)
                assertEquals(37.5, target.weightKg!!, 0.001)
            }
        )

        verifyCanonicalCompatibilityMutation(
            mutate = { model ->
                model.decideSuggestion(SuggestionDecision.ACCEPTED, 55.0)
            },
            assertMutation = { restored, _ ->
                val target = restored.workoutTemplates.assignedTemplate().exercises.single { it.id == "lat-target" }
                assertEquals(55.0, target.weightKg!!, 0.001)
            }
        )
    }

    private suspend fun verifyCanonicalCompatibilityMutation(
        mutate: (TrainingViewModel) -> Unit,
        assertMutation: (PersistedTrainingState, TrainingViewModel) -> Unit
    ) {
        val fixture = canonicalCompatibilityFixture()
        val runtime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        runtime.trainingStates.tryEmit(TrainingStateEntity("account-a", fixture.payload, 1L))
        val model = TrainingViewModel(runtime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) {
            while (!model.trainingMutationsReady) yield()
        }

        mutate(model)
        withTimeout(5_000) {
            while (runtime.savedPayloads.isEmpty()) yield()
        }
        val savedPayload = runtime.savedPayloads.last()
        val restored = requireNotNull(decodeTrainingState(savedPayload, model.exerciseLibrary))
        val reloadRuntime = FakeTrainingViewModelRuntime(
            session = SessionPreferences(authenticatedUserId = "account-a")
        )
        reloadRuntime.trainingStates.tryEmit(TrainingStateEntity("account-a", savedPayload, 2L))
        val reloadedModel = TrainingViewModel(reloadRuntime, CoroutineScope(Dispatchers.Unconfined))
        withTimeout(5_000) {
            while (!reloadedModel.trainingMutationsReady) yield()
        }

        assertEquals(listOf(fixture.custom), restored.customExercises)
        assertEquals(restored.workoutTemplates, reloadedModel.workoutTemplates)
        assertEquals(restored.weeklyDayPlans, reloadedModel.weeklyDayPlans)
        assertEquals(fixture.unassigned, restored.workoutTemplates.single { it.id == fixture.unassigned.id })
        assertEquals(
            listOf(fixture.assigned.id),
            restored.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.MONDAY }.templateIds
        )
        assertEquals(
            listOf(fixture.assigned.id),
            restored.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.WEDNESDAY }.templateIds
        )
        assertEquals(
            WeeklyDayPlan(java.time.DayOfWeek.SATURDAY, isRestDay = true),
            restored.weeklyDayPlans.single { it.weekday == java.time.DayOfWeek.SATURDAY }
        )
        assertMutation(restored, reloadedModel)
    }

    private fun canonicalCompatibilityFixture(): CanonicalCompatibilityFixture {
        val custom = Exercise(
            id = "exercise-00000000-0000-0000-0000-000000000010",
            name = "Custom carry",
            category = "Custom",
            primaryMuscles = "Grip",
            secondaryMuscles = "",
            instructions = "Carry steadily.",
            safetyNote = ""
        )
        val latPulldown = builtInExerciseCatalog().first { it.id == "lat-pulldown" }
        val assigned = WorkoutTemplate(
            id = "assigned-template",
            name = "Assigned",
            exercises = listOf(
                ExerciseTarget("assigned-target", custom, sets = 3, reps = 12),
                ExerciseTarget("lat-target", latPulldown, sets = 4, reps = 10, weightKg = 42.5)
            )
        )
        val unassigned = WorkoutTemplate("unassigned-template", "Unassigned")
        val plans = listOf(
            WeeklyDayPlan(java.time.DayOfWeek.MONDAY, listOf(assigned.id)),
            WeeklyDayPlan(java.time.DayOfWeek.WEDNESDAY, listOf(assigned.id)),
            WeeklyDayPlan(java.time.DayOfWeek.SATURDAY, isRestDay = true)
        )
        return CanonicalCompatibilityFixture(
            custom = custom,
            assigned = assigned,
            unassigned = unassigned,
            payload = encodeTrainingState(
                selectedSessionId = assigned.id,
                customExercises = listOf(custom),
                workoutTemplates = listOf(assigned, unassigned),
                weeklyDayPlans = plans
            )
        )
    }

    private fun List<WorkoutTemplate>.assignedTemplate(): WorkoutTemplate =
        single { it.id == "assigned-template" }

    private data class CanonicalCompatibilityFixture(
        val custom: Exercise,
        val assigned: WorkoutTemplate,
        val unassigned: WorkoutTemplate,
        val payload: String
    )
    private fun customExercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        category = "Custom",
        primaryMuscles = "Core",
        secondaryMuscles = "",
        instructions = "",
        safetyNote = ""
    )

    private fun rollbackFixture(): RollbackFixture {
        val custom = customExercise(
            "exercise-00000000-0000-0000-0000-0000000000f1",
            "Rollback carry"
        )
        val target = ExerciseTarget(
            id = "rollback-target",
            exercise = custom,
            sets = 3,
            reps = 10
        )
        val template = WorkoutTemplate("rollback-template", "Rollback workout", listOf(target))
        val plan = WeeklyDayPlan(java.time.DayOfWeek.MONDAY, listOf(template.id))
        val override = TrainingScheduleOverride(
            sessionId = template.id,
            originalDate = LocalDate.of(2026, 8, 18),
            scheduledDate = LocalDate.of(2026, 8, 19)
        )
        val setLog = WorkoutSetLog(
            id = "rollback-set",
            targetId = target.id,
            exerciseId = custom.id,
            exerciseName = custom.name,
            setNumber = 1,
            reps = 10,
            completed = true
        )
        return RollbackFixture(
            template = template,
            payload = encodeTrainingState(
                selectedSessionId = template.id,
                activeWorkoutSessionId = template.id,
                completedExerciseIds = mapOf(target.id to true),
                scheduleOverrides = listOf(override),
                activeSetLogs = mapOf(target.id to listOf(setLog)),
                activeWorkoutStartedAtMillis = 1234L,
                customExercises = listOf(custom),
                workoutTemplates = listOf(template),
                weeklyDayPlans = listOf(plan)
            )
        )
    }

    private data class RollbackFixture(
        val template: WorkoutTemplate,
        val payload: String
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
        val trainingSchedules = mutableListOf<Pair<String, TrainingReminderSettingsEntity>>()
        val recoverySchedules = mutableListOf<Pair<String, ReminderSystem>>()
        val scheduleAttempts = mutableListOf<ReminderSystem>()
        var saveGate: CompletableDeferred<Unit>? = null
        val saveStarted = CompletableDeferred<Unit>()
        var scheduleGate: CompletableDeferred<Unit>? = null
        val scheduleStarted = CompletableDeferred<Unit>()
        private val saveControls = mutableListOf<SaveControl>()
        private var saveCallCount = 0
        var saveFailure: Throwable? = null
        var trainingScheduleFailure: Throwable? = null
        var supplementScheduleFailure: Throwable? = null
        var layoutModeFailure: Throwable? = null

        fun prepareSave(
            gate: CompletableDeferred<Unit>? = null,
            nonCancellable: Boolean = false,
            emitToTrainingStatesBeforeCompletion: Boolean = false,
            roomEmissionCompleted: CompletableDeferred<Unit>? = null,
            failure: Throwable? = null,
            completion: CompletableDeferred<Unit>? = null
        ): CompletableDeferred<Unit> {
            val started = CompletableDeferred<Unit>()
            saveControls += SaveControl(
                gate = gate,
                nonCancellable = nonCancellable,
                started = started,
                emitToTrainingStatesBeforeCompletion = emitToTrainingStatesBeforeCompletion,
                roomEmissionCompleted = roomEmissionCompleted,
                failure = failure,
                completion = completion
            )
            return started
        }

        override fun trainingState(userId: String): Flow<TrainingStateEntity?> =
            trainingStates.filter { it == null || it.userId == userId }

        override suspend fun currentUserId(): String? = sessionState.value.authenticatedUserId

        override suspend fun saveTrainingState(userId: String, payload: String) {
            require(sessionState.value.authenticatedUserId == userId)
            val control = saveControls.getOrNull(saveCallCount++)
            saveStarted.complete(Unit)
            control?.started?.complete(Unit)
            try {
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
                (control?.failure ?: saveFailure)?.let { throw it }
                savedPayloads += payload
            } finally {
                control?.completion?.complete(Unit)
            }
        }

        fun clearSaveHistory() {
            attemptedPayloads.clear()
            savedPayloads.clear()
        }

        fun selectAccount(accountId: String) {
            sessionState.value = SessionPreferences(authenticatedUserId = accountId)
        }

        override suspend fun currentTrainingReminderSettings(userId: String) =
            TrainingReminderSettingsEntity(userId = userId)

        override suspend fun currentSupplementReminderSettings(userId: String) =
            SupplementReminderSettingsEntity(userId = userId)

        override fun scheduleTraining(userId: String, settings: TrainingReminderSettingsEntity) {
            scheduleAttempts += ReminderSystem.TRAINING
            trainingScheduleFailure?.let { throw it }
            trainingSchedules += userId to settings
        }

        override suspend fun scheduleSupplement(
            userId: String,
            settings: SupplementReminderSettingsEntity
        ) {
            scheduleAttempts += ReminderSystem.SUPPLEMENTS
            supplementScheduleFailure?.let { throw it }
            scheduleStarted.complete(Unit)
            scheduleGate?.await()
            supplementSchedules += userId to settings
        }

        override suspend fun scheduleRecovery(userId: String, system: ReminderSystem) {
            recoverySchedules += userId to system
        }

        override suspend fun setActiveWorkoutLayoutMode(userId: String, mode: ActiveWorkoutLayoutMode) {
            layoutModeFailure?.let { throw it }
            sessionState.value = sessionState.value.copy(activeWorkoutLayoutMode = mode)
        }

        private data class SaveControl(
            val gate: CompletableDeferred<Unit>?,
            val nonCancellable: Boolean,
            val started: CompletableDeferred<Unit>,
            val emitToTrainingStatesBeforeCompletion: Boolean,
            val roomEmissionCompleted: CompletableDeferred<Unit>?,
            val failure: Throwable?,
            val completion: CompletableDeferred<Unit>?
        )
    }
}

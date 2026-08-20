package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogCoverageTest {
    private val existingStableIds = setOf(
        "lat-pulldown", "push-up", "goblet-squat", "easy-run", "freestyle-swim",
        "bench-press", "incline-dumbbell-press", "overhead-press", "dumbbell-shoulder-press",
        "cable-fly", "pec-deck", "triceps-pushdown", "overhead-triceps-extension",
        "barbell-curl", "dumbbell-curl", "hammer-curl", "seated-cable-row", "barbell-row",
        "one-arm-dumbbell-row", "assisted-pull-up", "deadlift", "romanian-deadlift", "hip-thrust",
        "leg-press", "back-squat", "front-squat", "bulgarian-split-squat", "walking-lunge",
        "leg-extension", "leg-curl", "standing-calf-raise", "cable-lateral-raise", "face-pull",
        "reverse-fly", "dumbbell-shrug", "farmer-carry", "kettlebell-swing", "landmine-press",
        "chest-supported-row", "hack-squat", "cable-glute-kickback", "hip-abduction", "hip-adduction",
        "cable-woodchop", "weighted-crunch", "back-extension", "machine-chest-press",
        "machine-shoulder-press", "bodyweight-squat", "plank", "side-plank", "glute-bridge",
        "reverse-lunge", "step-up", "bench-dip", "chin-up", "mountain-climber", "burpee", "dead-bug",
        "bird-dog", "single-leg-calf-raise", "wall-sit", "brisk-walk", "cycling", "rowing-ergometer",
        "elliptical", "stair-climber", "jump-rope", "interval-run", "indoor-march", "cat-cow",
        "thoracic-rotation", "hip-flexor-stretch", "hamstring-stretch", "calf-stretch", "ankle-rocks",
        "shoulder-circles", "band-shoulder-pass", "deep-squat-hold", "child-pose", "pistol-squat"
    )

    private val approvedCategories = setOf(
        "Free weights", "Machine", "Cable", "Bodyweight", "Calisthenics", "Cardio", "Mobility",
        "Rehabilitation", "Home"
    )
    private val addedStableIds = setOf(
        "dumbbell-floor-press",
        "dumbbell-pullover",
        "dumbbell-incline-fly",
        "dumbbell-front-raise",
        "dumbbell-lateral-raise",
        "dumbbell-rear-delt-row",
        "dumbbell-sumo-squat",
        "dumbbell-step-back-lunge",
        "dumbbell-romanian-deadlift",
        "dumbbell-split-squat",
        "barbell-good-morning",
        "barbell-hip-bridge",
        "barbell-reverse-lunge",
        "barbell-sumo-deadlift",
        "barbell-close-grip-press",
        "barbell-skull-crusher",
        "ez-bar-curl",
        "zottman-curl",
        "incline-dumbbell-curl",
        "dumbbell-wrist-curl",
        "dumbbell-reverse-wrist-curl",
        "kettlebell-clean",
        "kettlebell-snatch",
        "kettlebell-front-rack-carry",
        "kettlebell-turkish-get-up",
        "smith-machine-squat",
        "smith-machine-incline-press",
        "machine-row",
        "plate-loaded-row",
        "seated-leg-press",
        "lying-leg-curl",
        "seated-leg-curl",
        "reverse-hyperextension",
        "calf-press-machine",
        "seated-calf-raise",
        "machine-preacher-curl",
        "machine-lateral-raise",
        "glute-drive-machine",
        "back-extension-machine",
        "machine-dip",
        "machine-crunch",
        "machine-pullover",
        "machine-biceps-curl",
        "machine-triceps-extension",
        "machine-rear-delt-fly",
        "machine-shoulder-shrug",
        "machine-hip-thrust",
        "machine-abduction-kickback",
        "machine-adduction-press",
        "machine-rotary-torso",
        "cable-chest-press",
        "cable-incline-press",
        "cable-crossover",
        "cable-low-row",
        "cable-pullover",
        "cable-biceps-curl",
        "rope-hammer-curl",
        "cable-reverse-curl",
        "cable-upright-row",
        "cable-rear-delt-fly",
        "cable-front-raise",
        "cable-pull-through",
        "cable-hip-abduction",
        "cable-hip-adduction",
        "pallof-press",
        "cable-crunch",
        "cable-reverse-crunch",
        "cable-single-arm-row",
        "cable-high-row",
        "cable-straight-arm-pulldown",
        "cable-kneeling-lat-pulldown",
        "cable-shrug",
        "cable-reverse-fly",
        "cable-external-rotation",
        "cable-internal-rotation",
        "pull-up",
        "wide-grip-pull-up",
        "neutral-grip-pull-up",
        "tuck-planche-hold",
        "advanced-tuck-planche",
        "straddle-planche-progression",
        "planche-lean",
        "pike-push-up",
        "handstand-push-up",
        "wall-handstand-hold",
        "muscle-up-progression",
        "ring-row",
        "archer-push-up",
        "pseudo-planche-push-up",
        "l-sit",
        "hanging-leg-raise",
        "toes-to-bar",
        "skin-the-cat",
        "tuck-front-lever",
        "advanced-tuck-front-lever",
        "dragon-flag-progression",
        "human-flag-progression",
        "parallel-bar-dip",
        "typewriter-pull-up",
        "back-lever-tuck",
        "band-external-rotation",
        "band-internal-rotation",
        "shoulder-isometric",
        "rehab-wall-slide",
        "scapular-retraction",
        "prone-y-raise",
        "prone-t-raise",
        "prone-w-raise",
        "quad-set",
        "straight-leg-raise",
        "terminal-knee-extension",
        "heel-slide",
        "rehab-clamshell",
        "side-lying-hip-abduction",
        "rehab-bridge",
        "eccentric-calf-raise",
        "tibialis-raise",
        "ankle-eversion-band",
        "ankle-inversion-band",
        "single-leg-balance",
        "heel-toe-walk",
        "wrist-flexion-rehab",
        "wrist-extension-rehab",
        "median-nerve-glide",
        "pelvic-tilt",
        "chair-squat",
        "wall-push-up",
        "countertop-push-up",
        "doorway-row",
        "backpack-row",
        "backpack-deadlift",
        "backpack-overhead-press",
        "towel-biceps-curl",
        "chair-triceps-dip",
        "couch-split-squat",
        "stair-step-up",
        "towel-hamstring-curl",
        "standing-hip-hinge",
        "wall-calf-raise",
        "floor-snow-angel",
        "home-plank",
        "home-side-plank",
        "sit-up",
        "home-reverse-lunge"
    )

    private data class ExpectedExercise(
        val id: String,
        val primaryMuscles: String,
        val secondaryMuscles: String,
        val instructionTerms: List<String>,
        val safetyTerms: List<String>
    )

    private val reviewedAddedExercises = listOf(
        ExpectedExercise("dumbbell-wrist-curl", "Wrist flexors", "Finger flexors, forearm stabilizers", listOf("forearms", "palms", "curl", "wrist"), listOf("light", "wrist")),
        ExpectedExercise("dumbbell-reverse-wrist-curl", "Wrist extensors", "Brachioradialis, forearm stabilizers", listOf("forearms", "palms down", "knuckles"), listOf("light", "wrist")),
        ExpectedExercise("kettlebell-clean", "Glutes, hamstrings, quadriceps", "Shoulders, upper back, grip, core", listOf("hinge", "hips", "rack"), listOf("forearm", "wrist")),
        ExpectedExercise("kettlebell-turkish-get-up", "Shoulders, core", "Glutes, quadriceps, triceps, grip", listOf("lying", "elbow", "stand"), listOf("eyes", "bell", "shoulder")),
        ExpectedExercise("seated-leg-press", "Quadriceps, glutes", "Hamstrings, calves", listOf("back", "feet", "platform"), listOf("pelvis", "knees")),
        ExpectedExercise("lying-leg-curl", "Hamstrings", "Gastrocnemius", listOf("hips", "pad", "heels"), listOf("lower back", "jerk")),
        ExpectedExercise("seated-leg-curl", "Hamstrings", "Gastrocnemius", listOf("knee", "roller", "curl"), listOf("thigh pad", "backrest")),
        ExpectedExercise("calf-press-machine", "Gastrocnemius, soleus", "Foot intrinsic muscles", listOf("balls", "heels", "ankles"), listOf("knees", "bounce")),
        ExpectedExercise("seated-calf-raise", "Soleus", "Gastrocnemius, foot intrinsic muscles", listOf("balls", "heels", "pause"), listOf("knee pad", "bounce")),
        ExpectedExercise("machine-rear-delt-fly", "Rear deltoids", "Rhomboids, middle trapezius", listOf("chest pad", "arms", "back"), listOf("shoulder", "momentum")),
        ExpectedExercise("machine-adduction-press", "Hip adductors", "Core stabilizers", listOf("pads", "thighs", "together"), listOf("hip", "controlled")),
        ExpectedExercise("cable-pull-through", "Glutes, hamstrings", "Erector spinae, core", listOf("away", "rope", "hips"), listOf("spine", "hyperextend")),
        ExpectedExercise("pallof-press", "Transverse abdominis, obliques", "Glutes, shoulder stabilizers", listOf("sideways", "press", "rotation"), listOf("pelvis", "torso")),
        ExpectedExercise("cable-rear-delt-fly", "Rear deltoids", "Rhomboids, middle trapezius", listOf("handles", "arms", "apart"), listOf("shoulder", "ribs")),
        ExpectedExercise("cable-external-rotation", "Infraspinatus, teres minor", "Rear deltoids, scapular stabilizers", listOf("side-on", "towel", "outward"), listOf("pain-free", "torso")),
        ExpectedExercise("pull-up", "Latissimus dorsi, upper back", "Biceps, brachialis, forearms", listOf("bar", "shoulders", "chest"), listOf("swing", "drop")),
        ExpectedExercise("tuck-planche-hold", "Anterior deltoids, serratus anterior, core", "Triceps, chest, wrist flexors", listOf("hands", "knees", "hold"), listOf("wrists", "shoulders", "regression")),
        ExpectedExercise("hanging-leg-raise", "Rectus abdominis, hip flexors", "Obliques, grip, latissimus dorsi", listOf("hang", "pelvis", "legs"), listOf("swing", "back")),
        ExpectedExercise("band-external-rotation", "Infraspinatus, teres minor", "Rear deltoids, scapular stabilizers", listOf("elbow", "rotate", "band"), listOf("pain-free", "shoulder")),
        ExpectedExercise("shoulder-isometric", "Anterior deltoids", "Upper pectoralis major, rotator cuff", listOf("fist", "wall", "hold"), listOf("submaximal", "pain")),
        ExpectedExercise("rehab-wall-slide", "Serratus anterior, lower trapezius", "Rotator cuff", listOf("forearms", "wall", "ribs"), listOf("pain-free", "shrug")),
        ExpectedExercise("straight-leg-raise", "Quadriceps, hip flexors", "Abdominals", listOf("thigh", "knee", "heel"), listOf("back", "hip pain")),
        ExpectedExercise("heel-slide", "Hamstrings", "Quadriceps, hip flexors", listOf("heel", "buttocks", "knee"), listOf("pain-free", "pelvis")),
        ExpectedExercise("eccentric-calf-raise", "Gastrocnemius, soleus", "Tibialis posterior, fibularis muscles", listOf("rise", "one leg", "lower"), listOf("Achilles", "pain")),
        ExpectedExercise("tibialis-raise", "Tibialis anterior", "Toe extensors", listOf("heels", "toes", "shins"), listOf("lean", "ankle pain")),
        ExpectedExercise("median-nerve-glide", "Forearm flexors, thenar muscles", "Triceps, wrist extensors", listOf("elbow", "wrist", "outward"), listOf("tingling", "numbness")),
        ExpectedExercise("backpack-deadlift", "Glutes, hamstrings", "Erector spinae, quadriceps, core", listOf("backpack", "hips", "stand"), listOf("spine", "load")),
        ExpectedExercise("towel-hamstring-curl", "Hamstrings", "Glutes, calves", listOf("heels", "towel", "hips"), listOf("hamstring", "cramp")),
        ExpectedExercise("wall-calf-raise", "Gastrocnemius, soleus", "Foot and ankle stabilizers", listOf("wall", "heels", "rise"), listOf("ankles", "bounce")),
        ExpectedExercise("home-plank", "Rectus abdominis, transverse abdominis", "Obliques, glutes, shoulders", listOf("forearms", "head", "heels"), listOf("back", "shoulder")),
        ExpectedExercise("home-side-plank", "Obliques, quadratus lumborum", "Gluteus medius, shoulders", listOf("side", "forearm", "hold"), listOf("shoulder", "hips"))
    )


    @Test
    fun catalogHasAtLeast220UniqueStableExercises() {
        val catalog = builtInExerciseCatalog()

        assertTrue(catalog.size >= 220)
        assertEquals(catalog.size, catalog.map(Exercise::id).distinct().size)
        assertTrue(catalog.map(Exercise::id).containsAll(existingStableIds))
    }

    @Test
    fun requestedCalisthenicsExercisesAreSearchable() {
        val catalog = builtInExerciseCatalog()

        assertTrue(filterExercises(catalog, "pike push", "All").any { it.id == "pike-push-up" })
        assertTrue(filterExercises(catalog, "pull up", "All").any { it.id == "pull-up" })
        assertTrue(filterExercises(catalog, "planche", "All").size >= 4)
    }

    @Test
    fun catalogCoversEveryApprovedCategory() {
        val counts = builtInExerciseCatalog().groupingBy(Exercise::category).eachCount()

        assertEquals(approvedCategories, counts.keys)
        approvedCategories.forEach { category ->
            assertTrue("$category is sparse", counts.getOrDefault(category, 0) >= 10)
        }
    }

    @Test
    fun practicalSearchCoversEveryCategoryAndEverySearchableField() {
        val catalog = builtInExerciseCatalog()

        val categorySearches = mapOf(
            "Free weights" to ("Turkish get-up" to "kettlebell-turkish-get-up"),
            "Machine" to ("hip adductors" to "machine-adduction-press"),
            "Cable" to ("transverse abdominis" to "pallof-press"),
            "Bodyweight" to ("Pistol squat" to "pistol-squat"),
            "Calisthenics" to ("brachialis" to "pull-up"),
            "Cardio" to ("cardiovascular system" to "easy-run"),
            "Mobility" to ("ankles" to "ankle-rocks"),
            "Rehabilitation" to ("toe extensors" to "tibialis-raise"),
            "Home" to ("quadratus lumborum" to "home-side-plank")
        )
        categorySearches.forEach { (category, search) ->
            val (query, expectedId) = search
            assertTrue(
                "$category search for '$query' did not find $expectedId",
                filterExercises(catalog, query.uppercase(), category).any { it.id == expectedId }
            )
        }

        assertTrue(filterExercises(catalog, "Turkish get-up", "All").any { it.id == "kettlebell-turkish-get-up" })
        assertTrue(filterExercises(catalog, "Rehabilitation", "All").any { it.id == "heel-slide" })
        assertTrue(filterExercises(catalog, "Tibialis anterior", "All").any { it.id == "tibialis-raise" })
        assertTrue(filterExercises(catalog, "Toe extensors", "All").any { it.id == "tibialis-raise" })
    }

    @Test
    fun addedExerciseIdsAreStableAndIndependentFromDisplayNames() {
        val catalog = builtInExerciseCatalog()
        val newIds = catalog.map(Exercise::id).toSet() - existingStableIds

        assertEquals(addedStableIds, newIds)
        assertEquals("Pull up", catalog.first { it.id == "pull-up" }.name)
    }

    @Test
    fun citedAndRepresentativeAddedExercisesHaveReviewedSemantics() {
        val catalog = builtInExerciseCatalog().associateBy(Exercise::id)
        val expectedRepresentativesByCategory = mapOf(
            "Free weights" to setOf("dumbbell-wrist-curl", "kettlebell-clean", "kettlebell-turkish-get-up"),
            "Machine" to setOf("seated-leg-press", "machine-rear-delt-fly", "machine-adduction-press"),
            "Cable" to setOf("cable-pull-through", "pallof-press", "cable-rear-delt-fly"),
            "Calisthenics" to setOf("pull-up", "tuck-planche-hold", "hanging-leg-raise"),
            "Rehabilitation" to setOf("rehab-wall-slide", "heel-slide", "tibialis-raise"),
            "Home" to setOf("backpack-deadlift", "towel-hamstring-curl", "home-side-plank")
        )

        assertTrue(reviewedAddedExercises.map(ExpectedExercise::id).containsAll(expectedRepresentativesByCategory.values.flatten()))
        expectedRepresentativesByCategory.forEach { (expectedCategory, ids) ->
            ids.forEach { id ->
                assertEquals("$id representative category", expectedCategory, requireNotNull(catalog[id]).category)
            }
        }
        reviewedAddedExercises.forEach { expected ->
            val actual = requireNotNull(catalog[expected.id]) { "Missing ${expected.id}" }
            assertEquals("${expected.id} primary muscles", expected.primaryMuscles, actual.primaryMuscles)
            assertEquals("${expected.id} secondary muscles", expected.secondaryMuscles, actual.secondaryMuscles)
            expected.instructionTerms.forEach { term -> assertTrue("${expected.id} instructions must mention '$term'", actual.instructions.contains(term, ignoreCase = true)) }
            expected.safetyTerms.forEach { term -> assertTrue("${expected.id} safety note must mention '$term'", actual.safetyNote.contains(term, ignoreCase = true)) }
        }
    }

    @Test
    fun everyAddedRecordHasConcreteMetadataAndNoGenericGuidance() {
        val added = builtInExerciseCatalog().filter { it.id in addedStableIds }
        val forbiddenInstructions = setOf(
            "Set the shoulder blades, then press or push with control.",
            "Set the shoulders down, then pull the elbows through the movement.",
            "Keep the whole foot grounded while bending hips and knees.",
            "Hinge at the hips while keeping the spine long and braced.",
            "Keep the upper arm still and curl without momentum.",
            "Move through a slow, pain-free range with light resistance."
        )
        val forbiddenSafetyNotes = setOf(
            "Stop before control or joint position deteriorates.",
            "Keep shoulders comfortable and avoid locking out forcefully.",
            "Do not swing or shrug toward the ears.",
            "Track knees with toes and control the descent.",
            "Stop if the lower back rounds or pinches.",
            "Avoid swinging the torso to finish a repetition.",
            "Stop for sharp pain or increasing symptoms."
        )
        val genericNameLead = Regex("^(execute|perform|complete|do)\\s+", RegexOption.IGNORE_CASE)
        val genericNameTail = Regex("\\b(through (its|a|the) .{0,20}range|for .{0,20}repetitions?|with a .{0,20}repetition)\\b", RegexOption.IGNORE_CASE)

        assertEquals(addedStableIds.size, added.size)
        added.forEach { exercise ->
            assertTrue(exercise.primaryMuscles.isNotBlank())
            assertTrue(exercise.secondaryMuscles.isNotBlank())
            assertTrue(exercise.instructions.isNotBlank())
            assertTrue(exercise.safetyNote.isNotBlank())
            assertFalse(exercise.primaryMuscles.equals(exercise.category, ignoreCase = true))
            assertFalse(exercise.secondaryMuscles.equals(exercise.category, ignoreCase = true))
            assertFalse("${exercise.id} uses a known generic instruction", exercise.instructions in forbiddenInstructions)
            assertFalse("${exercise.id} uses a known generic safety note", exercise.safetyNote in forbiddenSafetyNotes)
            assertFalse("${exercise.id} starts with a generic command", genericNameLead.containsMatchIn(exercise.instructions))
            assertFalse("${exercise.id} is only its display name plus generic range/repetition wording", exercise.instructions.contains(exercise.name, ignoreCase = true) && genericNameTail.containsMatchIn(exercise.instructions))
        }
        assertEquals("Each added exercise needs its own setup/execution guidance", added.size, added.map { it.instructions.lowercase() }.distinct().size)
        assertEquals("Each added exercise needs its own safety guidance", added.size, added.map { it.safetyNote.lowercase() }.distinct().size)
    }

    @Test
    fun addedStaticExercisesAreDescribedAsHoldsRatherThanRepetitionsOrRanges() {
        val catalog = builtInExerciseCatalog().associateBy(Exercise::id)
        val staticIds = setOf(
            "tuck-planche-hold", "advanced-tuck-planche", "straddle-planche-progression", "planche-lean",
            "wall-handstand-hold", "l-sit", "tuck-front-lever", "advanced-tuck-front-lever",
            "human-flag-progression", "back-lever-tuck", "shoulder-isometric", "single-leg-balance",
            "home-plank", "home-side-plank"
        )

        staticIds.forEach { id ->
            val instructions = requireNotNull(catalog[id]).instructions
            assertTrue("$id must describe a hold", instructions.contains("hold", ignoreCase = true))
            assertFalse("$id must not describe repetitions", instructions.contains("repetition", ignoreCase = true))
            assertFalse("$id must not use generic range wording", instructions.contains("range", ignoreCase = true))
        }
    }

    @Test
    fun duplicateExerciseIdsFailCatalogConstruction() {
        val exercise = builtInExerciseCatalog().first()

        val failure = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            requireUniqueExerciseIds(listOf(exercise, exercise.copy(name = "Duplicate")))
        }
        assertTrue(failure.message.orEmpty().contains("duplicate IDs"))
    }
}
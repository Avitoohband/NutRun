package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
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

        approvedCategories.forEach { category ->
            assertTrue("$category is sparse", counts.getOrDefault(category, 0) >= 10)
        }
    }

    @Test
    fun practicalSearchFindsExercisesAcrossAllApprovedCategories() {
        val catalog = builtInExerciseCatalog()

        assertTrue(filterExercises(catalog, "lats", "Free weights").any { it.id == "dumbbell-pullover" })
        assertTrue(filterExercises(catalog, "quadriceps", "Machine").any { it.id == "smith-machine-squat" })
        assertTrue(filterExercises(catalog, "obliques", "Cable").any { it.id == "cable-woodchop" })
        assertTrue(filterExercises(catalog, "triceps", "Bodyweight").any { it.id == "push-up" })
        assertTrue(filterExercises(catalog, "lats", "Calisthenics").any { it.id == "pull-up" })
        assertTrue(filterExercises(catalog, "cardio", "Cardio").any { it.id == "easy-run" })
        assertTrue(filterExercises(catalog, "ankles", "Mobility").any { it.id == "ankle-rocks" })
        assertTrue(filterExercises(catalog, "rotator cuff", "Rehabilitation").any { it.id == "band-external-rotation" })
        assertTrue(filterExercises(catalog, "hamstrings", "Home").any { it.id == "backpack-deadlift" })
    }

    @Test
    fun addedExerciseIdsAreStableAndIndependentFromDisplayNames() {
        val catalog = builtInExerciseCatalog()
        val newIds = catalog.map(Exercise::id).toSet() - existingStableIds

        assertEquals(addedStableIds, newIds)
        assertEquals("Pull up", catalog.first { it.id == "pull-up" }.name)
    }
}
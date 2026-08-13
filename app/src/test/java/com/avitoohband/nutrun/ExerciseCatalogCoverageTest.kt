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
}

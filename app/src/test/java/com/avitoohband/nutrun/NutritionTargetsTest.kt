package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTargetsTest {
    @Test
    fun recommendedTargetsUseStandardMacroSplit() {
        val targets = recommendedNutritionTargets(2_000)
        assertEquals(125.0, targets.proteinGrams, 0.001)
        assertEquals(225.0, targets.carbohydrateGrams, 0.001)
        assertEquals(67.0, targets.fatGrams, 0.001)
        assertFalse(targets.custom)
    }

    @Test
    fun recommendedTargetsStayNonNegativeForZeroCalories() {
        val targets = recommendedNutritionTargets(0)
        assertEquals(0.0, targets.proteinGrams, 0.001)
        assertEquals(0.0, targets.carbohydrateGrams, 0.001)
        assertEquals(0.0, targets.fatGrams, 0.001)
        assertFalse(targets.custom)
    }

    @Test
    fun entityRoundTripPreservesCustomFlag() {
        val entity = NutritionTargets(
            proteinGrams = 140.0,
            carbohydrateGrams = 200.0,
            fatGrams = 70.0,
            custom = true
        ).toEntity("user-a")
        val restored = entity.toDomain()
        assertEquals(140.0, restored.proteinGrams, 0.001)
        assertEquals(200.0, restored.carbohydrateGrams, 0.001)
        assertEquals(70.0, restored.fatGrams, 0.001)
        assertTrue(restored.custom)
        assertEquals("user-a", entity.userId)
    }
}

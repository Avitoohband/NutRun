package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementConversionTest {
    @Test
    fun weightDisplaysInSelectedUnit() {
        assertEquals("10 kg", displayWeight(10.0, metric = true))
        assertEquals("22 lb", displayWeight(10.0, metric = false))
    }

    @Test
    fun distanceDisplaysInSelectedUnit() {
        assertEquals("5 km", displayDistance(5.0, metric = true))
        assertEquals("3.1 mi", displayDistance(5.0, metric = false))
    }

    @Test
    fun heightConvertsFromInchesToCm() {
        assertEquals(175.26, convertHeightInputToCm(69.0, metric = false), 0.01)
    }

    @Test
    fun weightConvertsFromPoundsToKg() {
        assertEquals(75.0, convertWeightInputToKg(165.35, metric = false), 0.1)
    }

    @Test
    fun unitSwitchPreservesCanonicalHeight() {
        val heightCm = 180.0
        val imperialDisplay = formatHeightForUnits(heightCm, metric = false)
        val reconverted = convertHeightInputToCm(imperialDisplay.toDouble(), metric = false)
        assertEquals(heightCm, reconverted, 0.1)
    }

    @Test
    fun unitSwitchPreservesCanonicalWeight() {
        val weightKg = 80.0
        val imperialDisplay = formatWeightForUnits(weightKg, metric = false)
        val reconverted = convertWeightInputToKg(imperialDisplay.toDouble(), metric = false)
        assertEquals(weightKg, reconverted, 0.1)
    }

    @Test
    fun weightRuleRejectsOutOfRangeValues() {
        val rule = FormValidationRules.weightRule(metric = true)
        assertNotNull(validateDecimalInput("501", rule).error)
        assertNull(validateDecimalInput("75", rule).error)
    }
}

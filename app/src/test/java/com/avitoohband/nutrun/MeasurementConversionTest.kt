package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
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
}

package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.WalkPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkRouteCameraFramingTest {
    @Test
    fun emptyRouteDoesNotRequestCameraMovement() {
        assertEquals(WalkRouteCameraFraming.None, walkRouteCameraFraming(emptyList()))
    }

    @Test
    fun singlePointRouteCentersOnThatPoint() {
        assertEquals(
            WalkRouteCameraFraming.Center(latitude = 31.7683, longitude = 35.2137),
            walkRouteCameraFraming(listOf(point(latitude = 31.7683, longitude = 35.2137)))
        )
    }

    @Test
    fun multiPointRouteUsesTheOuterBounds() {
        assertEquals(
            WalkRouteCameraFraming.Bounds(
                south = 31.767,
                west = 35.212,
                north = 31.769,
                east = 35.215
            ),
            walkRouteCameraFraming(
                listOf(
                    point(latitude = 31.768, longitude = 35.215),
                    point(latitude = 31.767, longitude = 35.212),
                    point(latitude = 31.769, longitude = 35.214)
                )
            )
        )
    }

    @Test
    fun antimeridianRouteUsesTheNarrowWrappingBounds() {
        assertEquals(
            WalkRouteCameraFraming.Bounds(
                south = 10.0,
                west = 179.0,
                north = 11.0,
                east = -179.0
            ),
            walkRouteCameraFraming(
                listOf(
                    point(latitude = 10.0, longitude = 179.0),
                    point(latitude = 11.0, longitude = -179.0)
                )
            )
        )
    }

    private fun point(latitude: Double, longitude: Double) = WalkPointEntity(
        id = "$latitude:$longitude",
        userId = "user-1",
        sessionId = "walk-1",
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        recordedAtMillis = 0L
    )
}

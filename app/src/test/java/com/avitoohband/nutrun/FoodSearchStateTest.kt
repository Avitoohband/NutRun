package com.avitoohband.nutrun

import com.avitoohband.nutrun.domain.FoodCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodSearchStateTest {
    @Test
    fun blankQueryReturnsIdle() {
        assertEquals(FoodSearchUiState.Idle, foodSearchStateForQuery(""))
        assertEquals(FoodSearchUiState.Idle, foodSearchStateForQuery("   "))
    }

    @Test
    fun nonBlankQueryReturnsLoadingWithNormalizedText() {
        assertEquals(FoodSearchUiState.Loading("oats"), foodSearchStateForQuery(" oats "))
    }

    @Test
    fun staleResultsAreSuppressed() {
        val items = listOf(
            FoodCatalogItem("1", "Oats", null, 80.0, 300, 10.0, 50.0, 6.0)
        )
        assertNull(resolveFoodSearchResult(activeGeneration = 2, requestGeneration = 1, query = "oats", items = items))
        assertEquals(
            FoodSearchUiState.Results("oats", items),
            resolveFoodSearchResult(activeGeneration = 2, requestGeneration = 2, query = "oats", items = items)
        )
    }

    @Test
    fun emptyResultsBecomeEmptyState() {
        assertEquals(
            FoodSearchUiState.Empty("oats"),
            resolveFoodSearchResult(activeGeneration = 1, requestGeneration = 1, query = "oats", items = emptyList())
        )
    }

    @Test
    fun staleErrorsAreSuppressed() {
        assertNull(
            resolveFoodSearchError(
                activeGeneration = 2,
                requestGeneration = 1,
                query = "oats",
                message = "offline"
            )
        )
        assertEquals(
            FoodSearchUiState.Error("oats", "offline"),
            resolveFoodSearchError(
                activeGeneration = 2,
                requestGeneration = 2,
                query = "oats",
                message = "offline"
            )
        )
    }
}

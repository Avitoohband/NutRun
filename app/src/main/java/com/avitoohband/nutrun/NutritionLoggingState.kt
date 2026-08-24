package com.avitoohband.nutrun

import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.FoodTemplateEntity
import com.avitoohband.nutrun.domain.FoodCatalogItem

sealed interface FoodSearchUiState {
    data object Idle : FoodSearchUiState
    data class Loading(val query: String) : FoodSearchUiState
    data class Results(val query: String, val items: List<FoodCatalogItem>) : FoodSearchUiState
    data class Empty(val query: String) : FoodSearchUiState
    data class Error(val query: String, val message: String) : FoodSearchUiState
}

enum class NutritionDeletionKind {
    FOOD,
    TEMPLATE
}

data class PendingNutritionDeletion(
    val id: String,
    val label: String,
    val kind: NutritionDeletionKind,
    val ownerUserId: String,
    val foodEntry: FoodLogEntity? = null,
    val template: FoodTemplateEntity? = null
)

fun foodSearchStateForQuery(query: String): FoodSearchUiState {
    val normalized = query.trim()
    return if (normalized.isEmpty()) {
        FoodSearchUiState.Idle
    } else {
        FoodSearchUiState.Loading(normalized)
    }
}

fun resolveFoodSearchResult(
    activeGeneration: Int,
    requestGeneration: Int,
    query: String,
    items: List<FoodCatalogItem>
): FoodSearchUiState? {
    if (requestGeneration != activeGeneration) return null
    return if (items.isEmpty()) {
        FoodSearchUiState.Empty(query)
    } else {
        FoodSearchUiState.Results(query, items)
    }
}

fun resolveFoodSearchError(
    activeGeneration: Int,
    requestGeneration: Int,
    query: String,
    message: String
): FoodSearchUiState? {
    if (requestGeneration != activeGeneration) return null
    return FoodSearchUiState.Error(query, message)
}

fun foodEntryVisible(entryId: String, pending: PendingNutritionDeletion?): Boolean =
    pending?.takeIf { it.kind == NutritionDeletionKind.FOOD }?.id != entryId

fun templateVisible(templateId: String, pending: PendingNutritionDeletion?): Boolean =
    pending?.takeIf { it.kind == NutritionDeletionKind.TEMPLATE }?.id != templateId

const val NUTRITION_DELETION_WINDOW_MS = 5_000L

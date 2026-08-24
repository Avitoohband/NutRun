package com.avitoohband.nutrun

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.FoodTemplateEntity
import com.avitoohband.nutrun.domain.EntitlementKind
import com.avitoohband.nutrun.domain.FoodCatalogItem
import com.avitoohband.nutrun.domain.MealType
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NutritionOverviewContent(
    state: NutRunUiState,
    foodSearchState: FoodSearchUiState,
    pendingDeletion: PendingNutritionDeletion?,
    onSearchFood: (String) -> Unit,
    onClearFoodSearch: () -> Unit,
    onSaveFood: (FoodCatalogItem, MealType, String?) -> Unit,
    onDuplicateFood: (String) -> Unit,
    onLogRecentFood: (FoodLogEntity) -> Unit,
    onSaveFavoriteFood: (FoodLogEntity) -> Unit,
    onSaveMealTemplate: (String, MealType) -> Unit,
    onLogFoodTemplate: (FoodTemplateEntity) -> Unit,
    onRequestFoodDeletion: (FoodLogEntity) -> Unit,
    onRequestTemplateDeletion: (FoodTemplateEntity) -> Unit,
    onUndoNutritionDeletion: () -> Unit,
    onAddWater: (Int) -> Unit,
    onSetQuickServingAndAddWater: (Int) -> Unit,
    onHydrationSettings: () -> Unit,
    onWaterAmounts: () -> Unit,
    onCreateFood: () -> Unit,
    onEditFood: (FoodLogEntity) -> Unit,
    onDraftFood: (FoodCatalogItem) -> Unit,
    onSaveMeal: (MealType) -> Unit,
    waterFocusRequest: Int = 0,
    foodFocusRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var quickAddExpanded by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val calorieTarget = state.profile?.calorieTarget ?: 0
    val targets = state.nutritionTargets
    val waterHeadingIndex = 3 + foodSearchListItemCount(foodSearchState)

    LaunchedEffect(pendingDeletion?.id) {
        val pending = pendingDeletion ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted ${pending.label}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoNutritionDeletion()
        }
    }

    LaunchedEffect(waterFocusRequest, waterHeadingIndex) {
        if (waterFocusRequest > 0) listState.animateScrollToItem(waterHeadingIndex)
    }
    LaunchedEffect(foodFocusRequest) {
        if (foodFocusRequest > 0) listState.animateScrollToItem(1)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nutrition", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.nutrition.calories} kcal | P ${state.nutrition.proteinGrams.roundToInt()} | " +
                                "C ${state.nutrition.carbohydrateGrams.roundToInt()} | " +
                                "F ${state.nutrition.fatGrams.roundToInt()}"
                        )
                    }
                    IconButton(onClick = onCreateFood) {
                        Icon(Icons.Default.Add, contentDescription = "Add food")
                    }
                }
            }
            item(key = "macro-progress") {
                NutritionMacroProgressCard(
                    calories = state.nutrition.calories,
                    calorieTarget = calorieTarget,
                    proteinGrams = state.nutrition.proteinGrams,
                    carbohydrateGrams = state.nutrition.carbohydrateGrams,
                    fatGrams = state.nutrition.fatGrams,
                    targets = targets
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearchFood(it)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("nutrition-search"),
                    label = { Text("Search food") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    query = ""
                                    onClearFoodSearch()
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true
                )
            }
            when (foodSearchState) {
                FoodSearchUiState.Idle -> Unit
                is FoodSearchUiState.Loading -> {
                    item(key = "search-loading") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("nutrition-search-loading"))
                    }
                }
                is FoodSearchUiState.Results -> {
                    items(foodSearchState.items, key = { "search:${it.id}" }) { result ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDraftFood(result) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(result.name, fontWeight = FontWeight.SemiBold)
                                    Text("${result.servingGrams.roundToInt()} g serving")
                                }
                                Text("${result.calories} kcal")
                            }
                        }
                    }
                }
                is FoodSearchUiState.Empty -> {
                    item(key = "search-empty") {
                        Text(
                            "No results for \"${foodSearchState.query}\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("nutrition-search-empty")
                        )
                    }
                }
                is FoodSearchUiState.Error -> {
                    item(key = "search-error") {
                        Column(modifier = Modifier.testTag("nutrition-search-error")) {
                            Text(foodSearchState.message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { onSearchFood(foodSearchState.query) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            item(key = "water-heading") {
                Row(
                    modifier = Modifier.testTag("water-section"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NutritionSectionHeading("Water", Modifier.weight(1f))
                    TextButton(onClick = onHydrationSettings) { Text("Settings") }
                }
            }
            item {
                Card(shape = RoundedCornerShape(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${state.waterMl} / ${state.hydrationPlan.goalMl} mL",
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = {
                                    (state.waterMl / state.hydrationPlan.goalMl.toFloat()).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = { onAddWater(state.hydrationPlan.servingMl) },
                                    onLongClick = onWaterAmounts
                                )
                                .testTag("nutrition-quick-add-water"),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "+${state.hydrationPlan.servingMl} mL",
                                Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = onWaterAmounts,
                            modifier = Modifier.testTag("nutrition-choose-water-amount")
                        ) {
                            Icon(Icons.Default.UnfoldMore, contentDescription = "Choose water amount")
                        }
                    }
                }
            }
            val visibleTemplates = state.foodTemplates.filter { templateVisible(it.id, pendingDeletion) }
            val recentFoods = state.recentFoods
                .distinctBy { "${it.catalogId}:${it.name}:${it.servingGrams}" }
                .take(5)
            if (visibleTemplates.isNotEmpty() || recentFoods.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NutritionSectionHeading("Quick add", Modifier.weight(1f))
                        IconButton(
                            onClick = { quickAddExpanded = !quickAddExpanded },
                            modifier = Modifier.testTag("nutrition-quick-add-toggle")
                        ) {
                            Icon(
                                if (quickAddExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (quickAddExpanded) {
                                    "Collapse quick add"
                                } else {
                                    "Expand quick add"
                                }
                            )
                        }
                    }
                }
            }
            if (quickAddExpanded) {
                items(visibleTemplates, key = { "template:${it.id}" }) { template ->
                    NutritionTemplateRow(
                        template = template,
                        onLog = { onLogFoodTemplate(template) },
                        onDelete = { onRequestTemplateDeletion(template) }
                    )
                }
                items(recentFoods, key = { "recent:${it.id}" }) { entry ->
                    Card(shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Recent | ${entry.calories} kcal",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onSaveFavoriteFood(entry) }) {
                                Icon(Icons.Default.Star, contentDescription = "Favorite ${entry.name}")
                            }
                            IconButton(onClick = { onLogRecentFood(entry) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add ${entry.name}")
                            }
                        }
                    }
                }
            }
            MealType.entries.forEach { meal ->
                val entries = state.food
                    .filter { it.mealType == meal.name }
                    .filter { foodEntryVisible(it.id, pendingDeletion) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NutritionSectionHeading(
                            meal.name.lowercase().replaceFirstChar(Char::uppercase),
                            Modifier.weight(1f)
                        )
                        if (entries.isNotEmpty()) {
                            TextButton(onClick = { onSaveMeal(meal) }) { Text("Save meal") }
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "meal-empty:${meal.name}") {
                        Text("Nothing logged", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    NutritionFoodLogRow(
                        entry = entry,
                        onEdit = { onEditFood(entry) },
                        onDuplicate = { onDuplicateFood(entry.id) },
                        onFavorite = { onSaveFavoriteFood(entry) },
                        onDelete = { onRequestFoodDeletion(entry) }
                    )
                }
            }
            if (state.session.entitlement() == EntitlementKind.FREE_AD_SUPPORTED) {
                item { AdPlacement() }
            }
        }
    }
}

@Composable
private fun NutritionMacroProgressCard(
    calories: Int,
    calorieTarget: Int,
    proteinGrams: Double,
    carbohydrateGrams: Double,
    fatGrams: Double,
    targets: NutritionTargets?
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("nutrition-macro-progress"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Daily progress", fontWeight = FontWeight.SemiBold)
            if (calorieTarget > 0) {
                Text("$calories / $calorieTarget kcal")
                LinearProgressIndicator(
                    progress = { (calories / calorieTarget.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("$calories kcal logged")
            }
            if (targets != null) {
                Text(
                    "Macro targets are general guidance",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NutritionMacroRow("Protein", proteinGrams, targets.proteinGrams)
                NutritionMacroRow("Carbs", carbohydrateGrams, targets.carbohydrateGrams)
                NutritionMacroRow("Fat", fatGrams, targets.fatGrams)
            }
        }
    }
}

@Composable
private fun NutritionMacroRow(label: String, logged: Double, target: Double) {
    val progress = if (target > 0) (logged / target).coerceIn(0.0, 1.0).toFloat() else 0f
    Column {
        Text(
            "$label ${logged.roundToInt()} / ${target.roundToInt()} g",
            fontSize = 13.sp
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NutritionTemplateRow(
    template: FoodTemplateEntity,
    onLog: () -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (template.kind == "FAVORITE") Icons.Default.Star else Icons.Default.LocalDining,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, fontWeight = FontWeight.SemiBold)
                Text(
                    if (template.kind == "FAVORITE") "Favorite food" else "Saved meal",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onLog) {
                Icon(Icons.Default.Add, contentDescription = "Add ${template.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${template.name}")
            }
        }
    }
}

@Composable
private fun NutritionFoodLogRow(
    entry: FoodLogEntity,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                Text("${entry.servingGrams.roundToInt()} g | ${entry.calories} kcal")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Food actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { menu = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to favorites") },
                        onClick = { menu = false; onFavorite() },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, fontWeight = FontWeight.Bold, fontSize = 19.sp)
}

private fun foodSearchListItemCount(state: FoodSearchUiState): Int = when (state) {
    FoodSearchUiState.Idle -> 0
    is FoodSearchUiState.Loading -> 1
    is FoodSearchUiState.Results -> state.items.size
    is FoodSearchUiState.Empty, is FoodSearchUiState.Error -> 1
}

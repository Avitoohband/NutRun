package com.avitoohband.nutrun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutRunScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val rootModifier = testTag?.let { modifier.testTag(it) } ?: modifier
    Scaffold(
        modifier = rootModifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics {
                                contentDescription = "Back from $title"
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        },
        content = content
    )
}

@Composable
fun NutRunMetric(
    value: String,
    label: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    testTag: String = "nutrun-metric"
) {
    Card(
        modifier = modifier
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$actionLabel, $value $label"
            }
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(NutRunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NutRunEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    testTag: String = "nutrun-empty-state",
    titleTestTag: String? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            Modifier.fillMaxWidth().padding(NutRunSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
        ) {
            val titleModifier = titleTestTag?.let { Modifier.testTag(it) } ?: Modifier
            Text(title, modifier = titleModifier, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

enum class NutRunMessageKind { ERROR, WARNING, INFO }

@Composable
fun NutRunInlineMessage(
    message: String,
    kind: NutRunMessageKind,
    modifier: Modifier = Modifier,
    testTag: String = "nutrun-inline-message"
) {
    val color = when (kind) {
        NutRunMessageKind.ERROR -> MaterialTheme.colorScheme.error
        NutRunMessageKind.WARNING -> NutRunSemanticColors.warning
        NutRunMessageKind.INFO -> NutRunSemanticColors.info
    }
    val live = kind == NutRunMessageKind.ERROR
    Text(
        message,
        modifier = modifier
            .testTag(testTag)
            .semantics {
                if (live) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun NutRunLoadingState(
    message: String,
    modifier: Modifier = Modifier,
    testTag: String = "nutrun-loading"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = message
            }
            .padding(NutRunSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun <T> NutRunChoiceRow(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        values.chunked(2).forEach { rowValues ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowValues.forEach { value ->
                    FilterChip(
                        selected = value == selected,
                        onClick = { onSelected(value) },
                        label = { Text(label(value), fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                    )
                }
            }
        }
    }
}

@Composable
fun NutRunSettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val taggedModifier = testTag?.let { modifier.testTag(it) } ?: modifier
    Card(
        modifier = taggedModifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(NutRunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            value?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

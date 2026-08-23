package com.avitoohband.nutrun

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NutRunTeal = Color(0xFF0B6E69)
private val NutRunTealDark = Color(0xFF084F4C)
private val NutRunTealLight = Color(0xFF3A9A94)
private val NutRunSurfaceDark = Color(0xFF121416)
private val NutRunSurfaceLight = Color(0xFFF7F8FA)
private val NutRunOnSurfaceMuted = Color(0xFF4F5B62)
private val NutRunSuccess = Color(0xFF2E7D4F)
private val NutRunInfo = Color(0xFF1F6FEB)
private val NutRunWarning = Color(0xFFB7791F)

object NutRunSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object NutRunSemanticColors {
    val success = NutRunSuccess
    val info = NutRunInfo
    val warning = NutRunWarning
}

private val NutRunShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

private val NutRunTypography = Typography()

private val NutRunLightColorScheme = lightColorScheme(
    primary = NutRunTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0EE),
    onPrimaryContainer = NutRunTealDark,
    secondary = NutRunInfo,
    onSecondary = Color.White,
    background = NutRunSurfaceLight,
    onBackground = Color(0xFF202426),
    surface = Color.White,
    onSurface = Color(0xFF202426),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = NutRunOnSurfaceMuted,
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val NutRunDarkColorScheme = darkColorScheme(
    primary = NutRunTealLight,
    onPrimary = Color(0xFF00201E),
    primaryContainer = NutRunTealDark,
    onPrimaryContainer = Color(0xFFB8E8E4),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF0B1D35),
    background = NutRunSurfaceDark,
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFB0B8C0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun NutRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NutRunDarkColorScheme else NutRunLightColorScheme,
        typography = NutRunTypography,
        shapes = NutRunShapes,
        content = content
    )
}

package org.mobiletriage.localverify.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFF44336),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color(0xFF1C1C1E),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF48484A),
    surfaceTint = Color(0xFFF44336),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6),
    outline = Color(0xFF747477),
    outlineVariant = Color(0xFFD1D1D6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF44336),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5C1D18),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFC7C7CC),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF5C1D18),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFC7C7CC),
    onTertiary = Color(0xFF1C1C1E),
    tertiaryContainer = Color(0xFF3A3A3C),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFF2F2F7),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFC7C7CC),
    surfaceTint = Color(0xFFF44336),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF3A3A3C),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF48484A),
)

@Composable
fun LocalVerifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DontFretDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = TextIceWhite,
    primaryContainer = StudioSurfaceElevated,
    onPrimaryContainer = ElectricBlueBright,
    secondary = DciViolet,
    onSecondary = TextIceWhite,
    secondaryContainer = StudioSurfaceHigh,
    onSecondaryContainer = TextIceWhite,
    tertiary = CyanHighlight,
    onTertiary = StudioBgDeep,
    background = StudioBgPrimary,
    onBackground = TextIceWhite,
    surface = StudioSurface,
    onSurface = TextIceWhite,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = DangerRose
)

@Composable
fun DontFretTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DontFretDarkColorScheme,
        typography = Typography,
        content = content
    )
}

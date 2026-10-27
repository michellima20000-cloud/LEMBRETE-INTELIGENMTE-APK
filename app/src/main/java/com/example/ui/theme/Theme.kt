package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LembreteColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyan,
    onSecondary = BgDark,
    tertiary = AccentTeal,
    onTertiary = TextPrimary,
    background = BgDark,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgCardLight,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = AccentRose,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LembreteColorScheme,
        typography = Typography,
        content = content
    )
}

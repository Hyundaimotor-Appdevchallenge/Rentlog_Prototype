package com.example.pleosrantlog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = RentLogColors.PrimaryButton,
    onPrimary = RentLogColors.OnPrimaryButton,
    background = RentLogColors.Background,
    onBackground = RentLogColors.TextPrimary,
    surface = RentLogColors.Surface,
    onSurface = RentLogColors.TextPrimary,
    surfaceVariant = RentLogColors.SurfaceHigh,
    onSurfaceVariant = RentLogColors.TextSecondary,
    outline = RentLogColors.Outline,
    error = RentLogColors.Emergency,
)

/** Always dark (in-vehicle). */
@Composable
fun PleosRantLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = Typography, content = content)
}

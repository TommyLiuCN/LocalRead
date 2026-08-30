package com.example.localread.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D6DE5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FB),
    onPrimaryContainer = Color(0xFF12295E),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDAE2F9),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE7E9EF),
    onSurfaceVariant = Color(0xFF656B76),
    outlineVariant = Color(0xFFDFE2EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C2FF),
    onPrimary = Color(0xFF10306B),
    primaryContainer = Color(0xFF2A478D),
    onPrimaryContainer = Color(0xFFDCE5FB),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E4E8),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFE3E4E8),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFF9CA2AD),
    outlineVariant = Color(0xFF3A3E46),
)

@Composable
fun LocalReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

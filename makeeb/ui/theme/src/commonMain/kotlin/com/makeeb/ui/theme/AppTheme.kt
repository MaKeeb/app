package com.makeeb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightAppColors = lightColorScheme(
    primary = Color(0xFF3F5EFB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF0B1A6B),
    background = Color(0xFFF6F7FA),
    surface = Color(0xFFF6F7FA),
    surfaceContainer = Color(0xFFECEEF3),
)

private val DarkAppColors = darkColorScheme(
    primary = Color(0xFF7B90FF),
    onPrimary = Color(0xFF0B0E22),
    primaryContainer = Color(0xFF26337F),
    onPrimaryContainer = Color(0xFFDDE2FF),
    background = Color(0xFF101217),
    surface = Color(0xFF101217),
    surfaceContainer = Color(0xFF1B1E25),
)

/** Material theme for the companion app (settings, onboarding). */
@Composable
fun MaKeebAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkAppColors else LightAppColors, content = content)
}

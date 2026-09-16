package com.quranwidget.hafalan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B4332)
private val GreenSoft = Color(0xFF2D6A4F)
private val Cream = Color(0xFFF8F4EC)
private val Ink = Color(0xFF102A1F)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Cream,
    secondary = GreenSoft,
    onSecondary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFFFBF5),
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D5B2),
    onPrimary = Green,
    secondary = Color(0xFF74C69D),
    onSecondary = Green,
    background = Color(0xFF0B1A14),
    onBackground = Cream,
    surface = Color(0xFF12261D),
    onSurface = Cream,
)

@Composable
fun HafalanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

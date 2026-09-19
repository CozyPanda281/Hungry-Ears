package com.hungryears.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentDeep,
    onPrimaryContainer = OnAccent,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkRaised2,
    onSurfaceVariant = Slate,
    outline = Outlines,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkRaised2,
    surfaceContainerHighest = InkRaised2,
)

private val LightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Day,
    primaryContainer = Accent,
    onPrimaryContainer = OnAccent,
    background = Day,
    onBackground = DayInk,
    surface = DaySurface,
    onSurface = DayInk,
    surfaceVariant = DayOutline,
    onSurfaceVariant = DayMuted,
    outline = DayOutline,
    surfaceContainer = DaySurface,
    surfaceContainerHigh = DaySurface,
    surfaceContainerHighest = DayOutline,
)

@Composable
fun HungryEarsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HungryEarsTypography,
        shapes = HungryEarsShapes,
        content = content,
    )
}
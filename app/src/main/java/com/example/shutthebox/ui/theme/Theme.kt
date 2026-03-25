package com.example.shutthebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary            = WoodMedium,
    onPrimary          = WoodHighlight,
    primaryContainer   = WoodContainer,
    onPrimaryContainer = WoodOnContainer,

    secondary          = WoodLight,
    onSecondary        = WoodHighlight,
    secondaryContainer = WoodContainer,
    onSecondaryContainer = WoodOnContainer,

    background         = WoodHighlight,
    onBackground       = WoodDark,

    surface            = WoodHighlight,
    onSurface          = WoodDark,
    surfaceVariant     = WoodContainer,
    onSurfaceVariant   = WoodOnContainer,

    error              = CedarRed,
    onError            = WoodHighlight,
    errorContainer     = CedarRedLight,
    onErrorContainer   = CedarRed,
)

private val DarkColorScheme = darkColorScheme(
    primary            = WoodLight,
    onPrimary          = WoodDark,
    primaryContainer   = WoodMedium,
    onPrimaryContainer = WoodHighlight,

    secondary          = WoodContainer,
    onSecondary        = WoodDark,
    secondaryContainer = WoodMedium,
    onSecondaryContainer = WoodHighlight,

    background         = WoodDark,
    onBackground       = WoodHighlight,

    surface            = WoodDark,
    onSurface          = WoodHighlight,
    surfaceVariant     = WoodMedium,
    onSurfaceVariant   = WoodContainer,

    error              = CedarRedLight,
    onError            = CedarRed,
    errorContainer     = CedarRed,
    onErrorContainer   = CedarRedLight,
)

@Composable
fun ShutTheBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

package com.jjrapps.constanza.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * Material 3 theme wrapper. Habit colours and any product-specific palette land alongside the
 * habit editor in work unit 6a — this scaffolds the theme boundary only.
 */
@Composable
fun ConstanzaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

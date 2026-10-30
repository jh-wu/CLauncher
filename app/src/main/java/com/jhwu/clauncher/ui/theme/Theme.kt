package com.jhwu.clauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val colors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B0B5),
)

@Composable
fun CLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

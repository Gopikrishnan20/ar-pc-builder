package com.arpcbuilder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StatusGreen = Color(0xFF00C853)
val StatusAmber = Color(0xFFFFB300)
val StatusRed = Color(0xFFFF5252)

/** Always dark: overlays sit on a live camera feed, where a light theme washes out. */
private val Scheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF00E5FF),
    tertiary = Color(0xFFFF4081),
    surface = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1E2128),
    background = Color(0xFF0E0F12),
)

@Composable
fun ARPCBuilderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

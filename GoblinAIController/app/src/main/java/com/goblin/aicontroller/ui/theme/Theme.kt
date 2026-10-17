package com.goblin.aicontroller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkGreen = Color(0xFF00E676)
val DangerRed = Color(0xFFFF5252)
val BackgroundBlack = Color(0xFF121212)
val SurfaceGray = Color(0xFF1E1E1E)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreen,
    error = DangerRed,
    background = BackgroundBlack,
    surface = SurfaceGray,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun GoblinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

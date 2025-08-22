package com.example.run_wearos.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Custom colors for running app
private val DarkColorPalette = Colors(
    primary = Color(0xFF00E676), // Energetic green
    primaryVariant = Color(0xFF00C853), // Darker green
    secondary = Color(0xFF2196F3), // Blue for secondary elements
    secondaryVariant = Color(0xFF1976D2), // Darker blue
    error = Color(0xFFF44336), // Red for errors
    onPrimary = Color(0xFF000000), // Black text on green
    onSecondary = Color(0xFFFFFFFF), // White text on blue
    onError = Color(0xFFFFFFFF), // White text on red
    onSurface = Color(0xFFFFFFFF), // White text on surface
    onBackground = Color(0xFFFFFFFF), // White text on background
    surface = Color(0xFF121212), // Dark surface
    background = Color(0xFF000000) // Pure black background
)

@Composable
fun RunwearOsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = DarkColorPalette,
        content = content
    )
}
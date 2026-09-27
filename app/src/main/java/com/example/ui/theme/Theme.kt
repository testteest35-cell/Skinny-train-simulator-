package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IronRailColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = Color(0xFF0B0F17),
    primaryContainer = Color(0xFF3B2806),
    onPrimaryContainer = AmberGlow,
    secondary = CyanTelemetry,
    onSecondary = Color(0xFF061B26),
    secondaryContainer = Color(0xFF103247),
    onSecondaryContainer = Color(0xFFB8E8FF),
    tertiary = SignalGreen,
    onTertiary = Color(0xFF042619),
    background = RailSlateDark,
    onBackground = SteelTextPrimary,
    surface = RailSurfaceDark,
    onSurface = SteelTextPrimary,
    surfaceVariant = RailCardDark,
    onSurfaceVariant = SteelTextSecondary,
    outline = RailBorderSteel,
    error = SignalRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IronRailColorScheme,
        typography = Typography,
        content = content
    )
}

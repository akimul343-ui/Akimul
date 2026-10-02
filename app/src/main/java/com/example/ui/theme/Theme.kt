package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = CyberViolet,
    onSecondary = Color(0xFF280068),
    secondaryContainer = Color(0xFF3F009B),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = CyberGreen,
    onTertiary = Color(0xFF003914),
    tertiaryContainer = Color(0xFF005320),
    onTertiaryContainer = Color(0xFF70FF8E),
    background = DarkBg,
    onBackground = Color(0xFFE1E7F5),
    surface = DarkSurface,
    onSurface = Color(0xFFE1E7F5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC0CCDF),
    outline = DarkBorder,
    error = CyberRed
)

private val LightColorScheme = lightColorScheme(
    primary = CyberCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAF3FD),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = CyberVioletDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = CyberGreenDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA5FBC0),
    onTertiaryContainer = Color(0xFF00210A),
    background = LightBg,
    onBackground = Color(0xFF101622),
    surface = LightSurface,
    onSurface = Color(0xFF101622),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF404B5D),
    outline = LightBorder,
    error = CyberRed
)

@Composable
fun RayCollectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep cybernetic theme consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

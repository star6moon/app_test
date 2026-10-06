package com.plantdex.app.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6A3F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB1F1BC),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF506352),
    secondaryContainer = Color(0xFFD3E8D3),
    tertiary = Color(0xFF3A6470),
    background = Color(0xFFF7FBF3),
    surface = Color(0xFFF7FBF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF96D5A2),
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF125129),
    onPrimaryContainer = Color(0xFFB1F1BC),
    secondary = Color(0xFFB7CCB8),
    secondaryContainer = Color(0xFF394B3B),
    tertiary = Color(0xFFA2CDDB),
    background = Color(0xFF101510),
    surface = Color(0xFF101510),
)

@Composable
fun PlantDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

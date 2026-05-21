package com.example.compass_app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    primaryContainer = DarkContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground
)

private fun schemeFromHue(hue: Float) = darkColorScheme(
    primary         = Color.hsl(hue,                    0.75f, 0.60f),
    secondary       = Color.hsl((hue + 120f) % 360f,   0.65f, 0.55f),
    tertiary        = Color.hsl((hue + 240f) % 360f,   0.70f, 0.75f),
    background      = Color.hsl(hue,                    0.20f, 0.10f),
    primaryContainer= Color.hsl(hue,                    0.25f, 0.07f),
)

@Composable
fun Compass_appTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    randomHue: Float? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        randomHue != null -> schemeFromHue(randomHue)
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
package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = MintySage,
    onPrimaryContainer = DarkForestText,
    secondary = SlateGreen,
    onSecondary = Color.White,
    background = CharcoalForest,
    onBackground = WarmSageIvory,
    surface = CharcoalForest,
    onSurface = WarmSageIvory,
    surfaceVariant = SlateGreen,
    onSurfaceVariant = WarmSageIvory,
    outline = SoftSageBorders
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = MintySage,
    onPrimaryContainer = DarkForestText,
    secondary = SlateGreen,
    onSecondary = Color.White,
    background = WarmSageIvory,
    onBackground = CharcoalForest,
    surface = Color.White,
    onSurface = CharcoalForest,
    surfaceVariant = SoftSageSurface,
    onSurfaceVariant = SlateGreen,
    outline = SoftSageBorders
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to force load the custom Professional Polish theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

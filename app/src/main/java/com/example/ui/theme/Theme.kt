package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ComicPrimary,
    secondary = ComicSecondary,
    tertiary = ComicTertiary,
    background = ComicBackgroundDark,
    surface = ComicSurfaceDark,
    surfaceVariant = ComicSurfaceVariantDark,
    outline = ComicOutlineDark,
    onBackground = ComicOnBackgroundDark,
    onSurface = ComicOnSurfaceDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ComicPrimary,
    secondary = ComicSecondary,
    tertiary = ComicTertiary,
    background = ComicBackgroundLight,
    surface = ComicSurfaceLight,
    surfaceVariant = ComicSurfaceVariantLight,
    outline = ComicOutlineLight,
    onBackground = ComicOnBackgroundLight,
    onSurface = ComicOnSurfaceLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default so our professional polish theme colors apply reliably
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

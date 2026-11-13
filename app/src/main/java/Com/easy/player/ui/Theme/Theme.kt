package com.maxter.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MaxterOrange = Color(0xFFFF6700)
val MaxterOrangeLight = Color(0xFFFF8533)
val AmoledBlack = Color(0xFF000000)
val CharcoalSurface = Color(0xFF121212)
val CardSurface = Color(0xFF1A1A1A)

private val AmoledColorScheme = darkColorScheme(
    primary = MaxterOrange,
    secondary = MaxterOrangeLight,
    background = AmoledBlack,
    surface = CharcoalSurface,
    surfaceVariant = CardSurface,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = MaxterOrange,
    secondary = MaxterOrangeLight,
    background = Color(0xFF0E0E10),
    surface = Color(0xFF18181C),
    surfaceVariant = Color(0xFF222228),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = MaxterOrange,
    secondary = MaxterOrangeLight,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212)
)

enum class AppThemeMode { AMOLED, DARK, LIGHT }

@Composable
fun MaxterPlayerTheme(
    themeMode: AppThemeMode = AppThemeMode.AMOLED,
    content: @Composable () -> Unit
) {
    val scheme = when (themeMode) {
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

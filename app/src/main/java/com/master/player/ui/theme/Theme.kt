package com.maxter.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MaxterOrange = Color(0xFFFF6700)
val MaxterOrangeLight = Color(0xFFFF8533)
val MaxterOrangeDim = Color(0x33FF6700)
val AmoledBlack = Color(0xFF000000)
val CharcoalSurface = Color(0xFF121214)
val CardSurface = Color(0xFF1A1A1E)
val ElevatedSurface = Color(0xFF242429)
val MutedText = Color(0xFFA0A0AB)

private val AmoledColorScheme = darkColorScheme(
    primary = MaxterOrange,
    secondary = MaxterOrangeLight,
    tertiary = MaxterOrangeLight,
    background = AmoledBlack,
    surface = CharcoalSurface,
    surfaceVariant = CardSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = MutedText
)

private val DarkColorScheme = darkColorScheme(
    primary = MaxterOrange,
    secondary = MaxterOrangeLight,
    tertiary = MaxterOrangeLight,
    background = Color(0xFF0E0E12),
    surface = Color(0xFF17171C),
    surfaceVariant = Color(0xFF22222A),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = MutedText
)

private val LightColorScheme = lightColorScheme(
    primary = MaxterOrange,
    secondary = MaxterOrangeLight,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFECEEF2),
    onPrimary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF4A4A52)
)

private val MaxterTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = 0.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

enum class AppThemeMode(val label: String) {
    AMOLED("AMOLED Pure Black"),
    DARK("Charcoal Dark"),
    LIGHT("Daylight Clean")
}

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
        typography = MaxterTypography,
        content = content
    )
}

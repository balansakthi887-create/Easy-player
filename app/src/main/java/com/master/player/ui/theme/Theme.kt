package com.maxter.player.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeBaseMode(val label: String) {
    AMOLED("AMOLED"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class ThemePreset(val label: String) {
    ORANGE_BLACK("Orange Black"),
    AMOLED_BLACK("AMOLED Black"),
    PURE_WHITE("Pure White"),
    OCEAN_BLUE("Ocean Blue"),
    NEON_GREEN("Neon Green"),
    PURPLE_GALAXY("Purple Galaxy"),
    CYBERPUNK("Cyberpunk"),
    RED_DARK("Red Dark"),
    PINK("Pink"),
    MIDNIGHT_BLUE("Midnight Blue"),
    CUSTOM("Custom")
}

enum class ThemeColorSlot(val label: String) {
    PRIMARY("Primary Colour"),
    SECONDARY("Secondary Colour"),
    ACCENT("Accent Colour"),
    BACKGROUND("Background Colour"),
    PLAYER_CONTROL("Player Control Colour"),
    NAVIGATION_BAR("Navigation Bar Colour"),
    BUTTON_ICON("Buttons & Icons"),
    PROGRESS_BAR("Progress Bar Colour"),
    TEXT("Text Colour"),
    HIGHLIGHT("Highlight Colour")
}

@Immutable
data class MaxterThemeConfig(
    val preset: ThemePreset = ThemePreset.ORANGE_BLACK,
    val baseMode: ThemeBaseMode = ThemeBaseMode.AMOLED,
    val primary: Color = Color(0xFFFF6700),
    val secondary: Color = Color(0xFFFF8C3B),
    val accent: Color = Color(0xFFFF6700),
    val background: Color = Color(0xFF000000),
    val playerControl: Color = Color(0xFFFF6700),
    val navigationBar: Color = Color(0xFF101014),
    val buttonIcon: Color = Color(0xFFFFFFFF),
    val progressBar: Color = Color(0xFFFF6700),
    val text: Color = Color(0xFFFFFFFF),
    val highlight: Color = Color(0xFFFF9E44)
) {
    val isLight: Boolean
        get() = background.luminance() > 0.45f

    val surface: Color
        get() = when {
            isLight -> Color(0xFFF3F4F8)
            baseMode == ThemeBaseMode.AMOLED -> Color(0xFF0D0D10)
            else -> background.blendWith(Color.White, 0.06f)
        }

    val cardSurface: Color
        get() = when {
            isLight -> Color(0xFFFFFFFF)
            baseMode == ThemeBaseMode.AMOLED -> Color(0xFF16161B)
            else -> background.blendWith(Color.White, 0.11f)
        }

    val mutedText: Color
        get() = if (isLight) Color(0xFF5A5D68) else Color(0xFFA0A4B0)

    val onPrimary: Color
        get() = if (primary.luminance() > 0.45f) Color.Black else Color.White

    fun getSlotColor(slot: ThemeColorSlot): Color = when (slot) {
        ThemeColorSlot.PRIMARY -> primary
        ThemeColorSlot.SECONDARY -> secondary
        ThemeColorSlot.ACCENT -> accent
        ThemeColorSlot.BACKGROUND -> background
        ThemeColorSlot.PLAYER_CONTROL -> playerControl
        ThemeColorSlot.NAVIGATION_BAR -> navigationBar
        ThemeColorSlot.BUTTON_ICON -> buttonIcon
        ThemeColorSlot.PROGRESS_BAR -> progressBar
        ThemeColorSlot.TEXT -> text
        ThemeColorSlot.HIGHLIGHT -> highlight
    }

    fun withSlotColor(slot: ThemeColorSlot, color: Color): MaxterThemeConfig {
        val updated = when (slot) {
            ThemeColorSlot.PRIMARY -> copy(primary = color)
            ThemeColorSlot.SECONDARY -> copy(secondary = color)
            ThemeColorSlot.ACCENT -> copy(accent = color)
            ThemeColorSlot.BACKGROUND -> copy(background = color)
            ThemeColorSlot.PLAYER_CONTROL -> copy(playerControl = color)
            ThemeColorSlot.NAVIGATION_BAR -> copy(navigationBar = color)
            ThemeColorSlot.BUTTON_ICON -> copy(buttonIcon = color)
            ThemeColorSlot.PROGRESS_BAR -> copy(progressBar = color)
            ThemeColorSlot.TEXT -> copy(text = color)
            ThemeColorSlot.HIGHLIGHT -> copy(highlight = color)
        }
        return updated.copy(preset = ThemePreset.CUSTOM)
    }

    fun withBaseMode(mode: ThemeBaseMode): MaxterThemeConfig {
        if (preset != ThemePreset.CUSTOM && preset != ThemePreset.PURE_WHITE && preset != ThemePreset.AMOLED_BLACK) {
            return fromPreset(preset, mode)
        }
        return when (mode) {
            ThemeBaseMode.AMOLED -> copy(
                baseMode = mode,
                background = Color(0xFF000000),
                navigationBar = Color(0xFF0D0D10),
                text = Color(0xFFFFFFFF),
                buttonIcon = Color(0xFFFFFFFF)
            )
            ThemeBaseMode.DARK -> copy(
                baseMode = mode,
                background = Color(0xFF0E1016),
                navigationBar = Color(0xFF171A22),
                text = Color(0xFFFFFFFF),
                buttonIcon = Color(0xFFFFFFFF)
            )
            ThemeBaseMode.LIGHT -> copy(
                baseMode = mode,
                background = Color(0xFFF6F7FB),
                navigationBar = Color(0xFFFFFFFF),
                text = Color(0xFF111318),
                buttonIcon = Color(0xFF1D2028)
            )
        }
    }

    companion object {
        fun fromPreset(
            preset: ThemePreset,
            requestedBaseMode: ThemeBaseMode? = null
        ): MaxterThemeConfig {
            val base = when (preset) {
                ThemePreset.ORANGE_BLACK -> MaxterThemeConfig(
                    preset = ThemePreset.ORANGE_BLACK,
                    baseMode = ThemeBaseMode.AMOLED,
                    primary = Color(0xFFFF6700),
                    secondary = Color(0xFFFF8C3B),
                    accent = Color(0xFFFF6700),
                    background = Color(0xFF000000),
                    playerControl = Color(0xFFFF6700),
                    navigationBar = Color(0xFF101014),
                    buttonIcon = Color(0xFFFFFFFF),
                    progressBar = Color(0xFFFF6700),
                    text = Color(0xFFFFFFFF),
                    highlight = Color(0xFFFFA048)
                )
                ThemePreset.AMOLED_BLACK -> MaxterThemeConfig(
                    preset = ThemePreset.AMOLED_BLACK,
                    baseMode = ThemeBaseMode.AMOLED,
                    primary = Color(0xFFE0E0E6),
                    secondary = Color(0xFF9EA2B0),
                    accent = Color(0xFFFFFFFF),
                    background = Color(0xFF000000),
                    playerControl = Color(0xFFFFFFFF),
                    navigationBar = Color(0xFF070709),
                    buttonIcon = Color(0xFFF2F2F7),
                    progressBar = Color(0xFFFFFFFF),
                    text = Color(0xFFFFFFFF),
                    highlight = Color(0xFFB5B8C4)
                )
                ThemePreset.PURE_WHITE -> MaxterThemeConfig(
                    preset = ThemePreset.PURE_WHITE,
                    baseMode = ThemeBaseMode.LIGHT,
                    primary = Color(0xFF1A73E8),
                    secondary = Color(0xFF4F93F5),
                    accent = Color(0xFF1A73E8),
                    background = Color(0xFFFFFFFF),
                    playerControl = Color(0xFF1A73E8),
                    navigationBar = Color(0xFFF5F7FA),
                    buttonIcon = Color(0xFF1A1C22),
                    progressBar = Color(0xFF1A73E8),
                    text = Color(0xFF121318),
                    highlight = Color(0xFF5AA1FF)
                )
                ThemePreset.OCEAN_BLUE -> MaxterThemeConfig(
                    preset = ThemePreset.OCEAN_BLUE,
                    baseMode = ThemeBaseMode.DARK,
                    primary = Color(0xFF00B4D8),
                    secondary = Color(0xFF48CAE4),
                    accent = Color(0xFF00D4FF),
                    background = Color(0xFF06131F),
                    playerControl = Color(0xFF00B4D8),
                    navigationBar = Color(0xFF0B1E30),
                    buttonIcon = Color(0xFFE6F7FC),
                    progressBar = Color(0xFF00D4FF),
                    text = Color(0xFFF0FAFF),
                    highlight = Color(0xFF90E0EF)
                )
                ThemePreset.NEON_GREEN -> MaxterThemeConfig(
                    preset = ThemePreset.NEON_GREEN,
                    baseMode = ThemeBaseMode.AMOLED,
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF69F0AE),
                    accent = Color(0xFF00FF88),
                    background = Color(0xFF020B06),
                    playerControl = Color(0xFF00E676),
                    navigationBar = Color(0xFF091710),
                    buttonIcon = Color(0xFFE8FFF2),
                    progressBar = Color(0xFF00FF88),
                    text = Color(0xFFF2FFF7),
                    highlight = Color(0xFF80FFBA)
                )
                ThemePreset.PURPLE_GALAXY -> MaxterThemeConfig(
                    preset = ThemePreset.PURPLE_GALAXY,
                    baseMode = ThemeBaseMode.DARK,
                    primary = Color(0xFF9D4EDD),
                    secondary = Color(0xFFC77DFF),
                    accent = Color(0xFFB565FF),
                    background = Color(0xFF0D0716),
                    playerControl = Color(0xFFB565FF),
                    navigationBar = Color(0xFF160D24),
                    buttonIcon = Color(0xFFF5EBFF),
                    progressBar = Color(0xFFC77DFF),
                    text = Color(0xFFFAF5FF),
                    highlight = Color(0xFFE0AAFF)
                )
                ThemePreset.CYBERPUNK -> MaxterThemeConfig(
                    preset = ThemePreset.CYBERPUNK,
                    baseMode = ThemeBaseMode.DARK,
                    primary = Color(0xFFFF007F),
                    secondary = Color(0xFF00F0FF),
                    accent = Color(0xFF00F0FF),
                    background = Color(0xFF0A0814),
                    playerControl = Color(0xFFFF007F),
                    navigationBar = Color(0xFF141026),
                    buttonIcon = Color(0xFFFDF2FF),
                    progressBar = Color(0xFF00F0FF),
                    text = Color(0xFFFFFFFF),
                    highlight = Color(0xFFFFE600)
                )
                ThemePreset.RED_DARK -> MaxterThemeConfig(
                    preset = ThemePreset.RED_DARK,
                    baseMode = ThemeBaseMode.AMOLED,
                    primary = Color(0xFFE53935),
                    secondary = Color(0xFFFF6F60),
                    accent = Color(0xFFFF3D3D),
                    background = Color(0xFF0A0304),
                    playerControl = Color(0xFFE53935),
                    navigationBar = Color(0xFF16080A),
                    buttonIcon = Color(0xFFFFF0F0),
                    progressBar = Color(0xFFFF3D3D),
                    text = Color(0xFFFFFFFF),
                    highlight = Color(0xFFFF8A80)
                )
                ThemePreset.PINK -> MaxterThemeConfig(
                    preset = ThemePreset.PINK,
                    baseMode = ThemeBaseMode.DARK,
                    primary = Color(0xFFFF4D8D),
                    secondary = Color(0xFFFF85B3),
                    accent = Color(0xFFFF5E99),
                    background = Color(0xFF12080E),
                    playerControl = Color(0xFFFF4D8D),
                    navigationBar = Color(0xFF1D0E17),
                    buttonIcon = Color(0xFFFFF0F6),
                    progressBar = Color(0xFFFF5E99),
                    text = Color(0xFFFFFFFF),
                    highlight = Color(0xFFFFB3CE)
                )
                ThemePreset.MIDNIGHT_BLUE -> MaxterThemeConfig(
                    preset = ThemePreset.MIDNIGHT_BLUE,
                    baseMode = ThemeBaseMode.DARK,
                    primary = Color(0xFF4C8BFF),
                    secondary = Color(0xFF7BA9FF),
                    accent = Color(0xFF5C9BFF),
                    background = Color(0xFF080E1E),
                    playerControl = Color(0xFF4C8BFF),
                    navigationBar = Color(0xFF0F1830),
                    buttonIcon = Color(0xFFEBF2FF),
                    progressBar = Color(0xFF5C9BFF),
                    text = Color(0xFFF4F7FF),
                    highlight = Color(0xFFA3C4FF)
                )
                ThemePreset.CUSTOM -> MaxterThemeConfig(preset = ThemePreset.CUSTOM)
            }

            return if (requestedBaseMode != null && requestedBaseMode != base.baseMode && preset != ThemePreset.PURE_WHITE) {
                when (requestedBaseMode) {
                    ThemeBaseMode.AMOLED -> base.copy(
                        baseMode = ThemeBaseMode.AMOLED,
                        background = Color(0xFF000000),
                        navigationBar = Color(0xFF0D0D10),
                        text = Color(0xFFFFFFFF),
                        buttonIcon = Color(0xFFFFFFFF)
                    )
                    ThemeBaseMode.DARK -> base.copy(
                        baseMode = ThemeBaseMode.DARK,
                        background = if (base.isLight) Color(0xFF0E1016) else base.background,
                        navigationBar = if (base.isLight) Color(0xFF171A22) else base.navigationBar,
                        text = Color(0xFFFFFFFF),
                        buttonIcon = Color(0xFFFFFFFF)
                    )
                    ThemeBaseMode.LIGHT -> base.copy(
                        baseMode = ThemeBaseMode.LIGHT,
                        background = Color(0xFFF6F7FB),
                        navigationBar = Color(0xFFFFFFFF),
                        text = Color(0xFF121318),
                        buttonIcon = Color(0xFF1E2028)
                    )
                }
            } else {
                base
            }
        }
    }
}

private fun Color.blendWith(other: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = red * inv + other.red * ratio,
        green = green * inv + other.green * ratio,
        blue = blue * inv + other.blue * ratio,
        alpha = 1f
    )
}

val LocalMaxterTheme = staticCompositionLocalOf { MaxterThemeConfig() }

object MaxterTheme {
    val colors: MaxterThemeConfig
        @Composable get() = LocalMaxterTheme.current
}

private val MaxterTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = 0.4.sp
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
        letterSpacing = 0.35.sp
    )
)

@Composable
fun MaxterPlayerTheme(
    config: MaxterThemeConfig = MaxterThemeConfig(),
    content: @Composable () -> Unit
) {
    val scheme = if (config.isLight) {
        lightColorScheme(
            primary = config.primary,
            secondary = config.secondary,
            tertiary = config.accent,
            background = config.background,
            surface = config.surface,
            surfaceVariant = config.cardSurface,
            onPrimary = config.onPrimary,
            onSecondary = config.onPrimary,
            onBackground = config.text,
            onSurface = config.text,
            onSurfaceVariant = config.mutedText
        )
    } else {
        darkColorScheme(
            primary = config.primary,
            secondary = config.secondary,
            tertiary = config.accent,
            background = config.background,
            surface = config.surface,
            surfaceVariant = config.cardSurface,
            onPrimary = config.onPrimary,
            onSecondary = config.onPrimary,
            onBackground = config.text,
            onSurface = config.text,
            onSurfaceVariant = config.mutedText
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = config.background.toArgb()
            window.navigationBarColor = config.navigationBar.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = config.isLight
            controller.isAppearanceLightNavigationBars = config.isLight
        }
    }

    CompositionLocalProvider(LocalMaxterTheme provides config) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaxterTypography,
            content = content
        )
    }
}

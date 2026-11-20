package com.maxter.player.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.maxter.player.data.model.DecoderMode
import com.maxter.player.data.model.SmartEnhanceConfig
import com.maxter.player.data.model.SmartEnhanceLevel
import com.maxter.player.data.model.SmartEnhancePreset
import com.maxter.player.data.model.SortOption
import com.maxter.player.ui.theme.MaxterThemeConfig
import com.maxter.player.ui.theme.ThemeBaseMode
import com.maxter.player.ui.theme.ThemePreset

data class GestureSettings(
    val gesturesEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    val seekGestureEnabled: Boolean = true,
    val doubleTapSeekEnabled: Boolean = true,
    val doubleTapSeekSeconds: Int = 10,
    val longPressSpeedEnabled: Boolean = true,
    val longPressSpeed: Float = 2.0f
)

class MaxterPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("maxter_player_prefs_v2", Context.MODE_PRIVATE)

    fun loadThemeConfig(): MaxterThemeConfig {
        val default = MaxterThemeConfig.fromPreset(ThemePreset.ORANGE_BLACK, ThemeBaseMode.AMOLED)
        val presetName = prefs.getString(KEY_THEME_PRESET, default.preset.name) ?: default.preset.name
        val baseModeName = prefs.getString(KEY_THEME_BASE_MODE, default.baseMode.name) ?: default.baseMode.name
        val preset = runCatching { ThemePreset.valueOf(presetName) }.getOrDefault(ThemePreset.ORANGE_BLACK)
        val baseMode = runCatching { ThemeBaseMode.valueOf(baseModeName) }.getOrDefault(ThemeBaseMode.AMOLED)

        val base = MaxterThemeConfig.fromPreset(preset, baseMode)
        if (preset != ThemePreset.CUSTOM) {
            return base
        }

        return base.copy(
            primary = Color(prefs.getInt(KEY_COLOR_PRIMARY, base.primary.toArgb())),
            secondary = Color(prefs.getInt(KEY_COLOR_SECONDARY, base.secondary.toArgb())),
            accent = Color(prefs.getInt(KEY_COLOR_ACCENT, base.accent.toArgb())),
            background = Color(prefs.getInt(KEY_COLOR_BACKGROUND, base.background.toArgb())),
            playerControl = Color(prefs.getInt(KEY_COLOR_PLAYER_CONTROL, base.playerControl.toArgb())),
            navigationBar = Color(prefs.getInt(KEY_COLOR_NAV_BAR, base.navigationBar.toArgb())),
            buttonIcon = Color(prefs.getInt(KEY_COLOR_BUTTON_ICON, base.buttonIcon.toArgb())),
            progressBar = Color(prefs.getInt(KEY_COLOR_PROGRESS_BAR, base.progressBar.toArgb())),
            text = Color(prefs.getInt(KEY_COLOR_TEXT, base.text.toArgb())),
            highlight = Color(prefs.getInt(KEY_COLOR_HIGHLIGHT, base.highlight.toArgb()))
        )
    }

    fun saveThemeConfig(config: MaxterThemeConfig) {
        prefs.edit()
            .putString(KEY_THEME_PRESET, config.preset.name)
            .putString(KEY_THEME_BASE_MODE, config.baseMode.name)
            .putInt(KEY_COLOR_PRIMARY, config.primary.toArgb())
            .putInt(KEY_COLOR_SECONDARY, config.secondary.toArgb())
            .putInt(KEY_COLOR_ACCENT, config.accent.toArgb())
            .putInt(KEY_COLOR_BACKGROUND, config.background.toArgb())
            .putInt(KEY_COLOR_PLAYER_CONTROL, config.playerControl.toArgb())
            .putInt(KEY_COLOR_NAV_BAR, config.navigationBar.toArgb())
            .putInt(KEY_COLOR_BUTTON_ICON, config.buttonIcon.toArgb())
            .putInt(KEY_COLOR_PROGRESS_BAR, config.progressBar.toArgb())
            .putInt(KEY_COLOR_TEXT, config.text.toArgb())
            .putInt(KEY_COLOR_HIGHLIGHT, config.highlight.toArgb())
            .apply()
    }

    fun loadSmartEnhanceConfig(): SmartEnhanceConfig {
        val levelName = prefs.getString(KEY_SE_LEVEL, SmartEnhanceLevel.OFF.name) ?: SmartEnhanceLevel.OFF.name
        val presetName = prefs.getString(KEY_SE_PRESET, SmartEnhancePreset.AUTO.name) ?: SmartEnhancePreset.AUTO.name
        val level = runCatching { SmartEnhanceLevel.valueOf(levelName) }.getOrDefault(SmartEnhanceLevel.OFF)
        val preset = runCatching { SmartEnhancePreset.valueOf(presetName) }.getOrDefault(SmartEnhancePreset.AUTO)

        return SmartEnhanceConfig(
            enabled = prefs.getBoolean(KEY_SE_ENABLED, false),
            level = level,
            isAutoMode = preset == SmartEnhancePreset.AUTO,
            preset = preset,
            splitComparisonActive = false,
            splitPosition = prefs.getFloat(KEY_SE_SPLIT_POS, 0.5f).coerceIn(0.1f, 0.9f),
            intensity = prefs.getFloat(KEY_SE_INTENSITY, 0.72f).coerceIn(0f, 1f),
            brightness = prefs.getFloat(KEY_SE_BRIGHTNESS, preset.defaultBrightness).coerceIn(-0.4f, 0.4f),
            contrast = prefs.getFloat(KEY_SE_CONTRAST, preset.defaultContrast).coerceIn(0.6f, 1.8f),
            sharpness = prefs.getFloat(KEY_SE_SHARPNESS, preset.defaultSharpness).coerceIn(0f, 1.5f),
            saturation = prefs.getFloat(KEY_SE_SATURATION, preset.defaultSaturation).coerceIn(0.5f, 1.8f),
            denoise = prefs.getFloat(KEY_SE_DENOISE, preset.defaultDenoise).coerceIn(0f, 1f)
        )
    }

    fun saveSmartEnhanceConfig(config: SmartEnhanceConfig) {
        prefs.edit()
            .putBoolean(KEY_SE_ENABLED, config.enabled)
            .putString(KEY_SE_LEVEL, config.level.name)
            .putString(KEY_SE_PRESET, config.preset.name)
            .putFloat(KEY_SE_SPLIT_POS, config.splitPosition)
            .putFloat(KEY_SE_INTENSITY, config.intensity)
            .putFloat(KEY_SE_BRIGHTNESS, config.brightness)
            .putFloat(KEY_SE_CONTRAST, config.contrast)
            .putFloat(KEY_SE_SHARPNESS, config.sharpness)
            .putFloat(KEY_SE_SATURATION, config.saturation)
            .putFloat(KEY_SE_DENOISE, config.denoise)
            .apply()
    }

    fun loadDecoderMode(): DecoderMode {
        val raw = prefs.getString(KEY_DECODER_MODE, DecoderMode.HW_PLUS.name) ?: DecoderMode.HW_PLUS.name
        return runCatching { DecoderMode.valueOf(raw) }.getOrDefault(DecoderMode.HW_PLUS)
    }

    fun saveDecoderMode(mode: DecoderMode) {
        prefs.edit().putString(KEY_DECODER_MODE, mode.name).apply()
    }

    fun loadAutoResume(): Boolean = prefs.getBoolean(KEY_AUTO_RESUME, true)
    fun saveAutoResume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    fun loadNightMode(): Boolean = prefs.getBoolean(KEY_NIGHT_MODE, false)
    fun saveNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
    }

    fun loadGridLayout(): Boolean = prefs.getBoolean(KEY_GRID_LAYOUT, false)
    fun saveGridLayout(isGrid: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_LAYOUT, isGrid).apply()
    }

    fun loadSortOption(): SortOption {
        val raw = prefs.getString(KEY_SORT_OPTION, SortOption.DATE_DESC.name) ?: SortOption.DATE_DESC.name
        return runCatching { SortOption.valueOf(raw) }.getOrDefault(SortOption.DATE_DESC)
    }

    fun saveSortOption(sort: SortOption) {
        prefs.edit().putString(KEY_SORT_OPTION, sort.name).apply()
    }

    fun loadGestureSettings(): GestureSettings {
        return GestureSettings(
            gesturesEnabled = prefs.getBoolean(KEY_GESTURES_ENABLED, true),
            volumeGestureEnabled = prefs.getBoolean(KEY_GESTURE_VOLUME, true),
            brightnessGestureEnabled = prefs.getBoolean(KEY_GESTURE_BRIGHTNESS, true),
            seekGestureEnabled = prefs.getBoolean(KEY_GESTURE_SEEK, true),
            doubleTapSeekEnabled = prefs.getBoolean(KEY_GESTURE_DOUBLE_TAP, true),
            doubleTapSeekSeconds = prefs.getInt(KEY_DOUBLE_TAP_SECONDS, 10).coerceIn(5, 30),
            longPressSpeedEnabled = prefs.getBoolean(KEY_GESTURE_LONG_PRESS, true),
            longPressSpeed = prefs.getFloat(KEY_LONG_PRESS_SPEED, 2.0f).coerceIn(1.25f, 3.0f)
        )
    }

    fun saveGestureSettings(settings: GestureSettings) {
        prefs.edit()
            .putBoolean(KEY_GESTURES_ENABLED, settings.gesturesEnabled)
            .putBoolean(KEY_GESTURE_VOLUME, settings.volumeGestureEnabled)
            .putBoolean(KEY_GESTURE_BRIGHTNESS, settings.brightnessGestureEnabled)
            .putBoolean(KEY_GESTURE_SEEK, settings.seekGestureEnabled)
            .putBoolean(KEY_GESTURE_DOUBLE_TAP, settings.doubleTapSeekEnabled)
            .putInt(KEY_DOUBLE_TAP_SECONDS, settings.doubleTapSeekSeconds)
            .putBoolean(KEY_GESTURE_LONG_PRESS, settings.longPressSpeedEnabled)
            .putFloat(KEY_LONG_PRESS_SPEED, settings.longPressSpeed)
            .apply()
    }

    companion object {
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_THEME_BASE_MODE = "theme_base_mode"
        private const val KEY_COLOR_PRIMARY = "color_primary"
        private const val KEY_COLOR_SECONDARY = "color_secondary"
        private const val KEY_COLOR_ACCENT = "color_accent"
        private const val KEY_COLOR_BACKGROUND = "color_background"
        private const val KEY_COLOR_PLAYER_CONTROL = "color_player_control"
        private const val KEY_COLOR_NAV_BAR = "color_nav_bar"
        private const val KEY_COLOR_BUTTON_ICON = "color_button_icon"
        private const val KEY_COLOR_PROGRESS_BAR = "color_progress_bar"
        private const val KEY_COLOR_TEXT = "color_text"
        private const val KEY_COLOR_HIGHLIGHT = "color_highlight"

        private const val KEY_SE_ENABLED = "se_enabled"
        private const val KEY_SE_LEVEL = "se_level"
        private const val KEY_SE_PRESET = "se_preset"
        private const val KEY_SE_SPLIT_POS = "se_split_pos"
        private const val KEY_SE_INTENSITY = "se_intensity"
        private const val KEY_SE_BRIGHTNESS = "se_brightness"
        private const val KEY_SE_CONTRAST = "se_contrast"
        private const val KEY_SE_SHARPNESS = "se_sharpness"
        private const val KEY_SE_SATURATION = "se_saturation"
        private const val KEY_SE_DENOISE = "se_denoise"

        private const val KEY_DECODER_MODE = "decoder_mode"
        private const val KEY_AUTO_RESUME = "auto_resume"
        private const val KEY_NIGHT_MODE = "night_mode"
        private const val KEY_GRID_LAYOUT = "grid_layout"
        private const val KEY_SORT_OPTION = "sort_option"

        private const val KEY_GESTURES_ENABLED = "gestures_enabled"
        private const val KEY_GESTURE_VOLUME = "gesture_volume"
        private const val KEY_GESTURE_BRIGHTNESS = "gesture_brightness"
        private const val KEY_GESTURE_SEEK = "gesture_seek"
        private const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap"
        private const val KEY_DOUBLE_TAP_SECONDS = "double_tap_seconds"
        private const val KEY_GESTURE_LONG_PRESS = "gesture_long_press"
        private const val KEY_LONG_PRESS_SPEED = "long_press_speed"
    }
}

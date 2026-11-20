package com.maxter.player.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun ThemeAppearanceStudioSection(
    themeConfig: MaxterThemeConfig,
    onSelectPreset: (ThemePreset) -> Unit,
    onSelectBaseMode: (ThemeBaseMode) -> Unit,
    onUpdateSlotColor: (ThemeColorSlot, Color) -> Unit,
    onRestoreDefault: () -> Unit
) {
    var editingSlot by remember { mutableStateOf<ThemeColorSlot?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Live Theme Preview Card
        LiveThemePreviewCard(config = themeConfig)

        // Base Mode Selector (Light / Dark / AMOLED)
        Surface(
            color = themeConfig.cardSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, themeConfig.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Display Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = themeConfig.primary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeBaseMode.values().forEach { mode ->
                        val selected = themeConfig.baseMode == mode
                        Surface(
                            color = if (selected) themeConfig.primary else themeConfig.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) themeConfig.primary else themeConfig.mutedText.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectBaseMode(mode) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (selected) themeConfig.onPrimary else themeConfig.text
                                )
                            }
                        }
                    }
                }
            }
        }

        // 10 Preset Themes
        Surface(
            color = themeConfig.cardSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, themeConfig.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preset Themes (10)",
                        style = MaterialTheme.typography.titleMedium,
                        color = themeConfig.primary
                    )
                    TextButton(onClick = onRestoreDefault) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Restore Default",
                            tint = themeConfig.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Restore Default", color = themeConfig.accent, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    val presets = ThemePreset.values().filter { it != ThemePreset.CUSTOM }
                    items(presets, key = { it.name }) { preset ->
                        val preview = remember(preset) { MaxterThemeConfig.fromPreset(preset) }
                        val isSelected = themeConfig.preset == preset
                        Surface(
                            color = preview.background,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) themeConfig.primary else preview.primary.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .width(124.dp)
                                .clickable { onSelectPreset(preset) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(preview.primary)
                                    )
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(preview.secondary)
                                    )
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(preview.accent)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = preset.label,
                                    color = preview.text,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 10 Granular Custom Colour Slots + HEX & HSV Picker
        Surface(
            color = themeConfig.cardSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, themeConfig.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Custom Colour Studio",
                    style = MaterialTheme.typography.titleMedium,
                    color = themeConfig.primary
                )
                Text(
                    text = "Tap any element to customize with the colour picker or HEX code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeConfig.mutedText
                )
                Spacer(Modifier.height(10.dp))
                ThemeColorSlot.values().forEach { slot ->
                    val slotColor = themeConfig.getSlotColor(slot)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { editingSlot = slot }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(slotColor)
                                    .border(1.dp, themeConfig.mutedText.copy(alpha = 0.5f), CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = slot.label,
                                color = themeConfig.text,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = colorToHex(slotColor),
                                color = themeConfig.mutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Colorize,
                                contentDescription = "Pick ${slot.label}",
                                tint = themeConfig.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingSlot != null) {
        val slot = editingSlot!!
        ColorPickerDialog(
            title = slot.label,
            initialColor = themeConfig.getSlotColor(slot),
            themeConfig = themeConfig,
            onDismiss = { editingSlot = null },
            onConfirm = { newColor ->
                onUpdateSlotColor(slot, newColor)
                editingSlot = null
            }
        )
    }
}

@Composable
private fun LiveThemePreviewCard(config: MaxterThemeConfig) {
    Surface(
        color = config.background,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, config.primary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE THEME PREVIEW",
                        color = config.highlight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = config.preset.label,
                        color = config.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = config.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            // Mini player preview bar
            Surface(
                color = config.cardSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(config.playerControl),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = config.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sample_Movie_1080p.mp4",
                                    color = config.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "HW+ Decoder • Smart Enhance",
                                    color = config.secondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = config.buttonIcon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.65f },
                        color = config.progressBar,
                        trackColor = config.mutedText.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Mini navigation bar preview
            Surface(
                color = config.navigationBar,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = config.primary, modifier = Modifier.size(16.dp))
                    Icon(Icons.Default.Folder, contentDescription = null, tint = config.buttonIcon.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    Icon(Icons.Default.History, contentDescription = null, tint = config.buttonIcon.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    Icon(Icons.Default.Settings, contentDescription = null, tint = config.buttonIcon.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    themeConfig: MaxterThemeConfig,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }
    var hexError by remember { mutableStateOf(false) }

    val currentColor = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    val quickSwatches = remember {
        listOf(
            Color(0xFFFF6700), Color(0xFF00B4D8), Color(0xFF00E676), Color(0xFF9D4EDD),
            Color(0xFFFF007F), Color(0xFFE53935), Color(0xFF4C8BFF), Color(0xFFFFD600),
            Color(0xFFFFFFFF), Color(0xFF14161F), Color(0xFF000000), Color(0xFFFF4D8D)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = themeConfig.cardSurface,
        titleContentColor = themeConfig.text,
        textContentColor = themeConfig.text,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Live Colour Preview & HEX Input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor)
                            .border(1.dp, themeConfig.mutedText, RoundedCornerShape(12.dp))
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw.uppercase(Locale.US).take(9)
                            val parsed = parseHexColorOrNull(hexInput)
                            if (parsed != null) {
                                hexError = false
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            } else {
                                hexError = true
                            }
                        },
                        label = { Text("HEX (#RRGGBB)") },
                        isError = hexError,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Hue Slider with Rainbow Track
                Text("Hue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            )
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
                        hexInput = colorToHex(c)
                        hexError = false
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = currentColor, activeTrackColor = Color.Transparent)
                )

                Text("Saturation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
                        hexInput = colorToHex(c)
                        hexError = false
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = currentColor, activeTrackColor = currentColor)
                )

                Text("Brightness", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
                        hexInput = colorToHex(c)
                        hexError = false
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = currentColor, activeTrackColor = currentColor)
                )

                // Quick Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    quickSwatches.take(6).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, themeConfig.mutedText.copy(alpha = 0.4f), CircleShape)
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(swatch.toArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                    hexInput = colorToHex(swatch)
                                    hexError = false
                                }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    quickSwatches.drop(6).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, themeConfig.mutedText.copy(alpha = 0.4f), CircleShape)
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(swatch.toArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                    hexInput = colorToHex(swatch)
                                    hexError = false
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentColor) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.primary,
                    contentColor = themeConfig.onPrimary
                )
            ) {
                Text("Apply Colour", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = themeConfig.text)
            }
        }
    )
}

fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format(Locale.US, "#%06X", 0xFFFFFF and argb)
}

fun parseHexColorOrNull(input: String): Color? {
    val clean = input.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    return runCatching {
        val parsed = clean.toLong(16).toInt()
        if (clean.length == 6) {
            Color(0xFF000000.toInt() or parsed)
        } else {
            Color(parsed)
        }
    }.getOrNull()
}

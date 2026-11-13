package com.maxter.player

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.maxter.player.ui.MaxterAppRoot
import com.maxter.player.ui.theme.AppThemeMode
import com.maxter.player.ui.theme.MaxterPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(AppThemeMode.AMOLED) }
            MaxterPlayerTheme(themeMode = themeMode) {
                MaxterAppRoot(
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it },
                    onEnterPip = { enterPipMode() }
                )
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

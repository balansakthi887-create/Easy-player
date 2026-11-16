package com.maxter.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import com.maxter.player.ui.MaxterAppRoot
import com.maxter.player.ui.MaxterViewModel
import com.maxter.player.ui.theme.AppThemeMode
import com.maxter.player.ui.theme.MaxterPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MaxterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only handle the launch intent on a fresh start, not after recreation,
        // otherwise the same video restarts every time the activity is recreated.
        if (savedInstanceState == null) {
            handleIncomingVideoIntent(intent)
        }

        setContent {
            var themeMode by remember { mutableStateOf(AppThemeMode.AMOLED) }
            MaxterPlayerTheme(themeMode = themeMode) {
                MaxterAppRoot(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it },
                    onEnterPip = { enterPipMode() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingVideoIntent(intent)
    }

    private fun handleIncomingVideoIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.playExternalUri(uri)
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching {
                enterPictureInPictureMode(params)
            }
        }
    }
}

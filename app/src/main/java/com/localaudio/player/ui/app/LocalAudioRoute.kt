package com.localaudio.player.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localaudio.player.app.AppViewModel
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.ui.theme.LocalAudioTheme

@Composable
fun LocalAudioRoute(
    viewModel: AppViewModel,
    onDarkThemeChanged: (Boolean) -> Unit,
) {
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val darkTheme = when (contentState.settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    LaunchedEffect(darkTheme) { onDarkThemeChanged(darkTheme) }
    LocalAudioTheme(darkTheme = darkTheme) {
        LocalAudioApp(
            state = contentState,
            playback = playbackState,
            onEvent = viewModel::onEvent,
        )
    }
}

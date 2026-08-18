package com.localaudio.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.localaudio.player.app.AppEffect
import com.localaudio.player.app.AppEvent
import com.localaudio.player.app.AppViewModel
import com.localaudio.player.app.AppViewModelFactory
import com.localaudio.player.ui.app.LocalAudioRoute
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory((application as LocalAudioApplication).container)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onEvent(AppEvent.FolderSelected(it)) }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onEvent(AppEvent.NotificationPermissionHandled)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(viewModel.settings.value.showWhenLocked)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.onEvent(AppEvent.Back)
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings
                        .map { it.showWhenLocked }
                        .distinctUntilChanged()
                        .collect(::setShowWhenLocked)
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            AppEffect.OpenFolderPicker -> folderPicker.launch(null)
                            AppEffect.RequestNotificationPermission -> requestNotificationPermission()
                        }
                    }
                }
            }
        }
        setContent {
            LocalAudioRoute(
                viewModel = viewModel,
                onDarkThemeChanged = ::applyWindowColors,
            )
        }
        viewModel.onEvent(AppEvent.EnsureNotificationPermission)
    }

    @Suppress("DEPRECATION")
    private fun applyWindowColors(dark: Boolean) {
        val barColor = if (dark) {
            android.graphics.Color.rgb(20, 18, 24)
        } else {
            android.graphics.Color.rgb(255, 251, 255)
        }
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                barColor,
            ),
        )
        if (Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
        } else {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val lightBars = if (dark) 0 else {
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            }
            window.decorView.windowInsetsController?.setSystemBarsAppearance(
                lightBars,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            window.decorView.systemUiVisibility = if (dark) 0 else {
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(AppEvent.NotificationPermissionHandled)
        }
    }
}

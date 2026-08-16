package com.localaudio.player.app

import android.net.Uri
import com.localaudio.player.data.library.LibraryState
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackState

enum class AppScreen { HOME, PLAYER, SETTINGS }

sealed interface AppDialog {
    data object Queue : AppDialog
    data object Mode : AppDialog
    data object Timer : AppDialog
    data object Theme : AppDialog
    data object Header : AppDialog
    data object ClearFolders : AppDialog
    data class EditDuration(val existingMs: Long?) : AppDialog
}

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val dialog: AppDialog? = null,
    val homeLocation: FolderLocation? = null,
    val homeRows: List<HomeRow> = emptyList(),
    val hasLibrary: Boolean = false,
    val library: LibraryState = LibraryState(),
    val settings: AppSettings = AppSettings(),
    val playback: PlaybackState = PlaybackState(),
)

sealed interface SettingChange {
    data class SetThemeMode(val value: ThemeMode) : SettingChange
    data class SetHomeHeaderMode(val value: HomeHeaderMode) : SettingChange
    data class SetShowWhenLocked(val value: Boolean) : SettingChange
    data class SetShowStaticArtwork(val value: Boolean) : SettingChange
    data class SetTimerEnabled(val value: Boolean) : SettingChange
    data class SetTimerDuration(val valueMs: Long) : SettingChange
    data class SetWaitForCurrentEnd(val value: Boolean) : SettingChange
    data class SetSeekStep(val valueMs: Long) : SettingChange
    data class AddTimerDuration(val valueMs: Long) : SettingChange
    data class EditTimerDuration(val oldValueMs: Long, val newValueMs: Long) : SettingChange
    data class DeleteTimerDuration(val valueMs: Long) : SettingChange
}

sealed interface AppEvent {
    data class SelectScreen(val screen: AppScreen) : AppEvent
    data object Back : AppEvent
    data class OpenDirectory(val location: FolderLocation) : AppEvent
    data class PlayAudio(val item: AudioItem) : AppEvent
    data object AddFolder : AppEvent
    data class FolderSelected(val uri: Uri) : AppEvent
    data class Playback(val command: PlaybackCommand) : AppEvent
    data class ShowDialog(val dialog: AppDialog) : AppEvent
    data object DismissDialog : AppEvent
    data class UpdateSetting(val change: SettingChange) : AppEvent
    data class RescanFolder(val uri: String) : AppEvent
    data class RemoveFolder(val uri: String) : AppEvent
    data object RescanAll : AppEvent
    data object ClearFolders : AppEvent
    data object EnsureNotificationPermission : AppEvent
    data object NotificationPermissionRequestLaunched : AppEvent
}

sealed interface AppEffect {
    data object OpenFolderPicker : AppEffect
    data object RequestNotificationPermission : AppEffect
}

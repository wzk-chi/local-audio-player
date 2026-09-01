package com.localaudio.player.app

import android.net.Uri
import com.localaudio.player.data.library.LibraryState
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AutoSkipSegment
import com.localaudio.player.data.model.DirectorySkipRule
import com.localaudio.player.data.model.FolderLocation
import com.localaudio.player.data.model.RecycleBinState
import com.localaudio.player.data.settings.AppSettings
import com.localaudio.player.data.settings.EqualizerPreset
import com.localaudio.player.data.settings.HomeHeaderMode
import com.localaudio.player.data.settings.ThemeMode
import com.localaudio.player.playback.PlaybackCommand
import com.localaudio.player.playback.PlaybackState

enum class AppScreen {
    HOME,
    PLAYER,
    SETTINGS,
    LIBRARY_SETTINGS,
    AUTO_SKIP_SETTINGS,
    RECYCLE_BIN,
    EQUALIZER_SETTINGS,
}

sealed interface HomeActionTarget {
    data class Audio(val item: AudioItem) : HomeActionTarget
    data class Directory(val location: FolderLocation) : HomeActionTarget
}

sealed interface AppDialog {
    data object Queue : AppDialog
    data object Mode : AppDialog
    data class Rename(val target: HomeActionTarget) : AppDialog
    data class Delete(val target: HomeActionTarget, val deleteSource: Boolean = false) : AppDialog
    data class DirectorySkip(val folderUri: String, val relativePath: String) : AppDialog
    data class AutoSkipEditor(
        val audioKey: String,
        val segmentId: String?,
        val startMs: Long,
        val endMs: Long,
        val durationMs: Long,
    ) : AppDialog
    data object Timer : AppDialog
    data object Theme : AppDialog
    data object Header : AppDialog
    data object SeekStep : AppDialog
    data object TimerDuration : AppDialog
    data object AddDuration : AppDialog
}

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val dialog: AppDialog? = null,
    val homeLocation: FolderLocation? = null,
    val homeRows: List<HomeRow> = emptyList(),
    val locateRequest: Int = 0,
    val library: LibraryState = LibraryState(),
    val settings: AppSettings = AppSettings(),
    val autoSkipSegments: List<AutoSkipSegment> = emptyList(),
    val directorySkipRules: List<DirectorySkipRule> = emptyList(),
    val recycleBin: RecycleBinState = RecycleBinState(),
    val activeAutoSkipMark: ActiveAutoSkipMark? = null,
    val playback: PlaybackState = PlaybackState(),
)

data class ActiveAutoSkipMark(
    val audioKey: String,
    val startMs: Long,
)

sealed interface SettingChange {
    data class SetThemeMode(val value: ThemeMode) : SettingChange
    data class SetHomeHeaderMode(val value: HomeHeaderMode) : SettingChange
    data class SetHomeListBottomAligned(val value: Boolean) : SettingChange
    data class SetShowAlbumCover(val value: Boolean) : SettingChange
    data class SetShowWhenLocked(val value: Boolean) : SettingChange
    data class SetTimerEnabled(val value: Boolean) : SettingChange
    data class SetTimerDuration(val valueMs: Long) : SettingChange
    data class SetWaitForCurrentEnd(val value: Boolean) : SettingChange
    data class SetSeekStep(val valueMs: Long) : SettingChange
    data class SetFadeEnabled(val value: Boolean) : SettingChange
    data class SetFadeDuration(val valueMs: Long) : SettingChange
    data class SetLoudnessEnabled(val value: Boolean) : SettingChange
    data class SetEqualizerEnabled(val value: Boolean) : SettingChange
    data class SetEqualizerPreset(val value: EqualizerPreset) : SettingChange
    data class SetEqualizerBandGain(val index: Int, val valueDb: Int) : SettingChange
    data class AddTimerDuration(val valueMs: Long) : SettingChange
    data class DeleteTimerDuration(val valueMs: Long) : SettingChange
}

sealed interface AppEvent {
    data class SelectScreen(val screen: AppScreen) : AppEvent
    data class OpenEqualizer(val returnScreen: AppScreen) : AppEvent
    data object Back : AppEvent
    data object LocateCurrent : AppEvent
    data class OpenDirectory(val location: FolderLocation) : AppEvent
    data class PlayAudio(val item: AudioItem) : AppEvent
    data class RenameHomeItem(val target: HomeActionTarget, val name: String) : AppEvent
    data class DeleteHomeItem(val target: HomeActionTarget, val deleteSource: Boolean) : AppEvent
    data class RestoreRecycle(val keys: Set<String>) : AppEvent
    data class CleanRecycle(val keys: Set<String>) : AppEvent
    data object OpenRecycleBin : AppEvent
    data object StartAutoSkipMark : AppEvent
    data object FinishAutoSkipMark : AppEvent
    data object CancelAutoSkipMark : AppEvent
    data object OpenAutoSkipSettings : AppEvent
    data class DeleteAutoSkipSegment(val id: String) : AppEvent
    data class EditAutoSkipSegment(val id: String) : AppEvent
    data class PlayAutoSkipAudio(val segmentId: String) : AppEvent
    data class TestAutoSkipSegment(
        val audioKey: String,
        val startMs: Long,
        val endMs: Long,
    ) : AppEvent
    data class SaveAutoSkipSegment(
        val audioKey: String,
        val segmentId: String?,
        val startMs: Long,
        val endMs: Long,
    ) : AppEvent
    data class SaveDirectorySkip(
        val folderUri: String,
        val relativePath: String,
        val startSeconds: Long,
        val endSeconds: Long,
    ) : AppEvent
    data object AddFolder : AppEvent
    data class FolderSelected(val uri: Uri) : AppEvent
    data class Playback(val command: PlaybackCommand) : AppEvent
    data class ShowDialog(val dialog: AppDialog) : AppEvent
    data object DismissDialog : AppEvent
    data class UpdateSetting(val change: SettingChange) : AppEvent
    data class RescanFolder(val uri: String) : AppEvent
    data class RemoveFolder(val uri: String) : AppEvent
    data object RescanAll : AppEvent
    data object EnsureNotificationPermission : AppEvent
    data object NotificationPermissionHandled : AppEvent
}

sealed interface AppEffect {
    data object OpenFolderPicker : AppEffect
    data object RequestNotificationPermission : AppEffect
    data class ShowMessage(val message: String) : AppEffect
}

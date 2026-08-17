package com.localaudio.player.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.localaudio.player.data.model.FolderLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Owns typed settings state and its SharedPreferences persistence. */
class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("local_audio", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readFromPreferences())

    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun updateThemeMode(value: ThemeMode) =
        updateString(KEY_THEME, value.name) { it.copy(themeMode = value) }

    fun updateHomeHeaderMode(value: HomeHeaderMode) =
        updateString(KEY_HEADER, value.name) { it.copy(homeHeaderMode = value) }

    fun updateShowWhenLocked(value: Boolean) =
        updateBoolean(KEY_SHOW_WHEN_LOCKED, value) { it.copy(showWhenLocked = value) }

    fun updateTimerEnabled(value: Boolean) =
        updateBoolean(KEY_TIMER_ENABLED, value) { it.copy(timerEnabled = value) }

    fun updateTimerDurationMs(value: Long) =
        updateLong(KEY_TIMER_DURATION, value) { it.copy(timerDurationMs = value) }

    fun updateWaitForCurrentEnd(value: Boolean) =
        updateBoolean(KEY_WAIT_FOR_END, value) { it.copy(waitForCurrentEnd = value) }

    fun updateSeekStepMs(value: Long) {
        val normalized = value.coerceAtLeast(1_000L)
        updateLong(KEY_SEEK_STEP, normalized) { it.copy(seekStepMs = normalized) }
    }

    fun updateRepeatMode(value: Int) {
        val normalized = value.coerceIn(0, 2)
        updateInt(KEY_REPEAT, normalized) { it.copy(repeatMode = normalized) }
    }

    fun updateShuffleEnabled(value: Boolean) =
        updateBoolean(KEY_SHUFFLE, value) { it.copy(shuffleEnabled = value) }

    fun updateTimerDurationOptions(values: List<Long>, selectedMs: Long? = null) {
        val options = values.filter { it > 0L }.distinct().sorted()
        if (options.isEmpty()) return
        val selected = selectedMs ?: _state.value.timerDurationMs
        val normalizedSelected = if (selected in options) selected else options.first()
        updateState(
            preferences.edit()
                .putString(KEY_TIMER_OPTIONS, options.joinToString(","))
                .putLong(KEY_TIMER_DURATION, normalizedSelected),
        ) {
            it.copy(
                timerDurationOptionsMs = options,
                timerDurationMs = normalizedSelected,
            )
        }
    }

    fun updateSavedHomeLocation(value: FolderLocation?) {
        val editor = preferences.edit()
        if (value == null) {
            editor.remove(KEY_HOME_LOCATION)
        } else {
            editor.putString(
                KEY_HOME_LOCATION,
                JSONObject()
                    .put("folderUri", value.folderUri)
                    .put("rootName", value.rootName)
                    .put("relativePath", value.relativePath)
                    .put("name", value.name)
                    .toString(),
            )
        }
        updateState(editor) { it.copy(savedHomeLocation = value) }
    }

    private fun updateState(
        editor: SharedPreferences.Editor,
        transform: (AppSettings) -> AppSettings,
    ) {
        editor.apply()
        _state.update(transform)
    }

    private fun updateString(
        key: String,
        value: String,
        transform: (AppSettings) -> AppSettings,
    ) = updateState(preferences.edit().putString(key, value), transform)

    private fun updateBoolean(
        key: String,
        value: Boolean,
        transform: (AppSettings) -> AppSettings,
    ) = updateState(preferences.edit().putBoolean(key, value), transform)

    private fun updateLong(
        key: String,
        value: Long,
        transform: (AppSettings) -> AppSettings,
    ) = updateState(preferences.edit().putLong(key, value), transform)

    private fun updateInt(
        key: String,
        value: Int,
        transform: (AppSettings) -> AppSettings,
    ) = updateState(preferences.edit().putInt(key, value), transform)

    fun notificationRequested(): Boolean = preferences.getBoolean(KEY_NOTIFICATION_REQUESTED, false)

    fun markNotificationRequested() {
        preferences.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
    }

    private fun readFromPreferences(): AppSettings {
        val options = preferences.getString(KEY_TIMER_OPTIONS, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.filter { it > 0L }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_TIMER_DURATIONS_MS
        val duration = preferences.getLong(KEY_TIMER_DURATION, options.first())
            .takeIf { it in options } ?: options.first()
        return AppSettings(
            themeMode = enumOrDefault(preferences.getString(KEY_THEME, null), ThemeMode.SYSTEM),
            homeHeaderMode = enumOrDefault(
                preferences.getString(KEY_HEADER, null),
                HomeHeaderMode.FIXED,
            ),
            showWhenLocked = preferences.getBoolean(KEY_SHOW_WHEN_LOCKED, false),
            timerEnabled = preferences.getBoolean(KEY_TIMER_ENABLED, true),
            timerDurationMs = duration,
            timerDurationOptionsMs = options,
            waitForCurrentEnd = preferences.getBoolean(KEY_WAIT_FOR_END, true),
            seekStepMs = preferences.getLong(KEY_SEEK_STEP, 10_000L).coerceAtLeast(1_000L),
            repeatMode = preferences.getInt(KEY_REPEAT, REPEAT_ALL).coerceIn(0, 2),
            shuffleEnabled = preferences.getBoolean(KEY_SHUFFLE, false),
            savedHomeLocation = decodeLocation(preferences.getString(KEY_HOME_LOCATION, null)),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun decodeLocation(value: String?): FolderLocation? = runCatching {
        if (value.isNullOrBlank()) return@runCatching null
        val json = JSONObject(value)
        FolderLocation(
            folderUri = json.getString("folderUri"),
            rootName = json.getString("rootName"),
            relativePath = json.getString("relativePath"),
            name = json.getString("name"),
        )
    }.getOrNull()

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_HEADER = "header"
        const val KEY_SHOW_WHEN_LOCKED = "show_when_locked"
        const val KEY_TIMER_ENABLED = "timer_enabled"
        const val KEY_TIMER_DURATION = "timer_duration"
        const val KEY_TIMER_OPTIONS = "timer_options"
        const val KEY_WAIT_FOR_END = "wait_for_end"
        const val KEY_SEEK_STEP = "seek_step"
        const val KEY_REPEAT = "repeat"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_HOME_LOCATION = "home_location"
        const val KEY_NOTIFICATION_REQUESTED = "notification_requested"
    }
}

package com.localaudio.player.playback

import android.content.Context
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AudioItemJson
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackSnapshot(
    val queue: List<AudioItem>,
    val currentIndex: Int,
    val positionMs: Long,
)

class PlaybackStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_audio", Context.MODE_PRIVATE)

    fun readSnapshot(): PlaybackSnapshot? = runCatching {
        val value = preferences.getString(KEY_PLAYBACK_SNAPSHOT, null)
        if (value.isNullOrBlank()) return@runCatching null
        val json = JSONObject(value)
        val array = json.getJSONArray("queue")
        PlaybackSnapshot(
            queue = (0 until array.length()).map { index ->
                AudioItemJson.decode(array.getJSONObject(index))
            },
            currentIndex = json.optInt("index", 0).coerceIn(0, (array.length() - 1).coerceAtLeast(0)),
            positionMs = preferences.getLong(
                KEY_PLAYBACK_POSITION,
                json.optLong("position", 0L),
            ).coerceAtLeast(0L),
        )
    }.getOrNull()

    fun writeSnapshot(snapshot: PlaybackSnapshot) {
        val queue = JSONArray()
        snapshot.queue.forEach { item ->
            queue.put(AudioItemJson.encode(item))
        }
        preferences.edit()
            .putString(
                KEY_PLAYBACK_SNAPSHOT,
                JSONObject()
                    .put("queue", queue)
                    .put("index", snapshot.currentIndex)
                    .put("position", snapshot.positionMs)
                    .toString(),
            )
            .putLong(KEY_PLAYBACK_POSITION, snapshot.positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun writePosition(positionMs: Long) {
        preferences.edit()
            .putLong(KEY_PLAYBACK_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun clearSnapshot() {
        preferences.edit()
            .remove(KEY_PLAYBACK_SNAPSHOT)
            .remove(KEY_PLAYBACK_POSITION)
            .apply()
    }

    private companion object {
        const val KEY_PLAYBACK_SNAPSHOT = "playback_snapshot"
        const val KEY_PLAYBACK_POSITION = "playback_position"
    }
}

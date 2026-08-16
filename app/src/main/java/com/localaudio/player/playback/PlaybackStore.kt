package com.localaudio.player.playback

import android.content.Context
import android.net.Uri
import com.localaudio.player.data.model.AudioItem
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
                val item = array.getJSONObject(index)
                AudioItem(
                    uri = Uri.parse(item.getString("uri")),
                    title = item.getString("title"),
                    artist = item.getString("artist"),
                    durationMs = item.optLong("duration", 0L),
                    folderUri = item.getString("folderUri"),
                    folderName = item.getString("folderName"),
                    relativePath = item.optString("relativePath"),
                )
            },
            currentIndex = json.optInt("index", 0).coerceIn(0, (array.length() - 1).coerceAtLeast(0)),
            positionMs = json.optLong("position", 0L).coerceAtLeast(0L),
        )
    }.getOrNull()

    fun writeSnapshot(snapshot: PlaybackSnapshot) {
        val queue = JSONArray()
        snapshot.queue.forEach { item ->
            queue.put(
                JSONObject()
                    .put("uri", item.uri.toString())
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("duration", item.durationMs)
                    .put("folderUri", item.folderUri)
                    .put("folderName", item.folderName)
                    .put("relativePath", item.relativePath),
            )
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
            .apply()
    }

    private companion object {
        const val KEY_PLAYBACK_SNAPSHOT = "playback_snapshot"
    }
}

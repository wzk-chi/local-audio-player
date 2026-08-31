package com.localaudio.player.data.model

import android.net.Uri
import com.localaudio.player.data.hash.CONTENT_HASH_ALGORITHM
import org.json.JSONObject

internal object AudioItemJson {
    fun encode(item: AudioItem): JSONObject = JSONObject()
        .put("uri", item.uri.toString())
        .put("title", item.title)
        .put("artist", item.artist)
        .put("duration", item.durationMs)
        .put("folderUri", item.folderUri)
        .put("folderName", item.folderName)
        .put("relativePath", item.relativePath)
        .apply {
            item.contentHash?.let {
                put("contentHashAlgorithm", CONTENT_HASH_ALGORITHM)
                put("contentHash", it)
            }
        }

    fun decode(json: JSONObject): AudioItem = AudioItem(
        uri = Uri.parse(json.getString("uri")),
        title = json.getString("title"),
        artist = json.getString("artist"),
        durationMs = json.optLong("duration", 0L),
        folderUri = json.getString("folderUri"),
        folderName = json.getString("folderName"),
        relativePath = json.optString("relativePath"),
        contentHash = json.optString("contentHash")
            .takeIf { it.isNotBlank() && json.optString("contentHashAlgorithm") == CONTENT_HASH_ALGORITHM },
    )
}

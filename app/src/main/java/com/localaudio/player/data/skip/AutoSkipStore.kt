package com.localaudio.player.data.skip

import android.content.Context
import com.localaudio.player.data.model.AutoSkipSegment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AutoSkipStore(context: Context) {
    private val file = File(context.filesDir, "auto_skip_segments.json")

    fun readSegments(): List<AutoSkipSegment> = runCatching {
        val root = JSONObject(file.takeIf { it.exists() }?.readText() ?: "{}")
        val segments = root.optJSONArray("segments") ?: JSONArray()
        (0 until segments.length()).mapNotNull { index ->
            decode(segments.optJSONObject(index))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun writeSegments(segments: List<AutoSkipSegment>) {
        val values = JSONArray()
        segments.forEach { values.put(encode(it)) }
        file.writeText(
            JSONObject()
                .put("version", 1)
                .put("segments", values)
                .toString(),
        )
    }

    private fun encode(segment: AutoSkipSegment): JSONObject = JSONObject()
        .put("id", segment.id)
        .put("audioKey", segment.audioKey)
        .put("audioUri", segment.audioUri)
        .put("folderUri", segment.folderUri)
        .put("titleSnapshot", segment.titleSnapshot)
        .put("folderNameSnapshot", segment.folderNameSnapshot)
        .put("relativePath", segment.relativePath)
        .put("startMs", segment.startMs)
        .put("endMs", segment.endMs)
        .put("modifiedAtMs", segment.modifiedAtMs)

    private fun decode(json: JSONObject?): AutoSkipSegment? = runCatching {
        json ?: return@runCatching null
        AutoSkipSegment(
            id = json.getString("id"),
            audioKey = json.getString("audioKey"),
            audioUri = json.getString("audioUri"),
            folderUri = json.optString("folderUri"),
            titleSnapshot = json.optString("titleSnapshot"),
            folderNameSnapshot = json.optString("folderNameSnapshot"),
            relativePath = json.optString("relativePath"),
            startMs = json.getLong("startMs").coerceAtLeast(0L),
            endMs = json.getLong("endMs").coerceAtLeast(0L),
            modifiedAtMs = json.optLong("modifiedAtMs", json.optLong("createdAtMs", 0L)),
        ).takeIf { it.endMs > it.startMs }
    }.getOrNull()
}

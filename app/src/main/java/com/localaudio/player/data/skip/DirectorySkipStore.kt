package com.localaudio.player.data.skip

import android.content.Context
import com.localaudio.player.data.model.DirectorySkipRule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.AtomicMoveNotSupportedException

class DirectorySkipStore(context: Context) {
    private val file = File(context.filesDir, "directory_skip_rules.json")

    fun readRules(): List<DirectorySkipRule> = runCatching {
        val root = JSONObject(file.takeIf { it.exists() }?.readText() ?: "{}")
        val rules = root.optJSONArray("rules") ?: JSONArray()
        (0 until rules.length()).mapNotNull { index -> decode(rules.optJSONObject(index)) }
            .distinctBy { it.key }
    }.getOrDefault(emptyList())

    fun writeRules(rules: List<DirectorySkipRule>) {
        val values = JSONArray()
        rules.forEach { values.put(encode(it)) }
        val temporaryFile = File(file.parentFile, "${file.name}.tmp")
        try {
            temporaryFile.writeText(
                JSONObject()
                    .put("version", 1)
                    .put("rules", values)
                    .toString(),
            )
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    ATOMIC_MOVE,
                    REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun encode(rule: DirectorySkipRule): JSONObject = JSONObject()
        .put("folderUri", rule.folderUri)
        .put("relativePath", rule.relativePath)
        .put("startSeconds", rule.startSeconds)
        .put("endSeconds", rule.endSeconds)
        .put("modifiedAtMs", rule.modifiedAtMs)

    private fun decode(json: JSONObject?): DirectorySkipRule? = runCatching {
        json ?: return@runCatching null
        DirectorySkipRule(
            folderUri = json.getString("folderUri"),
            relativePath = json.optString("relativePath"),
            startSeconds = json.optLong("startSeconds", 0L).coerceAtLeast(0L),
            endSeconds = json.optLong("endSeconds", 0L).coerceAtLeast(0L),
            modifiedAtMs = json.optLong("modifiedAtMs", 0L),
        ).takeIf { it.startSeconds > 0L || it.endSeconds > 0L }
    }.getOrNull()
}

package com.localaudio.player.data.library

import android.content.Context
import android.net.Uri
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AudioItemJson
import com.localaudio.player.data.model.FolderItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_audio", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "library_cache.json")

    fun readFolders(): List<FolderItem> = runCatching {
        val json = JSONArray(preferences.getString(KEY_FOLDERS, "[]"))
        (0 until json.length()).map { index ->
            val value = json.get(index)
            if (value is JSONObject) {
                val uri = value.getString("uri")
                FolderItem(uri, value.optString("name").ifBlank { fallbackFolderName(Uri.parse(uri)) })
            } else {
                val uri = value.toString()
                FolderItem(uri, fallbackFolderName(Uri.parse(uri)))
            }
        }
    }.getOrDefault(emptyList())

    fun writeFolders(folders: List<FolderItem>) {
        val json = JSONArray()
        folders.forEach { folder ->
            json.put(
                JSONObject()
                    .put("uri", folder.uri)
                    .put("name", folder.displayName),
            )
        }
        preferences.edit().putString(KEY_FOLDERS, json.toString()).apply()
    }

    fun readItems(): List<AudioItem> = runCatching {
        val json = JSONArray(cacheFile.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until json.length()).map { index ->
            AudioItemJson.decode(json.getJSONObject(index))
        }.distinctBy { it.key }
    }.getOrDefault(emptyList())

    fun writeItems(items: List<AudioItem>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(AudioItemJson.encode(item))
        }
        cacheFile.writeText(json.toString())
    }

    private companion object {
        const val KEY_FOLDERS = "folders_json"
    }
}

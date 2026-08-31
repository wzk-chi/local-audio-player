package com.localaudio.player.data.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.localaudio.player.data.database.AudioDatabase
import com.localaudio.player.data.hash.CONTENT_HASH_ALGORITHM
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderItem
import org.json.JSONArray
import org.json.JSONObject

class LibraryStore(context: Context, private val database: AudioDatabase) {
    private val preferences = context.getSharedPreferences("local_audio", Context.MODE_PRIVATE)

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
        // Folder roots must be durable before a scan can outlive the process.
        // This method is always called from the repository's background writer.
        check(preferences.edit().putString(KEY_FOLDERS, json.toString()).commit()) {
            "无法保存音乐库文件夹"
        }
    }

    fun readItems(): List<AudioItem> = runCatching {
        database.readableDatabase.query(
            AudioDatabase.TABLE_AUDIO_ITEMS,
            AUDIO_ITEM_COLUMNS,
            null,
            null,
            null,
            null,
            "rowid ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toAudioItem())
            }
        }.distinctBy { it.key }
    }.getOrDefault(emptyList())

    fun writeItems(items: List<AudioItem>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete(AudioDatabase.TABLE_AUDIO_ITEMS, null, null)
            items.distinctBy { it.key }.forEach { item ->
                db.insertOrThrow(AudioDatabase.TABLE_AUDIO_ITEMS, null, item.toContentValues())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toAudioItem(): AudioItem {
        val hash = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH))
        val hashAlgorithm = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM))
        return AudioItem(
            uri = Uri.parse(getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_URI))),
            title = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_TITLE)),
            artist = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_ARTIST)),
            durationMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_DURATION_MS)),
            folderUri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_FOLDER_URI)),
            folderName = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_FOLDER_NAME)),
            relativePath = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_RELATIVE_PATH)),
            sizeBytes = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_SIZE_BYTES)),
            lastModifiedMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_LAST_MODIFIED_MS)),
            contentHash = hash.takeIf {
                !it.isNullOrBlank() && hashAlgorithm == CONTENT_HASH_ALGORITHM
            },
        )
    }

    private fun AudioItem.toContentValues(): ContentValues = ContentValues().apply {
        put(AudioDatabase.COLUMN_URI, uri.toString())
        put(AudioDatabase.COLUMN_TITLE, title)
        put(AudioDatabase.COLUMN_ARTIST, artist)
        put(AudioDatabase.COLUMN_DURATION_MS, durationMs)
        put(AudioDatabase.COLUMN_FOLDER_URI, folderUri)
        put(AudioDatabase.COLUMN_FOLDER_NAME, folderName)
        put(AudioDatabase.COLUMN_RELATIVE_PATH, relativePath)
        put(AudioDatabase.COLUMN_SIZE_BYTES, sizeBytes)
        put(AudioDatabase.COLUMN_LAST_MODIFIED_MS, lastModifiedMs)
        contentHash?.let {
            put(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM, CONTENT_HASH_ALGORITHM)
            put(AudioDatabase.COLUMN_CONTENT_HASH, it)
        }
    }

    private companion object {
        val AUDIO_ITEM_COLUMNS = arrayOf(
            AudioDatabase.COLUMN_URI,
            AudioDatabase.COLUMN_TITLE,
            AudioDatabase.COLUMN_ARTIST,
            AudioDatabase.COLUMN_DURATION_MS,
            AudioDatabase.COLUMN_FOLDER_URI,
            AudioDatabase.COLUMN_FOLDER_NAME,
            AudioDatabase.COLUMN_RELATIVE_PATH,
            AudioDatabase.COLUMN_SIZE_BYTES,
            AudioDatabase.COLUMN_LAST_MODIFIED_MS,
            AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM,
            AudioDatabase.COLUMN_CONTENT_HASH,
        )
        const val KEY_FOLDERS = "folders_json"
    }
}

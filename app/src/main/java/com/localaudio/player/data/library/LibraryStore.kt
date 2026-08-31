package com.localaudio.player.data.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.localaudio.player.data.hash.CONTENT_HASH_ALGORITHM
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderItem
import org.json.JSONArray
import org.json.JSONObject

class LibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_audio", Context.MODE_PRIVATE)
    private val database = LibraryDatabase(context)

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
        database.readableDatabase.query(
            TABLE_AUDIO_ITEMS,
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
            db.delete(TABLE_AUDIO_ITEMS, null, null)
            items.distinctBy { it.key }.forEach { item ->
                db.insertOrThrow(TABLE_AUDIO_ITEMS, null, item.toContentValues())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toAudioItem(): AudioItem {
        val hash = getString(getColumnIndexOrThrow(COLUMN_CONTENT_HASH))
        val hashAlgorithm = getString(getColumnIndexOrThrow(COLUMN_CONTENT_HASH_ALGORITHM))
        return AudioItem(
            uri = Uri.parse(getString(getColumnIndexOrThrow(COLUMN_URI))),
            title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
            artist = getString(getColumnIndexOrThrow(COLUMN_ARTIST)),
            durationMs = getLong(getColumnIndexOrThrow(COLUMN_DURATION_MS)),
            folderUri = getString(getColumnIndexOrThrow(COLUMN_FOLDER_URI)),
            folderName = getString(getColumnIndexOrThrow(COLUMN_FOLDER_NAME)),
            relativePath = getString(getColumnIndexOrThrow(COLUMN_RELATIVE_PATH)),
            contentHash = hash.takeIf {
                !it.isNullOrBlank() && hashAlgorithm == CONTENT_HASH_ALGORITHM
            },
        )
    }

    private fun AudioItem.toContentValues(): ContentValues = ContentValues().apply {
        put(COLUMN_URI, uri.toString())
        put(COLUMN_TITLE, title)
        put(COLUMN_ARTIST, artist)
        put(COLUMN_DURATION_MS, durationMs)
        put(COLUMN_FOLDER_URI, folderUri)
        put(COLUMN_FOLDER_NAME, folderName)
        put(COLUMN_RELATIVE_PATH, relativePath)
        contentHash?.let {
            put(COLUMN_CONTENT_HASH_ALGORITHM, CONTENT_HASH_ALGORITHM)
            put(COLUMN_CONTENT_HASH, it)
        }
    }

    private class LibraryDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_AUDIO_ITEMS (
                    $COLUMN_URI TEXT NOT NULL PRIMARY KEY,
                    $COLUMN_TITLE TEXT NOT NULL,
                    $COLUMN_ARTIST TEXT NOT NULL,
                    $COLUMN_DURATION_MS INTEGER NOT NULL,
                    $COLUMN_FOLDER_URI TEXT NOT NULL,
                    $COLUMN_FOLDER_NAME TEXT NOT NULL,
                    $COLUMN_RELATIVE_PATH TEXT NOT NULL,
                    $COLUMN_CONTENT_HASH_ALGORITHM TEXT,
                    $COLUMN_CONTENT_HASH TEXT
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 1 is the initial SQLite schema. Future schema changes belong here.
        }
    }

    private companion object {
        const val DATABASE_NAME = "library.db"
        const val DATABASE_VERSION = 1
        const val TABLE_AUDIO_ITEMS = "audio_items"
        const val COLUMN_URI = "uri"
        const val COLUMN_TITLE = "title"
        const val COLUMN_ARTIST = "artist"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_FOLDER_URI = "folder_uri"
        const val COLUMN_FOLDER_NAME = "folder_name"
        const val COLUMN_RELATIVE_PATH = "relative_path"
        const val COLUMN_CONTENT_HASH_ALGORITHM = "content_hash_algorithm"
        const val COLUMN_CONTENT_HASH = "content_hash"
        val AUDIO_ITEM_COLUMNS = arrayOf(
            COLUMN_URI,
            COLUMN_TITLE,
            COLUMN_ARTIST,
            COLUMN_DURATION_MS,
            COLUMN_FOLDER_URI,
            COLUMN_FOLDER_NAME,
            COLUMN_RELATIVE_PATH,
            COLUMN_CONTENT_HASH_ALGORITHM,
            COLUMN_CONTENT_HASH,
        )
        const val KEY_FOLDERS = "folders_json"
    }
}

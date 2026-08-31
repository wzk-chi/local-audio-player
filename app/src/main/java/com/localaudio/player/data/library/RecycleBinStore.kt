package com.localaudio.player.data.library

import android.content.ContentValues
import android.database.Cursor
import com.localaudio.player.data.database.AudioDatabase
import com.localaudio.player.data.hash.CONTENT_HASH_ALGORITHM
import com.localaudio.player.data.model.RecycleFolder
import com.localaudio.player.data.model.RecycleItem

class RecycleBinStore(private val database: AudioDatabase) {
    fun readState(): com.localaudio.player.data.model.RecycleBinState = runCatching {
        val items = database.readableDatabase.query(
            AudioDatabase.TABLE_RECYCLE_ITEMS,
            ITEM_COLUMNS,
            null,
            null,
            null,
            null,
            "${AudioDatabase.COLUMN_DELETED_AT_MS} DESC, ${AudioDatabase.COLUMN_URI} ASC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toRecycleItem()?.let(::add) }
        }
        val folders = database.readableDatabase.query(
            AudioDatabase.TABLE_RECYCLE_FOLDERS,
            FOLDER_COLUMNS,
            null,
            null,
            null,
            null,
            "${AudioDatabase.COLUMN_DELETED_AT_MS} DESC, ${AudioDatabase.COLUMN_URI} ASC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toRecycleFolder()) }
        }
        com.localaudio.player.data.model.RecycleBinState(items, folders)
    }.getOrDefault(com.localaudio.player.data.model.RecycleBinState())

    fun writeState(state: com.localaudio.player.data.model.RecycleBinState) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete(AudioDatabase.TABLE_RECYCLE_ITEMS, null, null)
            state.items.distinctBy { it.key }.forEach { item ->
                db.insertOrThrow(AudioDatabase.TABLE_RECYCLE_ITEMS, null, item.toContentValues())
            }
            db.delete(AudioDatabase.TABLE_RECYCLE_FOLDERS, null, null)
            state.folders.distinctBy { it.key }.forEach { folder ->
                db.insertOrThrow(AudioDatabase.TABLE_RECYCLE_FOLDERS, null, folder.toContentValues())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toRecycleItem(): RecycleItem? = runCatching {
        val hash = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH))
        val algorithm = getString(
            getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM),
        )
        RecycleItem(
            uri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_URI)),
            contentHash = hash.takeIf { algorithm == CONTENT_HASH_ALGORITHM && !it.isNullOrBlank() },
            title = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_TITLE)),
            artist = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_ARTIST)),
            durationMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_DURATION_MS)),
            folderUri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_FOLDER_URI)),
            folderName = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_FOLDER_NAME)),
            relativePath = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_RELATIVE_PATH)),
            deletedAtMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_DELETED_AT_MS)),
            deletedWithFolder = getInt(
                getColumnIndexOrThrow(AudioDatabase.COLUMN_DELETED_WITH_FOLDER),
            ) != 0,
        )
    }.getOrNull()

    private fun Cursor.toRecycleFolder(): RecycleFolder = RecycleFolder(
        uri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_URI)),
        rootFolderUri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_ROOT_FOLDER_URI)),
        relativePath = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_RELATIVE_PATH)),
        title = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_TITLE)),
        deletedAtMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_DELETED_AT_MS)),
    )

    private fun RecycleItem.toContentValues() = ContentValues().apply {
        put(AudioDatabase.COLUMN_URI, uri)
        contentHash?.let {
            put(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM, CONTENT_HASH_ALGORITHM)
            put(AudioDatabase.COLUMN_CONTENT_HASH, it)
        }
        put(AudioDatabase.COLUMN_TITLE, title)
        put(AudioDatabase.COLUMN_ARTIST, artist)
        put(AudioDatabase.COLUMN_DURATION_MS, durationMs)
        put(AudioDatabase.COLUMN_FOLDER_URI, folderUri)
        put(AudioDatabase.COLUMN_FOLDER_NAME, folderName)
        put(AudioDatabase.COLUMN_RELATIVE_PATH, relativePath)
        put(AudioDatabase.COLUMN_DELETED_AT_MS, deletedAtMs)
        put(AudioDatabase.COLUMN_DELETED_WITH_FOLDER, if (deletedWithFolder) 1 else 0)
    }

    private fun RecycleFolder.toContentValues() = ContentValues().apply {
        put(AudioDatabase.COLUMN_URI, uri)
        put(AudioDatabase.COLUMN_ROOT_FOLDER_URI, rootFolderUri)
        put(AudioDatabase.COLUMN_RELATIVE_PATH, relativePath)
        put(AudioDatabase.COLUMN_TITLE, title)
        put(AudioDatabase.COLUMN_DELETED_AT_MS, deletedAtMs)
    }

    private companion object {
        val ITEM_COLUMNS = arrayOf(
            AudioDatabase.COLUMN_URI,
            AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM,
            AudioDatabase.COLUMN_CONTENT_HASH,
            AudioDatabase.COLUMN_TITLE,
            AudioDatabase.COLUMN_ARTIST,
            AudioDatabase.COLUMN_DURATION_MS,
            AudioDatabase.COLUMN_FOLDER_URI,
            AudioDatabase.COLUMN_FOLDER_NAME,
            AudioDatabase.COLUMN_RELATIVE_PATH,
            AudioDatabase.COLUMN_DELETED_AT_MS,
            AudioDatabase.COLUMN_DELETED_WITH_FOLDER,
        )
        val FOLDER_COLUMNS = arrayOf(
            AudioDatabase.COLUMN_URI,
            AudioDatabase.COLUMN_ROOT_FOLDER_URI,
            AudioDatabase.COLUMN_RELATIVE_PATH,
            AudioDatabase.COLUMN_TITLE,
            AudioDatabase.COLUMN_DELETED_AT_MS,
        )
    }
}

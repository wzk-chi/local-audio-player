package com.localaudio.player.data.skip

import android.content.ContentValues
import android.database.Cursor
import com.localaudio.player.data.database.AudioDatabase
import com.localaudio.player.data.hash.CONTENT_HASH_ALGORITHM
import com.localaudio.player.data.model.AutoSkipSegment

class AutoSkipStore(private val database: AudioDatabase) {
    fun readSegments(): List<AutoSkipSegment> = runCatching {
        database.readableDatabase.query(
            AudioDatabase.TABLE_AUTO_SKIP_SEGMENTS,
            SEGMENT_COLUMNS,
            null,
            null,
            null,
            null,
            "${AudioDatabase.COLUMN_MODIFIED_AT_MS} ASC, ${AudioDatabase.COLUMN_ID} ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.toSegment()?.let(::add)
                }
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun writeSegments(segments: List<AutoSkipSegment>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete(AudioDatabase.TABLE_AUTO_SKIP_SEGMENTS, null, null)
            segments.distinctBy { it.id }.forEach { segment ->
                db.insertOrThrow(
                    AudioDatabase.TABLE_AUTO_SKIP_SEGMENTS,
                    null,
                    segment.toContentValues(),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toSegment(): AutoSkipSegment? = runCatching {
        val contentHash = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH))
        val hashAlgorithm = getString(
            getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM),
        )
        val segment = AutoSkipSegment(
            id = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_ID)),
            contentHash = contentHash,
            audioUri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_AUDIO_URI)),
            folderUri = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_FOLDER_URI)),
            titleSnapshot = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_TITLE_SNAPSHOT)),
            folderNameSnapshot = getString(
                getColumnIndexOrThrow(AudioDatabase.COLUMN_FOLDER_NAME_SNAPSHOT),
            ),
            relativePath = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_RELATIVE_PATH)),
            startMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_START_MS)),
            endMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_END_MS)),
            modifiedAtMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_MODIFIED_AT_MS)),
        )
        segment.takeIf {
            hashAlgorithm == CONTENT_HASH_ALGORITHM &&
                contentHash.isNotBlank() &&
                it.startMs >= 0L &&
                it.endMs > it.startMs
        }
    }.getOrNull()

    private fun AutoSkipSegment.toContentValues(): ContentValues = ContentValues().apply {
        put(AudioDatabase.COLUMN_ID, id)
        put(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM, CONTENT_HASH_ALGORITHM)
        put(AudioDatabase.COLUMN_CONTENT_HASH, contentHash)
        put(AudioDatabase.COLUMN_AUDIO_URI, audioUri)
        put(AudioDatabase.COLUMN_FOLDER_URI, folderUri)
        put(AudioDatabase.COLUMN_TITLE_SNAPSHOT, titleSnapshot)
        put(AudioDatabase.COLUMN_FOLDER_NAME_SNAPSHOT, folderNameSnapshot)
        put(AudioDatabase.COLUMN_RELATIVE_PATH, relativePath)
        put(AudioDatabase.COLUMN_START_MS, startMs)
        put(AudioDatabase.COLUMN_END_MS, endMs)
        put(AudioDatabase.COLUMN_MODIFIED_AT_MS, modifiedAtMs)
    }

    private companion object {
        val SEGMENT_COLUMNS = arrayOf(
            AudioDatabase.COLUMN_ID,
            AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM,
            AudioDatabase.COLUMN_CONTENT_HASH,
            AudioDatabase.COLUMN_AUDIO_URI,
            AudioDatabase.COLUMN_FOLDER_URI,
            AudioDatabase.COLUMN_TITLE_SNAPSHOT,
            AudioDatabase.COLUMN_FOLDER_NAME_SNAPSHOT,
            AudioDatabase.COLUMN_RELATIVE_PATH,
            AudioDatabase.COLUMN_START_MS,
            AudioDatabase.COLUMN_END_MS,
            AudioDatabase.COLUMN_MODIFIED_AT_MS,
        )
    }
}

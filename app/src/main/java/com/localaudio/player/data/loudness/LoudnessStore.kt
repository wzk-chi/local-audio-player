package com.localaudio.player.data.loudness

import android.content.ContentValues
import android.database.Cursor
import com.localaudio.player.data.database.AudioDatabase
import com.localaudio.player.data.hash.CONTENT_HASH_ALGORITHM

class LoudnessStore(private val database: AudioDatabase) {
    fun readAll(): List<AudioLoudness> = runCatching {
        database.readableDatabase.query(
            AudioDatabase.TABLE_AUDIO_LOUDNESS,
            COLUMNS,
            "${AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM} = ? AND " +
                "${AudioDatabase.COLUMN_ANALYSIS_VERSION} = ?",
            arrayOf(CONTENT_HASH_ALGORITHM, LOUDNESS_ANALYSIS_VERSION.toString()),
            null,
            null,
            "${AudioDatabase.COLUMN_ANALYZED_AT_MS} DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.toLoudness()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    fun upsert(loudness: AudioLoudness) {
        val values = ContentValues().apply {
            put(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM, CONTENT_HASH_ALGORITHM)
            put(AudioDatabase.COLUMN_CONTENT_HASH, loudness.contentHash)
            put(AudioDatabase.COLUMN_INTEGRATED_LUFS, loudness.integratedLufs)
            put(AudioDatabase.COLUMN_PEAK, loudness.peak)
            put(AudioDatabase.COLUMN_ANALYSIS_VERSION, loudness.analysisVersion)
            put(AudioDatabase.COLUMN_ANALYZED_AT_MS, loudness.analyzedAtMs)
        }
        database.writableDatabase.insertWithOnConflict(
            AudioDatabase.TABLE_AUDIO_LOUDNESS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun Cursor.toLoudness(): AudioLoudness? = runCatching {
        val hash = getString(getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH))
        val algorithm = getString(
            getColumnIndexOrThrow(AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM),
        )
        AudioLoudness(
            contentHash = hash,
            integratedLufs = getFloat(getColumnIndexOrThrow(AudioDatabase.COLUMN_INTEGRATED_LUFS)),
            peak = getFloat(getColumnIndexOrThrow(AudioDatabase.COLUMN_PEAK)),
            analysisVersion = getInt(getColumnIndexOrThrow(AudioDatabase.COLUMN_ANALYSIS_VERSION)),
            analyzedAtMs = getLong(getColumnIndexOrThrow(AudioDatabase.COLUMN_ANALYZED_AT_MS)),
        ).takeIf {
            algorithm == CONTENT_HASH_ALGORITHM &&
                it.contentHash.isNotBlank() &&
                it.integratedLufs.isFinite() &&
                it.peak.isFinite() &&
                it.peak >= 0f &&
                it.analysisVersion == LOUDNESS_ANALYSIS_VERSION
        }
    }.getOrNull()

    private companion object {
        val COLUMNS = arrayOf(
            AudioDatabase.COLUMN_CONTENT_HASH_ALGORITHM,
            AudioDatabase.COLUMN_CONTENT_HASH,
            AudioDatabase.COLUMN_INTEGRATED_LUFS,
            AudioDatabase.COLUMN_PEAK,
            AudioDatabase.COLUMN_ANALYSIS_VERSION,
            AudioDatabase.COLUMN_ANALYZED_AT_MS,
        )
    }
}

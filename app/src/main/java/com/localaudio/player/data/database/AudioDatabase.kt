package com.localaudio.player.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AudioDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createAudioItemsTable(db)
        createAutoSkipSegmentsTable(db)
        createAudioLoudnessTable(db)
        createRecycleItemsTable(db)
        createRecycleFoldersTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < VERSION_AUTO_SKIP_TABLE) {
            createAutoSkipSegmentsTable(db)
        }
        if (oldVersion < VERSION_RECYCLE_SCOPE) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_RECYCLE_ITEMS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_RECYCLE_FOLDERS")
            createRecycleItemsTable(db)
            createRecycleFoldersTable(db)
        }
        if (oldVersion < VERSION_AUDIO_METADATA) {
            db.execSQL(
                "ALTER TABLE $TABLE_AUDIO_ITEMS ADD COLUMN $COLUMN_SIZE_BYTES INTEGER NOT NULL DEFAULT -1",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_AUDIO_ITEMS ADD COLUMN $COLUMN_LAST_MODIFIED_MS INTEGER NOT NULL DEFAULT -1",
            )
        }
        if (oldVersion < VERSION_AUDIO_FOLDER_INDEX) {
            createAudioItemsFolderIndex(db)
        }
        if (oldVersion < VERSION_AUDIO_LOUDNESS) {
            createAudioLoudnessTable(db)
        }
    }

    private fun createAudioItemsTable(db: SQLiteDatabase) {
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
                $COLUMN_SIZE_BYTES INTEGER NOT NULL DEFAULT -1,
                $COLUMN_LAST_MODIFIED_MS INTEGER NOT NULL DEFAULT -1,
                $COLUMN_CONTENT_HASH_ALGORITHM TEXT,
                $COLUMN_CONTENT_HASH TEXT
            )
            """.trimIndent(),
        )
        createAudioItemsFolderIndex(db)
    }

    private fun createAudioItemsFolderIndex(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS ${TABLE_AUDIO_ITEMS}_folder_uri_index
            ON $TABLE_AUDIO_ITEMS ($COLUMN_FOLDER_URI)
            """.trimIndent(),
        )
    }

    private fun createAutoSkipSegmentsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AUTO_SKIP_SEGMENTS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_CONTENT_HASH_ALGORITHM TEXT NOT NULL,
                $COLUMN_CONTENT_HASH TEXT NOT NULL,
                $COLUMN_AUDIO_URI TEXT NOT NULL,
                $COLUMN_FOLDER_URI TEXT NOT NULL,
                $COLUMN_TITLE_SNAPSHOT TEXT NOT NULL,
                $COLUMN_FOLDER_NAME_SNAPSHOT TEXT NOT NULL,
                $COLUMN_RELATIVE_PATH TEXT NOT NULL,
                $COLUMN_START_MS INTEGER NOT NULL,
                $COLUMN_END_MS INTEGER NOT NULL,
                $COLUMN_MODIFIED_AT_MS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS ${TABLE_AUTO_SKIP_SEGMENTS}_hash_index
            ON $TABLE_AUTO_SKIP_SEGMENTS (
                $COLUMN_CONTENT_HASH_ALGORITHM,
                $COLUMN_CONTENT_HASH
            )
            """.trimIndent(),
        )
    }

    private fun createAudioLoudnessTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AUDIO_LOUDNESS (
                $COLUMN_CONTENT_HASH_ALGORITHM TEXT NOT NULL,
                $COLUMN_CONTENT_HASH TEXT NOT NULL,
                $COLUMN_INTEGRATED_LUFS REAL NOT NULL,
                $COLUMN_PEAK REAL NOT NULL,
                $COLUMN_ANALYSIS_VERSION INTEGER NOT NULL,
                $COLUMN_ANALYZED_AT_MS INTEGER NOT NULL,
                PRIMARY KEY ($COLUMN_CONTENT_HASH_ALGORITHM, $COLUMN_CONTENT_HASH)
            )
            """.trimIndent(),
        )
    }

    private fun createRecycleItemsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECYCLE_ITEMS (
                $COLUMN_URI TEXT NOT NULL PRIMARY KEY,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_ARTIST TEXT NOT NULL,
                $COLUMN_DURATION_MS INTEGER NOT NULL,
                $COLUMN_FOLDER_URI TEXT NOT NULL,
                $COLUMN_FOLDER_NAME TEXT NOT NULL,
                $COLUMN_RELATIVE_PATH TEXT NOT NULL,
                $COLUMN_DELETED_AT_MS INTEGER NOT NULL,
                $COLUMN_DELETED_BY_FOLDER_URI TEXT
            )
            """.trimIndent(),
        )
    }

    private fun createRecycleFoldersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECYCLE_FOLDERS (
                $COLUMN_URI TEXT NOT NULL PRIMARY KEY,
                $COLUMN_ROOT_FOLDER_URI TEXT NOT NULL,
                $COLUMN_RELATIVE_PATH TEXT NOT NULL,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_DELETED_AT_MS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    companion object {
        const val TABLE_AUDIO_ITEMS = "audio_items"
        const val COLUMN_URI = "uri"
        const val COLUMN_TITLE = "title"
        const val COLUMN_ARTIST = "artist"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_FOLDER_URI = "folder_uri"
        const val COLUMN_FOLDER_NAME = "folder_name"
        const val COLUMN_RELATIVE_PATH = "relative_path"
        const val COLUMN_SIZE_BYTES = "size_bytes"
        const val COLUMN_LAST_MODIFIED_MS = "last_modified_ms"
        const val COLUMN_CONTENT_HASH_ALGORITHM = "content_hash_algorithm"
        const val COLUMN_CONTENT_HASH = "content_hash"

        const val TABLE_AUTO_SKIP_SEGMENTS = "auto_skip_segments"
        const val COLUMN_ID = "id"
        const val COLUMN_AUDIO_URI = "audio_uri"
        const val COLUMN_TITLE_SNAPSHOT = "title_snapshot"
        const val COLUMN_FOLDER_NAME_SNAPSHOT = "folder_name_snapshot"
        const val COLUMN_START_MS = "start_ms"
        const val COLUMN_END_MS = "end_ms"
        const val COLUMN_MODIFIED_AT_MS = "modified_at_ms"

        const val TABLE_AUDIO_LOUDNESS = "audio_loudness"
        const val COLUMN_INTEGRATED_LUFS = "integrated_lufs"
        const val COLUMN_PEAK = "peak"
        const val COLUMN_ANALYSIS_VERSION = "analysis_version"
        const val COLUMN_ANALYZED_AT_MS = "analyzed_at_ms"

        const val TABLE_RECYCLE_ITEMS = "recycle_items"
        const val COLUMN_DELETED_AT_MS = "deleted_at_ms"
        const val COLUMN_DELETED_BY_FOLDER_URI = "deleted_by_folder_uri"

        const val TABLE_RECYCLE_FOLDERS = "recycle_folders"
        const val COLUMN_ROOT_FOLDER_URI = "root_folder_uri"

        private const val DATABASE_NAME = "library.db"
        private const val DATABASE_VERSION = 8
        private const val VERSION_AUTO_SKIP_TABLE = 2
        private const val VERSION_RECYCLE_SCOPE = 5
        private const val VERSION_AUDIO_METADATA = 6
        private const val VERSION_AUDIO_FOLDER_INDEX = 7
        private const val VERSION_AUDIO_LOUDNESS = 8
    }
}

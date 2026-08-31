package com.localaudio.player.data.model

/** A persisted interval that should be skipped for one audio document. */
data class AutoSkipSegment(
    val id: String,
    /** Content identity of the audio. URI/path is intentionally not used for matching. */
    val contentHash: String,
    val audioUri: String,
    val folderUri: String,
    val titleSnapshot: String,
    val folderNameSnapshot: String,
    val relativePath: String,
    val startMs: Long,
    val endMs: Long,
    val modifiedAtMs: Long,
)

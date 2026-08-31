package com.localaudio.player.data.model

/** A directory-scoped rule for skipping the beginning and/or end of every audio in that directory. */
data class DirectorySkipRule(
    val folderUri: String,
    val relativePath: String,
    val startSeconds: Long,
    val endSeconds: Long,
    val modifiedAtMs: Long,
) {
    val key: String get() = "$folderUri\u0000$relativePath"
}

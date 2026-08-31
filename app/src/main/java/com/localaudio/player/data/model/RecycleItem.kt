package com.localaudio.player.data.model

/** A soft-deleted audio source kept in the recycle bin. */
data class RecycleItem(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val folderUri: String,
    val folderName: String,
    val relativePath: String,
    val deletedAtMs: Long,
    /** URI of the deleted folder that owns this item, or null for an individual delete. */
    val deletedByFolderUri: String? = null,
) {
    val key: String get() = uri
}

/** A soft-deleted directory. Its audio children are stored as RecycleItem entries too. */
data class RecycleFolder(
    val uri: String,
    val rootFolderUri: String,
    val relativePath: String,
    val title: String,
    val deletedAtMs: Long,
) {
    val key: String get() = uri
}

data class RecycleBinState(
    val items: List<RecycleItem> = emptyList(),
    val folders: List<RecycleFolder> = emptyList(),
) {
    val entryCount: Int get() = items.size + folders.size
}

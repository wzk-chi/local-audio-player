package com.localaudio.player.data.model

/** 用户添加的文件夹（SAF tree uri 持久化授权）。 */
data class FolderItem(
    val uri: String,
    val displayName: String,
)

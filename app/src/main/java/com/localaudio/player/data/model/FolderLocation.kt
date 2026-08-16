package com.localaudio.player.data.model

/** 首页上次打开的位置。relativePath 为空表示所选根文件夹。 */
data class FolderLocation(
    val folderUri: String,
    val rootName: String,
    val relativePath: String,
    val name: String,
) {
    fun parent(): FolderLocation? {
        if (relativePath.isEmpty()) return null
        val parentPath = relativePath.substringBeforeLast('/', "")
        return copy(
            relativePath = parentPath,
            name = parentPath.substringAfterLast('/').ifEmpty { rootName },
        )
    }
}

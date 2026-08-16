package com.localaudio.player.data.model

import android.net.Uri
import java.util.Locale

/** 单个音频文件。扫描结果快照，不可变。 */
data class AudioItem(
    val uri: Uri,
    /** 标题：文件名去掉扩展名。 */
    val title: String,
    /** 艺术家：v1 使用来源文件夹名占位。 */
    val artist: String,
    /** 时长（ms）。扫描阶段为 0，播放时由媒体源填充。 */
    val durationMs: Long = 0L,
    /** 来源文件夹的 tree uri 字符串。 */
    val folderUri: String,
    /** 来源文件夹显示名。 */
    val folderName: String,
    /** 相对来源根文件夹的目录路径（不含文件名），如 "流行/华语"；根目录下为空串。 */
    val relativePath: String = "",
) {
    /** 去重用的稳定键。 */
    val key: String get() = uri.toString().lowercase(Locale.ROOT)
}

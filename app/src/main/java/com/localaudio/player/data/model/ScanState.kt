package com.localaudio.player.data.model

/** 单个文件夹的扫描状态。 */
sealed interface ScanState {
    data object Idle : ScanState

    data class Scanning(val scanned: Int, val found: Int) : ScanState

    data class Done(val audioCount: Int) : ScanState

    data class Failed(val reason: String) : ScanState
}

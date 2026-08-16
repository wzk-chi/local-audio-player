package com.localaudio.player.data.library

import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderItem
import com.localaudio.player.data.model.ScanState

data class LibraryState(
    val folders: List<FolderItem> = emptyList(),
    val items: List<AudioItem> = emptyList(),
    val scanStates: Map<String, ScanState> = emptyMap(),
)

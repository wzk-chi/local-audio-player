package com.localaudio.player.app

import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.FolderLocation

sealed interface HomeRow {
    data class Directory(val location: FolderLocation) : HomeRow
    data class Audio(val item: AudioItem) : HomeRow
}

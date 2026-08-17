package com.localaudio.player.playback

sealed interface QueueMove {
    data class Select(val index: Int) : QueueMove
    data object Stop : QueueMove
}

class QueueNavigator(
    private val randomIndex: (IntRange) -> Int = { it.random() },
) {
    fun next(
        queueSize: Int,
        currentIndex: Int,
        repeatMode: Int,
        shuffle: Boolean,
        automatic: Boolean,
    ): QueueMove {
        if (queueSize <= 0) return QueueMove.Stop
        val safeIndex = currentIndex.coerceIn(0, queueSize - 1)
        if (automatic && repeatMode == 1) return QueueMove.Select(safeIndex)
        if (shuffle && queueSize > 1) {
            val candidates = (0 until queueSize).filterNot { it == safeIndex }
            return QueueMove.Select(candidates[randomIndex(candidates.indices)])
        }
        return when {
            safeIndex < queueSize - 1 -> QueueMove.Select(safeIndex + 1)
            repeatMode == 2 -> QueueMove.Select(0)
            else -> QueueMove.Stop
        }
    }

    fun previous(
        queueSize: Int,
        currentIndex: Int,
        repeatMode: Int,
    ): QueueMove {
        if (queueSize <= 0) return QueueMove.Stop
        val safeIndex = currentIndex.coerceIn(0, queueSize - 1)
        return when {
            safeIndex > 0 -> QueueMove.Select(safeIndex - 1)
            repeatMode == 2 -> QueueMove.Select(queueSize - 1)
            else -> QueueMove.Select(0)
        }
    }
}

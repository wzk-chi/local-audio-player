package com.localaudio.player.data.skip

import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AutoSkipSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AutoSkipRepository(
    private val store: AutoSkipStore,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(store.readSegments())

    val state: StateFlow<List<AutoSkipSegment>> = _state.asStateFlow()

    fun add(item: AudioItem, startMs: Long, endMs: Long): AutoSkipSegment? {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(0L)
        if (end <= start) return null
        val contentHash = item.contentHash?.takeIf { it.isNotBlank() } ?: return null
        val segment = AutoSkipSegment(
            id = UUID.randomUUID().toString(),
            contentHash = contentHash,
            audioUri = item.uri.toString(),
            folderUri = item.folderUri,
            titleSnapshot = item.title,
            folderNameSnapshot = item.folderName,
            relativePath = item.relativePath,
            startMs = start,
            endMs = end,
            modifiedAtMs = nextModifiedAtMs(),
        )
        updateSegments { it + segment }
        return segment
    }

    fun delete(id: String) {
        updateSegments { segments -> segments.filterNot { it.id == id } }
    }

    fun update(id: String, startMs: Long, endMs: Long): AutoSkipSegment? {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(0L)
        if (end <= start) return null
        val existing = _state.value.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(
            startMs = start,
            endMs = end,
            modifiedAtMs = nextModifiedAtMs(),
        )
        updateSegments { segments ->
            segments.map { segment -> if (segment.id == id) updated else segment }
        }
        return updated
    }

    fun segmentsFor(contentHash: String): List<AutoSkipSegment> = _state.value
        .asSequence()
        .filter { it.contentHash == contentHash }
        .sortedBy { it.startMs }
        .toList()

    fun reconcile(validContentHashes: Set<String>, unresolvedAudioUris: Set<String>) {
        updateSegments { segments ->
            segments.filterNot { segment ->
                segment.contentHash !in validContentHashes && segment.audioUri !in unresolvedAudioUris
            }
        }
    }

    private fun updateSegments(transform: (List<AutoSkipSegment>) -> List<AutoSkipSegment>) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        persist(next)
    }

    private fun persist(segments: List<AutoSkipSegment>) {
        executor.execute { runCatching { store.writeSegments(segments) } }
    }

    private fun nextModifiedAtMs(): Long {
        val now = System.currentTimeMillis()
        val latest = _state.value.maxOfOrNull { it.modifiedAtMs } ?: Long.MIN_VALUE
        return if (latest == Long.MAX_VALUE) {
            latest
        } else {
            maxOf(now, latest + 1L)
        }
    }
}

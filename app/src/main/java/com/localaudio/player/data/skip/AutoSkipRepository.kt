package com.localaudio.player.data.skip

import android.net.Uri
import com.localaudio.player.data.model.AudioItem
import com.localaudio.player.data.model.AutoSkipSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AutoSkipRepository(
    private val store: AutoSkipStore,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val loadedLatch = CountDownLatch(1)
    @Volatile
    private var loaded = false
    private val _state = MutableStateFlow<List<AutoSkipSegment>>(emptyList())
    @Volatile
    private var index = SegmentIndex(
        byContentHash = buildSegmentIndex(_state.value),
        revision = 0L,
    )

    val state: StateFlow<List<AutoSkipSegment>> = _state.asStateFlow()

    /** Changes whenever the indexed rule set changes, so playback can invalidate its cache. */
    val revision: Long
        get() = index.revision

    init {
        executor.execute {
            val saved = runCatching { store.readSegments() }.getOrDefault(emptyList())
            index = SegmentIndex(
                byContentHash = buildSegmentIndex(saved),
                revision = nextRevision(index.revision),
            )
            _state.value = saved
            loaded = true
            loadedLatch.countDown()
        }
    }

    @Synchronized
    fun add(item: AudioItem, startMs: Long, endMs: Long): AutoSkipSegment? {
        if (!awaitLoaded()) return null
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

    @Synchronized
    fun delete(id: String) {
        if (!awaitLoaded()) return
        updateSegments { segments -> segments.filterNot { it.id == id } }
    }

    @Synchronized
    fun update(id: String, startMs: Long, endMs: Long): AutoSkipSegment? {
        if (!awaitLoaded()) return null
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

    fun segmentsFor(contentHash: String): List<AutoSkipSegment> =
        index.byContentHash[contentHash].orEmpty()

    @Synchronized
    fun reconcile(validContentHashes: Set<String>, unresolvedAudioUris: Set<String>) {
        if (!awaitLoaded()) return
        updateSegments { segments ->
            segments.filterNot { segment ->
                segment.contentHash !in validContentHashes && segment.audioUri !in unresolvedAudioUris
            }
        }
    }

    /** Refreshes the display/playback snapshot after a completed media scan. */
    @Synchronized
    fun updateSnapshots(items: List<AudioItem>) {
        if (!awaitLoaded()) return
        val byUri = items.associateBy { normalizeUri(it.uri.toString()) }
        val byHash = items.filter { !it.contentHash.isNullOrBlank() }
            .groupBy { it.contentHash }
        updateSegments { segments ->
            segments.map { segment ->
                val item = byUri[normalizeUri(segment.audioUri)]
                    ?: byHash[segment.contentHash]?.singleOrNull()
                item?.let { segment.withAudioSnapshot(it) } ?: segment
            }
        }
    }

    @Synchronized
    fun updateAudioSnapshot(previousUri: String, item: AudioItem) {
        if (!awaitLoaded()) return
        updateSegments { segments ->
            segments.map { segment ->
                if (segment.contentHash == item.contentHash &&
                    normalizeUri(segment.audioUri) == normalizeUri(previousUri)
                ) {
                    segment.withAudioSnapshot(item)
                } else {
                    segment
                }
            }
        }
    }

    @Synchronized
    fun updateRootFolderSnapshot(folderUri: String, folderName: String) {
        if (!awaitLoaded()) return
        updateSegments { segments ->
            segments.map { segment ->
                if (segment.folderUri == folderUri) {
                    segment.copy(folderNameSnapshot = folderName)
                } else segment
            }
        }
    }

    @Synchronized
    fun updatePathSnapshot(folderUri: String, oldPath: String, newPath: String) {
        if (!awaitLoaded()) return
        updateSegments { segments ->
            segments.map { segment ->
                if (segment.folderUri == folderUri && isInPath(segment.relativePath, oldPath)) {
                    segment.copy(relativePath = replacePath(segment.relativePath, oldPath, newPath))
                } else segment
            }
        }
    }

    private fun updateSegments(transform: (List<AutoSkipSegment>) -> List<AutoSkipSegment>) {
        val current = _state.value
        val next = transform(current)
        if (next == current) return
        val currentIndex = index
        index = SegmentIndex(
            byContentHash = buildSegmentIndex(next),
            revision = nextRevision(currentIndex.revision),
        )
        _state.value = next
        persist(next)
    }

    private fun persist(segments: List<AutoSkipSegment>) {
        synchronized(this) {
            pendingPersistence = segments
            if (persistenceScheduled) return
            persistenceScheduled = true
        }
        executor.execute(::drainPersistence)
    }

    private fun drainPersistence() {
        while (true) {
            val segments = synchronized(this) {
                val next = pendingPersistence ?: run {
                    persistenceScheduled = false
                    return
                }
                pendingPersistence = null
                next
            }
            runCatching { store.writeSegments(segments) }
        }
    }

    private var pendingPersistence: List<AutoSkipSegment>? = null
    private var persistenceScheduled = false

    private fun awaitLoaded(): Boolean {
        if (loaded) return true
        return runCatching { loadedLatch.await() }
            .onFailure { Thread.currentThread().interrupt() }
            .isSuccess && loaded
    }

    private fun AutoSkipSegment.withAudioSnapshot(item: AudioItem) = copy(
        audioUri = item.uri.toString(),
        folderUri = item.folderUri,
        titleSnapshot = item.title,
        folderNameSnapshot = item.folderName,
        relativePath = item.relativePath,
    )

    private fun normalizeUri(value: String): String = Uri.parse(value).normalizeScheme().toString()

    private fun nextModifiedAtMs(): Long {
        val now = System.currentTimeMillis()
        val latest = _state.value.maxOfOrNull { it.modifiedAtMs } ?: Long.MIN_VALUE
        return if (latest == Long.MAX_VALUE) {
            latest
        } else {
            maxOf(now, latest + 1L)
        }
    }

    private data class SegmentIndex(
        val byContentHash: Map<String, List<AutoSkipSegment>>,
        val revision: Long,
    )

    private companion object {
        fun buildSegmentIndex(
            segments: List<AutoSkipSegment>,
        ): Map<String, List<AutoSkipSegment>> =
            segments.groupBy { it.contentHash }
                .mapValues { (_, sameHash) -> sameHash.sortedBy { it.startMs } }

        fun nextRevision(current: Long): Long =
            if (current == Long.MAX_VALUE) 0L else current + 1L

        fun isInPath(path: String, parent: String): Boolean =
            path == parent || (parent.isNotEmpty() && path.startsWith("$parent/"))

        fun replacePath(path: String, oldPath: String, newPath: String): String =
            if (path == oldPath) newPath else newPath + path.removePrefix(oldPath)
    }
}

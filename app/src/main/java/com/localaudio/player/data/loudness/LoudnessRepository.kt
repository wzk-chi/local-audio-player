package com.localaudio.player.data.loudness

import android.os.Process
import com.localaudio.player.data.model.AudioItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Provides hash-keyed loudness results and starts analysis only when a track is played. */
class LoudnessRepository(
    private val store: LoudnessStore,
    private val analyzer: LoudnessAnalyzer,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            task.run()
        }.apply { name = "local-audio-loudness" }
    }
    private val pendingHashes = ConcurrentHashMap.newKeySet<String>()
    private val failedSources = ConcurrentHashMap.newKeySet<AnalysisKey>()
    private val _state = MutableStateFlow<List<AudioLoudness>>(emptyList())
    private val _revision = MutableStateFlow(0L)
    private val resultsByHash = HashMap<String, AudioLoudness>()
    private var latestRequest: AudioItem? = null
    private var analysisWorkerScheduled = false

    init {
        executor.execute {
            runCatching { store.pruneUnreferenced() }
            val saved = runCatching { store.readAll() }.getOrDefault(emptyList())
            synchronized(this) {
                saved.forEach { result -> resultsByHash[result.contentHash] = result }
                _state.value = resultsByHash.values.sortedByDescending { it.analyzedAtMs }
                _revision.value = nextRevision(_revision.value)
            }
        }
    }

    val state: StateFlow<List<AudioLoudness>> = _state.asStateFlow()
    /** Changes when an analysis finishes, including a failed analysis. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Synchronized
    fun resultFor(contentHash: String): AudioLoudness? = resultsByHash[contentHash]

    fun isAnalysisPending(contentHash: String): Boolean = contentHash in pendingHashes

    @Synchronized
    fun ensureAnalysis(item: AudioItem) {
        val contentHash = item.contentHash?.takeIf { it.isNotBlank() } ?: return
        val source = AnalysisKey(contentHash = contentHash, uri = item.key)
        if (resultsByHash.containsKey(contentHash) ||
            source in failedSources ||
            !pendingHashes.add(contentHash)
        ) return
        latestRequest?.contentHash?.let(pendingHashes::remove)
        latestRequest = item
        if (!analysisWorkerScheduled) {
            analysisWorkerScheduled = true
            executor.execute(::runQueuedAnalysis)
        }
    }

    fun close() {
        synchronized(this) {
            latestRequest = null
            pendingHashes.clear()
        }
        executor.shutdownNow()
    }

    private fun runQueuedAnalysis() {
        while (true) {
            val item = synchronized(this) {
                val next = latestRequest ?: run {
                    analysisWorkerScheduled = false
                    return
                }
                latestRequest = null
                next
            }
            val contentHash = item.contentHash?.takeIf { it.isNotBlank() } ?: continue
            val source = AnalysisKey(contentHash = contentHash, uri = item.key)
            try {
                if (resultFor(contentHash) != null) continue
                val measurement = analyzer.analyze(item.uri)
                if (measurement == null) {
                    failedSources.add(source)
                    continue
                }
                val result = AudioLoudness(
                    contentHash = contentHash,
                    integratedLufs = measurement.integratedLufs,
                    peak = measurement.peak,
                    analysisVersion = LOUDNESS_ANALYSIS_VERSION,
                    analyzedAtMs = System.currentTimeMillis(),
                )
                store.upsert(result)
                synchronized(this) {
                    resultsByHash[contentHash] = result
                    _state.value = resultsByHash.values.sortedByDescending { it.analyzedAtMs }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                synchronized(this) {
                    latestRequest?.contentHash?.let(pendingHashes::remove)
                    latestRequest = null
                    analysisWorkerScheduled = false
                }
                return
            } catch (_: RuntimeException) {
                // An unsupported decoder or revoked URI should not affect playback.
                failedSources.add(source)
            } finally {
                pendingHashes.remove(contentHash)
                _revision.value = nextRevision(_revision.value)
            }
        }
    }

    private fun nextRevision(current: Long): Long =
        if (current == Long.MAX_VALUE) 0L else current + 1L

    private data class AnalysisKey(
        val contentHash: String,
        val uri: String,
    )
}

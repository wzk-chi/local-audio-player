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
    private val _state = MutableStateFlow(store.readAll())
    private val _revision = MutableStateFlow(0L)
    private val resultsByHash = HashMap<String, AudioLoudness>().apply {
        _state.value.forEach { put(it.contentHash, it) }
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
        if (resultsByHash.containsKey(contentHash) || !pendingHashes.add(contentHash)) return
        executor.execute {
            try {
                val measurement = analyzer.analyze(item.uri) ?: return@execute
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
            } catch (_: RuntimeException) {
                // An unsupported decoder or revoked URI should not affect playback.
            } finally {
                pendingHashes.remove(contentHash)
                _revision.value = nextRevision(_revision.value)
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun nextRevision(current: Long): Long =
        if (current == Long.MAX_VALUE) 0L else current + 1L
}

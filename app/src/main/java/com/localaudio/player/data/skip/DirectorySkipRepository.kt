package com.localaudio.player.data.skip

import com.localaudio.player.data.model.DirectorySkipRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DirectorySkipRepository(
    private val store: DirectorySkipStore,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val loadedLatch = CountDownLatch(1)
    @Volatile
    private var loaded = false
    private val _state = MutableStateFlow<List<DirectorySkipRule>>(emptyList())
    @Volatile
    private var index = RuleIndex(
        byFolderAndPath = buildRuleIndex(_state.value),
        revision = 0L,
    )

    val state: StateFlow<List<DirectorySkipRule>> = _state.asStateFlow()

    /** Changes whenever the indexed rule set changes, so playback can invalidate its cache. */
    val revision: Long
        get() = index.revision

    init {
        executor.execute {
            val saved = runCatching { store.readRules() }.getOrDefault(emptyList())
            index = RuleIndex(
                byFolderAndPath = buildRuleIndex(saved),
                revision = nextRevision(index.revision),
            )
            _state.value = saved
            loaded = true
            loadedLatch.countDown()
        }
    }

    fun ruleFor(folderUri: String, relativePath: String): DirectorySkipRule? {
        val currentIndex = index
        return currentIndex.byFolderAndPath[folderUri]?.get(relativePath)
    }

    fun save(folderUri: String, relativePath: String, startSeconds: Long, endSeconds: Long) {
        if (!awaitLoaded()) return
        val start = startSeconds.coerceAtLeast(0L)
        val end = endSeconds.coerceAtLeast(0L)
        updateRules { rules ->
            val remaining = rules.filterNot {
                it.folderUri == folderUri && it.relativePath == relativePath
            }
            if (start == 0L && end == 0L) {
                remaining
            } else {
                remaining + DirectorySkipRule(
                    folderUri = folderUri,
                    relativePath = relativePath,
                    startSeconds = start,
                    endSeconds = end,
                    modifiedAtMs = System.currentTimeMillis(),
                )
            }
        }
    }

    fun removeForFolder(folderUri: String) {
        if (!awaitLoaded()) return
        updateRules { rules -> rules.filterNot { it.folderUri == folderUri } }
    }

    fun updatePath(folderUri: String, oldPath: String, newPath: String) {
        if (!awaitLoaded()) return
        updateRules { rules ->
            rules.map { rule ->
                if (rule.folderUri == folderUri && isInPath(rule.relativePath, oldPath)) {
                    rule.copy(relativePath = replacePath(rule.relativePath, oldPath, newPath))
                } else rule
            }
        }
    }

    fun removePath(folderUri: String, path: String) {
        if (!awaitLoaded()) return
        updateRules { rules ->
            rules.filterNot { it.folderUri == folderUri && isInPath(it.relativePath, path) }
        }
    }

    @Synchronized
    private fun updateRules(transform: (List<DirectorySkipRule>) -> List<DirectorySkipRule>) {
        val current = _state.value
        val next = transform(current)
        if (next == current) return
        val currentIndex = index
        index = RuleIndex(
            byFolderAndPath = buildRuleIndex(next),
            revision = nextRevision(currentIndex.revision),
        )
        _state.value = next
        persist(next)
    }

    private fun persist(rules: List<DirectorySkipRule>) {
        synchronized(this) {
            pendingPersistence = rules
            if (persistenceScheduled) return
            persistenceScheduled = true
        }
        executor.execute(::drainPersistence)
    }

    private fun drainPersistence() {
        while (true) {
            val rules = synchronized(this) {
                val next = pendingPersistence ?: run {
                    persistenceScheduled = false
                    return
                }
                pendingPersistence = null
                next
            }
            runCatching { store.writeRules(rules) }
        }
    }

    private var pendingPersistence: List<DirectorySkipRule>? = null
    private var persistenceScheduled = false

    private fun awaitLoaded(): Boolean {
        if (loaded) return true
        return runCatching { loadedLatch.await() }
            .onFailure { Thread.currentThread().interrupt() }
            .isSuccess && loaded
    }

    private data class RuleIndex(
        val byFolderAndPath: Map<String, Map<String, DirectorySkipRule>>,
        val revision: Long,
    )

    private companion object {
        fun buildRuleIndex(
            rules: List<DirectorySkipRule>,
        ): Map<String, Map<String, DirectorySkipRule>> {
            val byFolder = HashMap<String, MutableMap<String, DirectorySkipRule>>()
            rules.forEach { rule ->
                val byPath = byFolder.getOrPut(rule.folderUri) { HashMap() }
                if (rule.relativePath !in byPath) {
                    byPath[rule.relativePath] = rule
                }
            }
            return byFolder.mapValues { (_, rulesByPath) -> rulesByPath.toMap() }
        }

        fun nextRevision(current: Long): Long =
            if (current == Long.MAX_VALUE) 0L else current + 1L

        fun isInPath(path: String, parent: String): Boolean =
            path == parent || (parent.isNotEmpty() && path.startsWith("$parent/"))

        fun replacePath(path: String, oldPath: String, newPath: String): String =
            if (path == oldPath) newPath else newPath + path.removePrefix(oldPath)
    }
}

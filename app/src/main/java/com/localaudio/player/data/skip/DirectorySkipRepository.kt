package com.localaudio.player.data.skip

import com.localaudio.player.data.model.DirectorySkipRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DirectorySkipRepository(
    private val store: DirectorySkipStore,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(store.readRules())

    val state: StateFlow<List<DirectorySkipRule>> = _state.asStateFlow()

    fun ruleFor(folderUri: String, relativePath: String): DirectorySkipRule? = _state.value
        .firstOrNull { it.folderUri == folderUri && it.relativePath == relativePath }

    fun save(folderUri: String, relativePath: String, startSeconds: Long, endSeconds: Long) {
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
        updateRules { rules -> rules.filterNot { it.folderUri == folderUri } }
    }

    fun updatePath(folderUri: String, oldPath: String, newPath: String) {
        updateRules { rules ->
            rules.map { rule ->
                if (rule.folderUri == folderUri && isInPath(rule.relativePath, oldPath)) {
                    rule.copy(relativePath = replacePath(rule.relativePath, oldPath, newPath))
                } else rule
            }
        }
    }

    fun removePath(folderUri: String, path: String) {
        updateRules { rules ->
            rules.filterNot { it.folderUri == folderUri && isInPath(it.relativePath, path) }
        }
    }

    private fun updateRules(transform: (List<DirectorySkipRule>) -> List<DirectorySkipRule>) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        executor.execute { runCatching { store.writeRules(next) } }
    }

    private companion object {
        fun isInPath(path: String, parent: String): Boolean =
            path == parent || (parent.isNotEmpty() && path.startsWith("$parent/"))

        fun replacePath(path: String, oldPath: String, newPath: String): String =
            if (path == oldPath) newPath else newPath + path.removePrefix(oldPath)
    }
}

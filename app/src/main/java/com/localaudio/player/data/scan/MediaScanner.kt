package com.localaudio.player.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import com.localaudio.player.data.hash.AudioHashCalculator
import com.localaudio.player.data.model.AudioItem
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ScannedDirectory(
    val uri: Uri,
    val rootFolderUri: String,
    val relativePath: String,
    val title: String,
)

/** SAF scanner using one query per directory and a small bounded detail pool. */
class MediaScanner(private val context: Context) {
    private val hashCalculator = AudioHashCalculator(context)
    private val detailExecutor: ExecutorService = Executors.newFixedThreadPool(MAX_DETAIL_THREADS)

    private companion object {
        const val MAX_DEPTH = 20
        const val MAX_DETAIL_THREADS = 2
        const val DETAIL_BATCH_SIZE = 16
        const val PROGRESS_ITEM_STEP = 100
        const val PROGRESS_INTERVAL_MS = 250L
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "mka", "amr", "mp4",
        )
    }

    fun scanFolder(
        folderUri: Uri,
        folderName: String,
        onProgress: (scanned: Int, found: Int) -> Unit,
        onItems: (List<AudioItem>) -> Unit,
        knownItems: Map<String, AudioItem> = emptyMap(),
        onDirectories: (List<ScannedDirectory>) -> Unit = {},
        isDirectoryBlocked: (rootFolderUri: String, documentId: String) -> Boolean = { _, _ -> false },
    ): List<AudioItem> {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(folderUri)
        val visited = HashSet<String>()
        val found = ArrayList<AudioItem>()
        val directories = ArrayList<ScannedDirectory>()
        var scanned = 0
        var lastProgressAtMs = 0L

        fun walk(documentId: String, depth: Int, relativePath: String) {
            checkInterrupted()
            if (depth > MAX_DEPTH) return
            if (isDirectoryBlocked(folderUri.toString(), documentId)) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
            val cursor = resolver.query(children, PROJECTION, null, null, null)
                ?: error("无法读取目录：$relativePath")
            cursor.use { c ->
                val idColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    checkInterrupted()
                    val id = c.getString(idColumn) ?: continue
                    if (!visited.add(id)) continue
                    val name = c.getString(nameColumn) ?: continue
                    val mime = c.getString(mimeColumn) ?: ""
                    val sizeBytes = if (sizeColumn >= 0 && !c.isNull(sizeColumn)) {
                        c.getLong(sizeColumn)
                    } else {
                        -1L
                    }
                    val lastModifiedMs = if (modifiedColumn >= 0 && !c.isNull(modifiedColumn)) {
                        c.getLong(modifiedColumn)
                    } else {
                        -1L
                    }
                    scanned++
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                        directories += ScannedDirectory(
                            uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id),
                            rootFolderUri = folderUri.toString(),
                            relativePath = childPath,
                            title = name,
                        )
                        walk(id, depth + 1, childPath)
                    } else if (isAudio(mime, name)) {
                        val item = AudioItem(
                            uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id),
                            title = name.substringBeforeLast('.'),
                            artist = folderName,
                            folderUri = folderUri.toString(),
                            folderName = folderName,
                            relativePath = relativePath,
                            sizeBytes = sizeBytes,
                            lastModifiedMs = lastModifiedMs,
                        )
                        found += item
                    }
                    val now = SystemClock.uptimeMillis()
                    if (scanned % PROGRESS_ITEM_STEP == 0 &&
                        (lastProgressAtMs == 0L || now - lastProgressAtMs >= PROGRESS_INTERVAL_MS)
                    ) {
                        onProgress(scanned, found.size)
                        lastProgressAtMs = now
                    }
                }
            }
        }

        walk(treeId, 0, "")
        onDirectories(directories.distinctBy { it.uri.normalizeScheme().toString() })
        val discovered = found.distinctBy { it.key }
        if (discovered.isNotEmpty()) onItems(discovered)
        onProgress(scanned, discovered.size)
        return enrich(discovered, knownItems)
    }

    private fun enrich(
        items: List<AudioItem>,
        knownItems: Map<String, AudioItem>,
    ): List<AudioItem> {
        if (items.isEmpty()) return emptyList()
        val result = ArrayList<AudioItem>(items.size)
        items.chunked(DETAIL_BATCH_SIZE).forEach { batch ->
            checkInterrupted()
            val tasks = batch.map { item ->
                Callable { enrichItem(item, knownItems[item.key]) }
            }
            val futures = detailExecutor.invokeAll(tasks)
            val enrichedBatch = futures.mapIndexed { index, future ->
                try {
                    future.get()
                } catch (_: CancellationException) {
                    throw InterruptedException()
                } catch (error: ExecutionException) {
                    when (val cause = error.cause) {
                        is InterruptedException -> throw cause
                        is Error -> throw cause
                        else -> batch[index]
                    }
                }
            }
            result += enrichedBatch
        }
        return result
    }

    private fun enrichItem(item: AudioItem, cached: AudioItem?): AudioItem {
        if (cached != null && hasSameFileMetadata(item, cached) && !cached.contentHash.isNullOrBlank()) {
            return item.copy(
                durationMs = cached.durationMs,
                contentHash = cached.contentHash,
            )
        }
        checkInterrupted()
        val durationMs = if (cached != null && hasSameFileMetadata(item, cached)) {
            cached.durationMs
        } else {
            readDuration(item.uri)
        }
        checkInterrupted()
        return item.copy(
            durationMs = durationMs,
            contentHash = hashCalculator.calculate(item.uri),
        )
    }

    private fun hasSameFileMetadata(current: AudioItem, cached: AudioItem): Boolean =
        current.sizeBytes >= 0L &&
            current.lastModifiedMs >= 0L &&
            current.sizeBytes == cached.sizeBytes &&
            current.lastModifiedMs == cached.lastModifiedMs

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/", ignoreCase = true)) return true
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
    }

    private fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}

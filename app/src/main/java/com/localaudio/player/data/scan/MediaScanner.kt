package com.localaudio.player.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.localaudio.player.data.model.AudioItem
import java.util.Locale

/** SAF scanner using one query per directory and one reusable metadata retriever. */
class MediaScanner(private val context: Context) {
    private companion object {
        const val MAX_DEPTH = 20
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
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
    ): List<AudioItem> {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(folderUri)
        val visited = HashSet<String>()
        val found = ArrayList<AudioItem>()
        val pending = ArrayList<AudioItem>(25)
        val retriever = MediaMetadataRetriever()
        var scanned = 0

        fun walk(documentId: String, depth: Int, relativePath: String) {
            if (depth > MAX_DEPTH) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
            val cursor = resolver.query(children, PROJECTION, null, null, null)
                ?: error("无法读取目录：$relativePath")
            cursor.use { c ->
                val idColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getString(idColumn) ?: continue
                    if (!visited.add(id)) continue
                    val name = c.getString(nameColumn) ?: continue
                    val mime = c.getString(mimeColumn) ?: ""
                    scanned++
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                        walk(id, depth + 1, childPath)
                    } else if (isAudio(mime, name)) {
                        val item = AudioItem(
                            uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id),
                            title = name.substringBeforeLast('.'),
                            artist = folderName,
                            folderUri = folderUri.toString(),
                            folderName = folderName,
                            relativePath = relativePath,
                        )
                        found += item
                        pending += item
                        if (pending.size >= 25) {
                            onItems(pending.toList())
                            pending.clear()
                        }
                    }
                    if (scanned % 25 == 0) onProgress(scanned, found.size)
                }
            }
        }

        try {
            walk(treeId, 0, "")
            if (pending.isNotEmpty()) onItems(pending.toList())
            val result = found.distinctBy { it.key }.map { item ->
                item.copy(durationMs = readDuration(retriever, item.uri))
            }
            onProgress(scanned, result.size)
            return result
        } finally {
            retriever.release()
        }
    }

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/", ignoreCase = true)) return true
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
    }

    private fun readDuration(retriever: MediaMetadataRetriever, uri: Uri): Long = try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: RuntimeException) {
        0L
    }
}

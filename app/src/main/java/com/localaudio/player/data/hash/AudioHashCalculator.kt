package com.localaudio.player.data.hash

import android.content.Context
import android.net.Uri
import net.jpountz.xxhash.StreamingXXHash64
import net.jpountz.xxhash.XXHashFactory
import java.io.IOException

internal const val CONTENT_HASH_ALGORITHM = "xxHash64"

/** Calculates a content hash without loading the whole audio file into memory. */
internal class AudioHashCalculator(context: Context) {
    private val contentResolver = context.contentResolver

    fun calculate(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            val hash: StreamingXXHash64 = XXHashFactory.fastestJavaInstance().newStreamingHash64(0)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) hash.update(buffer, 0, count)
            }
            java.lang.Long.toUnsignedString(hash.value, 16).padStart(HASH_HEX_LENGTH, '0')
        }
    } catch (error: InterruptedException) {
        throw error
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val HASH_HEX_LENGTH = 16
    }
}

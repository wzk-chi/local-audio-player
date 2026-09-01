package com.localaudio.player.data.loudness

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max

/**
 * Decodes one audio source to PCM and computes a lightweight integrated loudness estimate.
 * The work is deliberately performed by LoudnessRepository's low-priority serial worker.
 */
class LoudnessAnalyzer(private val context: Context) {
    fun analyze(uri: Uri): Measurement? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            checkInterrupted()
            extractor.setDataSource(context, uri, emptyMap())
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/", ignoreCase = true) == true
            } ?: return null
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decode(extractor, decoder, inputFormat)
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            null
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                codec.release()
            }
            extractor.release()
        }
    }

    private fun decode(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
    ): Measurement? {
        val stats = PcmStats()
        stats.updateFormat(inputFormat)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var outputFormat = inputFormat

        while (!outputEnded) {
            checkInterrupted()
            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(BUFFER_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val input = decoder.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("解码器输入缓冲区不可用")
                    input.clear()
                    val sampleSize = extractor.readSampleData(input, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            presentationTimeUs,
                            0,
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, BUFFER_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = decoder.outputFormat
                    stats.updateFormat(outputFormat)
                }
                else -> if (outputIndex >= 0) {
                    if (bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        decoder.getOutputBuffer(outputIndex)?.let { output ->
                            consumeOutput(output, bufferInfo, outputFormat, stats)
                        }
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                }
            }
        }
        return stats.finish()
    }

    private fun consumeOutput(
        output: ByteBuffer,
        info: MediaCodec.BufferInfo,
        format: MediaFormat,
        stats: PcmStats,
    ) {
        val start = info.offset.coerceIn(0, output.capacity())
        val end = (info.offset + info.size).coerceIn(start, output.capacity())
        if (end <= start) return
        val pcm = output.duplicate()
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                position(start)
                limit(end)
            }
        stats.consume(pcm, format)
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
    }

    data class Measurement(
        val integratedLufs: Float,
        val peak: Float,
    )

    private class PcmStats {
        private var sampleRate = DEFAULT_SAMPLE_RATE
        private var channelCount = DEFAULT_CHANNEL_COUNT
        private var encoding = AudioFormat.ENCODING_PCM_16BIT
        private var previousInput = FloatArray(channelCount)
        private var previousOutput = FloatArray(channelCount)
        private var blockEnergy = 0.0
        private var blockFrames = 0L
        private var totalEnergy = 0.0
        private var totalFrames = 0L
        private var observedFrames = 0L
        private var peak = 0f

        fun updateFormat(format: MediaFormat) {
            val newSampleRate = format.integerOrDefault(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            val newChannelCount = format.integerOrDefault(
                MediaFormat.KEY_CHANNEL_COUNT,
                channelCount,
            ).coerceIn(1, MAX_CHANNELS)
            val newEncoding = format.integerOrDefault(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (newSampleRate == sampleRate &&
                newChannelCount == channelCount &&
                newEncoding == encoding
            ) return
            flushBlock()
            sampleRate = newSampleRate.coerceAtLeast(1)
            channelCount = newChannelCount
            encoding = newEncoding
            previousInput = FloatArray(channelCount)
            previousOutput = FloatArray(channelCount)
        }

        fun consume(buffer: ByteBuffer, format: MediaFormat) {
            updateFormat(format)
            val bytesPerSample = bytesPerSample(encoding)
            val frameBytes = bytesPerSample * channelCount
            if (frameBytes <= 0) return
            while (buffer.remaining() >= frameBytes) {
                var frameEnergy = 0.0
                repeat(channelCount) { channel ->
                    val sample = readSample(buffer, encoding)
                    peak = max(peak, kotlin.math.abs(sample))
                    val weighted = highPass(sample, channel)
                    frameEnergy += weighted.toDouble() * weighted.toDouble()
                }
                blockEnergy += frameEnergy / channelCount
                blockFrames++
                observedFrames++
                if (blockFrames >= blockSizeFrames()) flushBlock()
            }
        }

        fun finish(): Measurement? {
            flushBlock()
            if (observedFrames == 0L) return null
            val meanSquare = if (totalFrames == 0L) 0.0 else totalEnergy / totalFrames
            val lufs = if (meanSquare <= 0.0) {
                SILENCE_LUFS
            } else {
                (-0.691 + 10.0 * log10(meanSquare)).toFloat().coerceAtLeast(SILENCE_LUFS)
            }
            return Measurement(integratedLufs = lufs, peak = peak.coerceIn(0f, 1f))
        }

        private fun highPass(sample: Float, channel: Int): Float {
            val filtered = sample - previousInput[channel] + HIGH_PASS_COEFFICIENT * previousOutput[channel]
            previousInput[channel] = sample
            previousOutput[channel] = filtered
            return filtered
        }

        private fun flushBlock() {
            if (blockFrames == 0L) return
            val meanSquare = blockEnergy / blockFrames
            if (meanSquare > ABSOLUTE_GATE_POWER) {
                totalEnergy += blockEnergy
                totalFrames += blockFrames
            }
            blockEnergy = 0.0
            blockFrames = 0L
        }

        private fun blockSizeFrames(): Long = max(1L, sampleRate * BLOCK_DURATION_MS / 1_000L)

        private fun bytesPerSample(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            ENCODING_PCM_24BIT_PACKED -> 3
            ENCODING_PCM_32BIT -> 4
            else -> 2
        }

        private fun readSample(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT ->
                ((buffer.get().toInt() and 0xff) - 128) / 128f
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.coerceIn(-1f, 1f)
            ENCODING_PCM_24BIT_PACKED -> {
                val value = (buffer.get().toInt() and 0xff) or
                    ((buffer.get().toInt() and 0xff) shl 8) or
                    (buffer.get().toInt() shl 16)
                value / 8_388_608f
            }
            ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648f
            else -> buffer.short / 32_768f
        }

        private fun MediaFormat.integerOrDefault(key: String, default: Int): Int =
            if (containsKey(key)) getInteger(key) else default

        private companion object {
            const val DEFAULT_SAMPLE_RATE = 44_100
            const val DEFAULT_CHANNEL_COUNT = 2
            const val MAX_CHANNELS = 8
            const val BLOCK_DURATION_MS = 400L
            const val HIGH_PASS_COEFFICIENT = 0.995f
            const val ABSOLUTE_GATE_POWER = 8.5e-8
            const val SILENCE_LUFS = -70f
            const val ENCODING_PCM_24BIT_PACKED = 5
            const val ENCODING_PCM_32BIT = 22
        }
    }

    private companion object {
        const val BUFFER_TIMEOUT_US = 10_000L
    }
}

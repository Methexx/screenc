package com.example.screeenc

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * H.264 Video Decoder using Android MediaCodec API
 * Provides hardware-accelerated decoding of H.264/AVC video streams
 */
class H264Decoder(private val surface: Surface) {
    companion object {
        private const val TAG = "H264Decoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // "video/avc"
        private const val TIMEOUT_US = 10000L // 10ms timeout
    }

    private var mediaCodec: MediaCodec? = null
    private var isConfigured = false
    private var width = 1920
    private var height = 1080

    /**
     * Initialize and configure the MediaCodec decoder
     */
    fun configure(width: Int = 1920, height: Int = 1080) {
        try {
            this.width = width
            this.height = height

            // Create MediaCodec decoder for H.264
            mediaCodec = MediaCodec.createDecoderByType(MIME_TYPE)

            // Configure video format
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1) // Request low latency mode
            }

            // Configure codec with surface for rendering
            mediaCodec?.configure(format, surface, null, 0)
            mediaCodec?.start()

            isConfigured = true
            Log.i(TAG, "MediaCodec configured: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure decoder", e)
            throw e
        }
    }

    /**
     * Decode a single H.264 frame (NAL unit)
     * @param frameData The raw H.264 encoded frame data
     * @param presentationTimeUs Presentation timestamp in microseconds
     */
    fun decodeFrame(frameData: ByteArray, presentationTimeUs: Long = System.nanoTime() / 1000) {
        if (!isConfigured || mediaCodec == null) {
            Log.w(TAG, "Decoder not configured")
            return
        }

        try {
            val codec = mediaCodec ?: return

            // Get input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(frameData)

                // Queue the input buffer with the encoded data
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    frameData.size,
                    presentationTimeUs,
                    0
                )
            }

            // Dequeue output buffer and render to surface
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            while (outputBufferIndex >= 0) {
                // Render decoded frame to surface
                codec.releaseOutputBuffer(outputBufferIndex, true)

                // Check for more output buffers
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                Log.d(TAG, "Output format changed: $newFormat")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding frame", e)
        }
    }

    /**
     * Release all decoder resources
     */
    fun release() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            isConfigured = false
            Log.i(TAG, "Decoder released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing decoder", e)
        }
    }

    /**
     * Check if decoder is ready
     */
    fun isReady(): Boolean = isConfigured && mediaCodec != null
}

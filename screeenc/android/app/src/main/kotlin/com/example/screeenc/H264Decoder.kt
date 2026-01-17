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
     * NAL unit MUST include the 0x00 0x00 0x00 0x01 start code prefix
     * @param frameData The raw H.264 encoded frame data WITH start code
     * @param presentationTimeUs Presentation timestamp in microseconds
     */
    fun decodeFrame(frameData: ByteArray, presentationTimeUs: Long = System.nanoTime() / 1000) {
        if (!isConfigured || mediaCodec == null) {
            Log.w(TAG, "Decoder not configured")
            return
        }

        // Validate NAL unit has start code
        if (frameData.size < 5) {
            Log.w(TAG, "Frame too small: ${frameData.size} bytes")
            return
        }
        
        // Log NAL type for debugging
        val nalType = if (frameData.size > 4) frameData[4].toInt() and 0x1F else -1
        val nalTypeName = when (nalType) {
            1 -> "P-slice"
            5 -> "IDR (I-frame)"
            6 -> "SEI"
            7 -> "SPS"
            8 -> "PPS"
            else -> "type=$nalType"
        }
        Log.d(TAG, "Decoding NAL: ${frameData.size} bytes, $nalTypeName")

        try {
            val codec = mediaCodec ?: return

            // Get input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(frameData)

                // Set CODEC_CONFIG flag for SPS/PPS
                val flags = when (nalType) {
                    7, 8 -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    else -> 0
                }

                // Queue the input buffer with the encoded data
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    frameData.size,
                    presentationTimeUs,
                    flags
                )
                Log.v(TAG, "Queued: ${frameData.size} bytes, $nalTypeName, flags=$flags")
            } else {
                Log.w(TAG, "No input buffer available (index=$inputBufferIndex)")
            }

            // Dequeue output buffer and render to surface
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            while (outputBufferIndex >= 0) {
                // Render decoded frame to surface (true = render)
                codec.releaseOutputBuffer(outputBufferIndex, true)
                Log.i(TAG, "*** RENDERED frame to surface, size=${bufferInfo.size}, flags=${bufferInfo.flags}")

                // Check for more output buffers
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

            when (outputBufferIndex) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    Log.i(TAG, "Output format changed: $newFormat")
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Normal - no output available yet
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding frame: ${e.message}", e)
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

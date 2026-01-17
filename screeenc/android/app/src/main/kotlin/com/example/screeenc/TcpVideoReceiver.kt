package com.example.screeenc

import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP Video Receiver
 * Connects to localhost TCP socket and receives H.264 video stream
 */
class TcpVideoReceiver(
    private val host: String = "127.0.0.1",
    private val port: Int = 27183
) {
    companion object {
        private const val TAG = "TcpVideoReceiver"
        private const val BUFFER_SIZE = 65536 // 64KB buffer
        private const val CONNECTION_TIMEOUT = 5000 // 5 seconds
        private const val NAL_START_CODE_SIZE = 4
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var isRunning = false
    private var receiveJob: Job? = null

    // Callback for received frames
    var onFrameReceived: ((ByteArray) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean, String?) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Connect to the TCP server
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to $host:$port...")
            Log.i(TAG, "Port value before socket: $port (type: ${port::class.simpleName})")
            
            val address = InetSocketAddress(host, port)
            Log.i(TAG, "InetSocketAddress created: ${address.hostString}:${address.port}")

            socket = Socket().apply {
                soTimeout = CONNECTION_TIMEOUT
                keepAlive = true
                tcpNoDelay = true // Disable Nagle's algorithm for lower latency
                Log.i(TAG, "About to connect to: ${address.hostString}:${address.port}")
                connect(address, CONNECTION_TIMEOUT)
            }

            inputStream = socket?.getInputStream()
            isRunning = true

            Log.i(TAG, "Connected successfully")
            onConnectionStateChanged?.invoke(true, null)
            true
        } catch (e: IOException) {
            val errorMsg = "Connection failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onConnectionStateChanged?.invoke(false, errorMsg)
            onError?.invoke(errorMsg)
            false
        }
    }

    /**
     * Start receiving video stream
     */
    fun startReceiving(coroutineScope: CoroutineScope) {
        if (!isRunning) {
            Log.w(TAG, "Not connected, cannot start receiving")
            return
        }

        receiveJob = coroutineScope.launch(Dispatchers.IO) {
            receiveVideoStream()
        }
    }

    /**
     * Main receive loop - reads H.264 NAL units from stream
     */
    private suspend fun receiveVideoStream() {
        val buffer = ByteArray(BUFFER_SIZE)
        val frameBuffer = mutableListOf<Byte>()
        var nalStartFound = false

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val stream = inputStream ?: break
                val bytesRead = stream.read(buffer)

                if (bytesRead <= 0) {
                    Log.w(TAG, "Stream ended or connection closed")
                    break
                }

                // Process received bytes and extract H.264 NAL units
                for (i in 0 until bytesRead) {
                    frameBuffer.add(buffer[i])

                    // Look for NAL start codes (0x00 0x00 0x00 0x01 or 0x00 0x00 0x01)
                    if (isNalStartCode(frameBuffer)) {
                        if (nalStartFound && frameBuffer.size > NAL_START_CODE_SIZE) {
                            // Extract the previous NAL unit (excluding current start code)
                            val nalUnit = frameBuffer.dropLast(NAL_START_CODE_SIZE).toByteArray()
                            if (nalUnit.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    onFrameReceived?.invoke(nalUnit)
                                }
                            }
                            frameBuffer.clear()
                            // Keep the current start code
                            for (j in 0 until NAL_START_CODE_SIZE) {
                                frameBuffer.add(buffer[i - NAL_START_CODE_SIZE + 1 + j])
                            }
                        }
                        nalStartFound = true
                    }
                }
            }

            // Process any remaining data
            if (frameBuffer.isNotEmpty()) {
                val nalUnit = frameBuffer.toByteArray()
                withContext(Dispatchers.Main) {
                    onFrameReceived?.invoke(nalUnit)
                }
            }
        } catch (e: IOException) {
            if (isRunning) {
                Log.e(TAG, "Error receiving stream", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Stream error: ${e.message}")
                }
            }
        } finally {
            Log.i(TAG, "Receive loop ended")
        }
    }

    /**
     * Check if current buffer ends with NAL start code
     */
    private fun isNalStartCode(buffer: List<Byte>): Boolean {
        if (buffer.size < 4) return false

        val size = buffer.size
        // Check for 0x00 0x00 0x00 0x01
        return (buffer[size - 4] == 0x00.toByte() &&
                buffer[size - 3] == 0x00.toByte() &&
                buffer[size - 2] == 0x00.toByte() &&
                buffer[size - 1] == 0x01.toByte()) ||
                // Check for 0x00 0x00 0x01
                (buffer[size - 3] == 0x00.toByte() &&
                        buffer[size - 2] == 0x00.toByte() &&
                        buffer[size - 1] == 0x01.toByte())
    }

    /**
     * Disconnect and cleanup
     */
    fun disconnect() {
        isRunning = false
        receiveJob?.cancel()

        try {
            inputStream?.close()
            socket?.close()
            Log.i(TAG, "Disconnected")
            onConnectionStateChanged?.invoke(false, "Disconnected")
        } catch (e: IOException) {
            Log.e(TAG, "Error during disconnect", e)
        } finally {
            inputStream = null
            socket = null
        }
    }

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = socket?.isConnected == true && isRunning
}

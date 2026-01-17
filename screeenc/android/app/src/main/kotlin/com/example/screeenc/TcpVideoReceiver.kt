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
                // IMPORTANT: Only set timeout during connection, NOT for reading
                // Keep reading indefinitely without timeout
                keepAlive = true
                tcpNoDelay = true // Disable Nagle's algorithm for lower latency
                Log.i(TAG, "About to connect to: ${address.hostString}:${address.port}")
                connect(address, CONNECTION_TIMEOUT)
                // After connection, disable read timeout
                soTimeout = 0 // 0 = infinite timeout for reading
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
     * Main receive loop - reads H.264 stream and extracts NAL units
     */
    private suspend fun receiveVideoStream() {
        val buffer = ByteArray(BUFFER_SIZE)
        val accumulator = mutableListOf<Byte>()
        
        try {
            var totalBytesReceived = 0L
            var nalCount = 0
            
            Log.i(TAG, "=== Starting receive loop ===")
            
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val stream = inputStream ?: break
                val bytesRead = stream.read(buffer)

                if (bytesRead <= 0) {
                    Log.w(TAG, "Stream ended or connection closed after $totalBytesReceived bytes")
                    break
                }

                totalBytesReceived += bytesRead
                Log.v(TAG, "Received $bytesRead bytes (total: $totalBytesReceived)")

                // Add all bytes to accumulator
                for (i in 0 until bytesRead) {
                    accumulator.add(buffer[i])
                }

                // Extract complete NAL units (INCLUDING start codes) from accumulator
                // MediaCodec expects NAL units WITH the 0x00 0x00 0x00 0x01 prefix!
                val nalPositions = mutableListOf<Int>()
                
                // Find all start code positions
                var pos = 0
                while (pos < accumulator.size - 3) {
                    if (accumulator[pos] == 0x00.toByte() && 
                        accumulator[pos + 1] == 0x00.toByte() &&
                        accumulator[pos + 2] == 0x00.toByte() &&
                        accumulator[pos + 3] == 0x01.toByte()) {
                        nalPositions.add(pos)
                        pos += 4
                    } else {
                        pos++
                    }
                }
                
                // Extract complete NALs (from one start code to the next)
                if (nalPositions.size >= 2) {
                    // We have at least 2 start codes, so we can extract complete NALs
                    for (i in 0 until nalPositions.size - 1) {
                        val startPos = nalPositions[i]
                        val endPos = nalPositions[i + 1]
                        
                        // Extract NAL INCLUDING start code
                        val nalBytes = ByteArray(endPos - startPos)
                        for (j in startPos until endPos) {
                            nalBytes[j - startPos] = accumulator[j]
                        }
                        
                        if (nalBytes.size > 4) {
                            val nalType = nalBytes[4].toInt() and 0x1F
                            Log.i(TAG, ">>> Extracted NAL #${nalCount}: ${nalBytes.size} bytes, type=$nalType")
                            try {
                                withContext(Dispatchers.Main) {
                                    onFrameReceived?.invoke(nalBytes)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error invoking callback: ${e.message}", e)
                            }
                            nalCount++
                        }
                    }
                    
                    // Keep only the last incomplete NAL in accumulator
                    val lastStart = nalPositions.last()
                    val remaining = accumulator.subList(lastStart, accumulator.size).toMutableList()
                    accumulator.clear()
                    accumulator.addAll(remaining)
                }
            }

            // Send any remaining NAL data
            if (accumulator.isNotEmpty()) {
                val finalNal = accumulator.toByteArray()
                Log.i(TAG, "Final NAL #${nalCount}: ${finalNal.size} bytes")
                withContext(Dispatchers.Main) {
                    onFrameReceived?.invoke(finalNal)
                }
            }
            
            Log.i(TAG, "=== Receive loop ended: received $totalBytesReceived bytes, processed $nalCount NALs ===")
        } catch (e: IOException) {
            if (isRunning) {
                Log.e(TAG, "Error receiving stream: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Stream error: ${e.message}")
                }
            }
        } finally {
            Log.i(TAG, "Receive loop finally block")
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

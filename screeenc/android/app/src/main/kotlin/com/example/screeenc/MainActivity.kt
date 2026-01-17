package com.example.screeenc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val METHOD_CHANNEL = "com.example.screeenc/video_receiver"
        private const val EVENT_CHANNEL = "com.example.screeenc/video_status"
    }

    private var statusEventSink: EventChannel.EventSink? = null
    private var statusReceiver: BroadcastReceiver? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Method Channel - for commands from Flutter to Android
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startReceiver" -> {
                        startReceiverService()
                        result.success(true)
                    }
                    "stopReceiver" -> {
                        stopReceiverService()
                        result.success(true)
                    }
                    "getStatus" -> {
                        // Return current service status
                        result.success(mapOf(
                            "isRunning" to isServiceRunning(),
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            }

        // Event Channel - for status updates from Android to Flutter
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    statusEventSink = events
                    registerStatusReceiver()
                    Log.d(TAG, "Event channel listener registered")
                }

                override fun onCancel(arguments: Any?) {
                    statusEventSink = null
                    unregisterStatusReceiver()
                    Log.d(TAG, "Event channel listener cancelled")
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")
    }

    /**
     * Register broadcast receiver for service status updates
     */
    private fun registerStatusReceiver() {
        if (statusReceiver != null) return

        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getStringExtra(VideoReceiverService.EXTRA_STATUS)
                val message = intent.getStringExtra(VideoReceiverService.EXTRA_MESSAGE)

                val eventData = mapOf(
                    "status" to status,
                    "message" to message,
                    "timestamp" to System.currentTimeMillis()
                )

                statusEventSink?.success(eventData)
                Log.d(TAG, "Status update: $status - $message")
            }
        }

        val filter = IntentFilter(VideoReceiverService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    /**
     * Unregister broadcast receiver
     */
    private fun unregisterStatusReceiver() {
        statusReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        statusReceiver = null
    }

    /**
     * Start the video receiver service
     */
    private fun startReceiverService() {
        try {
            val intent = Intent(this, VideoReceiverService::class.java).apply {
                action = VideoReceiverService.ACTION_START
            }
            startForegroundService(intent)
            Log.i(TAG, "Starting receiver service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            statusEventSink?.error("START_ERROR", e.message, null)
        }
    }

    /**
     * Stop the video receiver service
     */
    private fun stopReceiverService() {
        try {
            val intent = Intent(this, VideoReceiverService::class.java).apply {
                action = VideoReceiverService.ACTION_STOP
            }
            startService(intent)
            Log.i(TAG, "Stopping receiver service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop service", e)
            statusEventSink?.error("STOP_ERROR", e.message, null)
        }
    }

    /**
     * Check if service is running (simplified check)
     */
    private fun isServiceRunning(): Boolean {
        // This is a simplified check - in production, you might want to track this more accurately
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterStatusReceiver()
        Log.i(TAG, "MainActivity destroyed")
    }
}


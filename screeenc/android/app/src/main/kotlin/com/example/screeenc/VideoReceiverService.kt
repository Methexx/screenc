package com.example.screeenc

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground Service for USB Video Streaming
 * Handles TCP connection, H.264 decoding, and rendering
 */
class VideoReceiverService : Service() {
    companion object {
        private const val TAG = "VideoReceiverService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "video_receiver_channel"
        private const val CHANNEL_NAME = "Screen Receiver"

        const val ACTION_START = "com.example.screeenc.START"
        const val ACTION_STOP = "com.example.screeenc.STOP"
        const val ACTION_STATUS = "com.example.screeenc.STATUS"

        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var tcpReceiver: TcpVideoReceiver? = null
    private var h264Decoder: H264Decoder? = null
    private var surfaceView: SurfaceView? = null
    private var windowManager: WindowManager? = null
    
    private var isStreaming = false
    private var frameCount = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Start video streaming from TCP connection
     */
    private fun startStreaming() {
        if (isStreaming) {
            Log.w(TAG, "Already streaming")
            return
        }

        Log.i(TAG, "Starting video receiver service...")

        // Start as foreground service
        val notification = createNotification("Connecting to Windows host...")
        startForeground(NOTIFICATION_ID, notification)

        // Create overlay surface for video rendering
        createSurfaceView()

        isStreaming = true
        frameCount = 0

        // Initialize TCP receiver
        tcpReceiver = TcpVideoReceiver().apply {
            onFrameReceived = { frameData ->
                handleReceivedFrame(frameData)
            }

            onConnectionStateChanged = { connected, message ->
                if (connected) {
                    updateNotification("Streaming from Windows")
                    broadcastStatus("connected", "Receiving video stream")
                } else {
                    updateNotification("Disconnected")
                    broadcastStatus("disconnected", message ?: "Connection lost")
                }
            }

            onError = { error ->
                Log.e(TAG, "TCP Error: $error")
                broadcastStatus("error", error)
            }
        }

        // Connect and start receiving in coroutine
        serviceScope.launch {
            val connected = tcpReceiver?.connect() ?: false
            if (connected) {
                tcpReceiver?.startReceiving(serviceScope)
            } else {
                stopStreaming()
            }
        }
    }

    /**
     * Stop streaming and cleanup
     */
    private fun stopStreaming() {
        if (!isStreaming) {
            Log.w(TAG, "Not streaming")
            return
        }

        Log.i(TAG, "Stopping video receiver service...")
        isStreaming = false

        tcpReceiver?.disconnect()
        tcpReceiver = null

        h264Decoder?.release()
        h264Decoder = null

        removeSurfaceView()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        broadcastStatus("stopped", "Service stopped")
    }

    private var closeButton: android.widget.Button? = null

    /**
     * Create floating surface view for video rendering with close button
     */
    private fun createSurfaceView() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Get screen dimensions
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            Log.i(TAG, "Screen size: ${screenWidth}x${screenHeight}")

            surfaceView = SurfaceView(this).apply {
                // CRITICAL: setZOrderOnTop makes overlay appear above other windows
                setZOrderOnTop(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.i(TAG, "=== SURFACE CREATED ===")
                        Log.i(TAG, "Surface valid: ${holder.surface.isValid}")
                        
                        // Initialize decoder with surface - decoder will render directly
                        h264Decoder = H264Decoder(holder.surface).apply {
                            configure(screenWidth, screenHeight)
                        }
                        Log.i(TAG, "Decoder initialized and ready to render")
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        Log.d(TAG, "Surface changed: ${width}x${height}, format=$format")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.i(TAG, "Surface destroyed")
                        h264Decoder?.release()
                        h264Decoder = null
                    }
                })
            }

            // Create overlay window parameters - TYPE_APPLICATION_OVERLAY goes on top of everything
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }

            // Use full screen dimensions - TRANSLUCENT to work with setZOrderOnTop
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            windowManager?.addView(surfaceView, params)
            Log.i(TAG, "Surface view added to window manager at ${screenWidth}x${screenHeight}")
            
            // Create close button overlay
            createCloseButton(layoutType)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create surface view", e)
            broadcastStatus("error", "Failed to create overlay: ${e.message}")
        }
    }

    /**
     * Create a floating close button
     */
    private fun createCloseButton(layoutType: Int) {
        closeButton = android.widget.Button(this).apply {
            text = "✕ CLOSE"
            setBackgroundColor(android.graphics.Color.parseColor("#CC0000"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding(24, 12, 24, 12)
            
            setOnClickListener {
                Log.i(TAG, "Close button pressed - stopping service")
                stopStreaming()
            }
        }
        
        val buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 80  // Below status bar
        }
        
        windowManager?.addView(closeButton, buttonParams)
        Log.i(TAG, "Close button added")
    }

    /**
     * Remove surface view and close button
     */
    private fun removeSurfaceView() {
        try {
            closeButton?.let {
                windowManager?.removeView(it)
            }
            closeButton = null
            
            surfaceView?.let {
                windowManager?.removeView(it)
            }
            surfaceView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error removing surface view", e)
        }
    }

    /**
     * Handle received H.264 frame
     */
    private fun handleReceivedFrame(frameData: ByteArray) {
        Log.i(TAG, ">>> Frame received: ${frameData.size} bytes, frameCount=$frameCount")
        h264Decoder?.decodeFrame(frameData)
        frameCount++

        if (frameCount % 10 == 0L) {
            Log.i(TAG, "✓ Decoded $frameCount frames so far")
        }
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for screen receiver service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification
     */
    private fun createNotification(message: String): Notification {
        val stopIntent = Intent(this, VideoReceiverService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Receiver")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .build()
    }

    /**
     * Update notification message
     */
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Broadcast status update
     */
    private fun broadcastStatus(status: String, message: String) {
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
    }
}

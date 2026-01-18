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
    private var backgroundView: android.view.View? = null
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
                    // Stop timer on disconnection
                    stopStreamTimer()
                }
            }

            onError = { error ->
                Log.e(TAG, "TCP Error: $error")
                broadcastStatus("error", error)
                // Stop timer on error
                stopStreamTimer()
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
        
        // Stop timer immediately
        stopStreamTimer()

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
    private var portraitButton: android.widget.Button? = null
    private var landscapeButton: android.widget.Button? = null
    private var timerTextView: android.widget.TextView? = null
    private var isLandscapeMode = false
    private var videoWidth = 1920
    private var videoHeight = 1080
    private var screenWidth = 0
    private var screenHeight = 0
    
    // Timer variables
    private var streamStartTime: Long = 0
    private var timerJob: kotlinx.coroutines.Job? = null
    
    /**
     * Calculate surface dimensions maintaining aspect ratio
     * Returns: IntArray with [width, height, offsetX, offsetY]
     */
    private fun calculateSurfaceDimensions(): IntArray {
        // Video aspect ratio (1920x1080 = 16:9 ≈ 1.777)
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
        
        Log.i(TAG, "calculateSurfaceDimensions: videoAspect=$videoAspect (16/9=${16f/9f}), screenAspect=$screenAspect")
        Log.i(TAG, "Screen: ${screenWidth}x${screenHeight}, Video: ${videoWidth}x${videoHeight}")
        Log.i(TAG, "isLandscape=$isLandscapeMode")
        
        var surfaceWidth: Int
        var surfaceHeight: Int
        var offsetX: Int
        var offsetY: Int
        
        if (videoAspect > screenAspect) {
            // Video is wider than screen (letterbox - black bars top/bottom)
            // Scale to full width, height adjusted for aspect ratio
            surfaceWidth = screenWidth
            surfaceHeight = (screenWidth / videoAspect).toInt()
            offsetX = 0
            offsetY = (screenHeight - surfaceHeight) / 2
            Log.i(TAG, "Letterbox mode: scaling to full width ${screenWidth}px")
        } else {
            // Video is narrower than screen (pillarbox - black bars left/right)
            // Scale to full height, width adjusted for aspect ratio
            surfaceHeight = screenHeight
            surfaceWidth = (surfaceHeight * videoAspect).toInt()
            offsetX = (screenWidth - surfaceWidth) / 2
            offsetY = 0
            Log.i(TAG, "Pillarbox mode: scaling to full height ${screenHeight}px")
        }
        
        Log.i(TAG, "FINAL: ${surfaceWidth}x${surfaceHeight} at offset ($offsetX, $offsetY)")
        return intArrayOf(surfaceWidth, surfaceHeight, offsetX, offsetY)
    }

    /**
     * Create floating surface view for video rendering with control buttons
     */
    private fun createSurfaceView() {
        try {
            // Set initial orientation to portrait
            setOrientation(false)
            
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Get screen dimensions
            val displayMetrics = resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            Log.i(TAG, "Screen size: ${screenWidth}x${screenHeight}")
            
            // Calculate surface dimensions based on aspect ratio
            val dimensions = calculateSurfaceDimensions()
            val surfaceWidth = dimensions[0]
            val surfaceHeight = dimensions[1]
            val offsetX = dimensions[2]
            val offsetY = dimensions[3]
            Log.i(TAG, "Surface dimensions: ${surfaceWidth}x${surfaceHeight}, offset: ($offsetX, $offsetY)")

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
                            configure(videoWidth, videoHeight)
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
            
            // Create black background container
            backgroundView = android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            
            val bgParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            windowManager?.addView(backgroundView, bgParams)
            
            // Add surface view on top with proper dimensions and offset
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            
            val params = WindowManager.LayoutParams(
                surfaceWidth,
                surfaceHeight,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = offsetX
                y = offsetY
            }

            windowManager?.addView(surfaceView, params)
            Log.i(TAG, "Surface view added to window manager at ${screenWidth}x${screenHeight}")
            
            // Create control buttons overlay
            createControlButtons(layoutType)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create surface view", e)
            broadcastStatus("error", "Failed to create overlay: ${e.message}")
        }
    }

    /**
     * Create control buttons (Portrait, Landscape, Close)
     */
    private fun createControlButtons(layoutType: Int) {
        // Portrait Button
        portraitButton = android.widget.Button(this).apply {
            text = "📱 Portrait"
            setBackgroundColor(android.graphics.Color.parseColor("#2196F3")) // Blue when active
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(20, 10, 20, 10)
            
            setOnClickListener {
                Log.i(TAG, "Portrait button pressed")
                setOrientation(false)
            }
        }
        
        val portraitParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 80  // Below status bar
        }
        
        windowManager?.addView(portraitButton, portraitParams)
        
        // Landscape Button
        landscapeButton = android.widget.Button(this).apply {
            text = "🖥️ Landscape"
            setBackgroundColor(android.graphics.Color.parseColor("#757575")) // Gray when inactive
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(20, 10, 20, 10)
            
            setOnClickListener {
                Log.i(TAG, "Landscape button pressed")
                setOrientation(true)
            }
        }
        
        val landscapeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200  // Next to portrait button
            y = 80
        }
        
        windowManager?.addView(landscapeButton, landscapeParams)
        
        // Close Button
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
        
        // Timer TextView (under close button)
        timerTextView = android.widget.TextView(this).apply {
            text = "0.000s"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setBackgroundColor(android.graphics.Color.parseColor("#99000000")) // Semi-transparent black
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER
        }
        
        val timerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 140  // Below close button
        }
        
        windowManager?.addView(timerTextView, timerParams)
        
        // Start timer
        startStreamTimer()
        
        Log.i(TAG, "Control buttons and timer added")
    }
    
    /**
     * Set screen orientation and update UI
     */
    private fun setOrientation(landscape: Boolean) {
        isLandscapeMode = landscape
        
        Log.i(TAG, ">>> setOrientation called: landscape=$landscape <<<")
        Log.i(TAG, "Current screen before orientation: ${screenWidth}x${screenHeight}")
        
        // Request orientation change via broadcast to activity
        val intent = android.content.Intent("com.example.screeenc.ORIENTATION_CHANGE").apply {
            putExtra("landscape", landscape)
        }
        sendBroadcast(intent)
        Log.i(TAG, "Broadcast sent to MainActivity")
        
        // Wait for activity to change orientation and display metrics to update
        serviceScope.launch {
            // First delay - let Android process the rotation
            delay(300)
            
            // Re-read screen dimensions - they should have swapped
            val displayMetrics = resources.displayMetrics
            val newScreenWidth = displayMetrics.widthPixels
            val newScreenHeight = displayMetrics.heightPixels
            
            Log.i(TAG, "After 300ms - Display metrics: ${newScreenWidth}x${newScreenHeight}")
            Log.i(TAG, "Dimension change: (${screenWidth}x${screenHeight}) → (${newScreenWidth}x${newScreenHeight})")
            
            // If dimensions haven't changed, wait longer
            if ((landscape && newScreenWidth <= newScreenHeight) || 
                (!landscape && newScreenWidth > newScreenHeight)) {
                Log.w(TAG, "Dimensions haven't updated yet, waiting another 300ms...")
                delay(300)
                
                // Final read
                val finalMetrics = resources.displayMetrics
                val finalWidth = finalMetrics.widthPixels
                val finalHeight = finalMetrics.heightPixels
                Log.i(TAG, "After additional 300ms - Final display metrics: ${finalWidth}x${finalHeight}")
                
                screenWidth = finalWidth
                screenHeight = finalHeight
            } else {
                screenWidth = newScreenWidth
                screenHeight = newScreenHeight
            }
            
            Log.i(TAG, "=== SCREEN DIMENSIONS UPDATED ===")
            Log.i(TAG, "New screen: ${screenWidth}x${screenHeight}")
            Log.i(TAG, "Target: ${if (landscape) "LANDSCAPE (W>H)" else "PORTRAIT (H>W)"}")
            
            // Recalculate surface dimensions with updated screen dimensions
            val dimensions = calculateSurfaceDimensions()
            val surfaceWidth = dimensions[0]
            val surfaceHeight = dimensions[1]
            val offsetX = dimensions[2]
            val offsetY = dimensions[3]
            
            Log.i(TAG, "New surface layout: ${surfaceWidth}x${surfaceHeight} at (${offsetX}, ${offsetY})")
            
            // Update surface view position and size
            surfaceView?.let {
                val params = WindowManager.LayoutParams(
                    surfaceWidth,
                    surfaceHeight,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = offsetX
                    y = offsetY
                }
                try {
                    windowManager?.updateViewLayout(it, params)
                    Log.i(TAG, "✓ Surface view layout updated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ ERROR updating surface layout", e)
                }
            }
            
            // Update button colors to reflect active orientation
            portraitButton?.setBackgroundColor(
                android.graphics.Color.parseColor(if (!landscape) "#2196F3" else "#757575")
            )
            landscapeButton?.setBackgroundColor(
                android.graphics.Color.parseColor(if (landscape) "#2196F3" else "#757575")
            )
            Log.i(TAG, "✓ Button colors updated")
            
            Log.i(TAG, ">>> setOrientation COMPLETE: ${if (landscape) "LANDSCAPE" else "PORTRAIT"} <<<")
        }
    }

    /**
     * Remove surface view and all overlays
     */
    private fun removeSurfaceView() {
        Log.i(TAG, ">>> removeSurfaceView called - cleaning up all overlays <<<")
        
        try {
            // Remove portrait button
            portraitButton?.let {
                try {
                    Log.d(TAG, "Removing portrait button...")
                    windowManager?.removeView(it)
                    Log.d(TAG, "✓ Portrait button removed")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error removing portrait button", e)
                }
            }
            portraitButton = null
            
            // Remove landscape button
            landscapeButton?.let {
                try {
                    Log.d(TAG, "Removing landscape button...")
                    windowManager?.removeView(it)
                    Log.d(TAG, "✓ Landscape button removed")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error removing landscape button", e)
                }
            }
            landscapeButton = null
            
            // Remove close button
            closeButton?.let {
                try {
                    Log.d(TAG, "Removing close button...")
                    windowManager?.removeView(it)
                    Log.d(TAG, "✓ Close button removed")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error removing close button", e)
                }
            }
            closeButton = null
            
            // Remove timer
            timerTextView?.let {
                try {
                    Log.d(TAG, "Removing timer...")
                    windowManager?.removeView(it)
                    Log.d(TAG, "✓ Timer removed")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error removing timer", e)
                }
            }
            timerTextView = null
            
            // Stop timer job
            stopStreamTimer()
            
            // Remove surface view (MUST be removed before background view)
            surfaceView?.let {
                try {
                    Log.d(TAG, "Removing surface view...")
                    windowManager?.removeView(it)
                    Log.d(TAG, "✓ Surface view removed")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error removing surface view", e)
                }
            }
            surfaceView = null
            
            // CRITICAL: Remove background view LAST - it fills entire screen!
            backgroundView?.let {
                try {
                    Log.d(TAG, "Removing background view (CRITICAL - fills screen)...")
                    windowManager?.removeView(it)
                    Log.d(TAG, "✓ Background view removed")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error removing background view - THIS CAUSES DARK SCREEN", e)
                }
            }
            backgroundView = null
            
            Log.i(TAG, ">>> ALL OVERLAYS REMOVED SUCCESSFULLY <<<")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Critical error in removeSurfaceView", e)
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
    
    /**
     * Start streaming timer
     */
    private fun startStreamTimer() {
        streamStartTime = System.currentTimeMillis()
        timerJob = serviceScope.launch {
            while (isActive && isStreaming) {  // Check both isActive AND isStreaming
                val elapsed = System.currentTimeMillis() - streamStartTime
                val seconds = elapsed / 1000
                val millis = elapsed % 1000
                
                timerTextView?.text = String.format("%d.%03ds", seconds, millis)
                
                delay(50) // Update every 50ms for smooth milliseconds
            }
            Log.d(TAG, "Stream timer loop ended (isStreaming=$isStreaming)")
        }
        Log.d(TAG, "Stream timer started")
    }
    
    /**
     * Stop streaming timer
     */
    private fun stopStreamTimer() {
        timerJob?.cancel()
        timerJob = null
        Log.d(TAG, "Stream timer stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
    }
}

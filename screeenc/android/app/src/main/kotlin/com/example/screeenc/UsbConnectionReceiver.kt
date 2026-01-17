package com.example.screeenc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.util.Log

/**
 * Broadcast Receiver for USB Connection Events
 * Automatically starts/stops video receiver service based on USB connection state
 */
class UsbConnectionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UsbConnectionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                handlePowerConnected(context, intent)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                handlePowerDisconnected(context)
            }
            "android.hardware.usb.action.USB_STATE" -> {
                handleUsbState(context, intent)
            }
        }
    }

    /**
     * Handle power connected event
     * This could be USB or AC power
     */
    private fun handlePowerConnected(context: Context, intent: Intent) {
        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB

        if (usbCharge) {
            Log.i(TAG, "USB power detected - starting receiver service")
            startReceiverService(context)
        } else {
            Log.d(TAG, "AC power detected - not starting service")
        }
    }

    /**
     * Handle power disconnected event
     */
    private fun handlePowerDisconnected(context: Context) {
        Log.i(TAG, "Power disconnected - stopping receiver service")
        stopReceiverService(context)
    }

    /**
     * Handle USB state change
     */
    private fun handleUsbState(context: Context, intent: Intent) {
        val connected = intent.extras?.getBoolean("connected", false) ?: false
        
        Log.d(TAG, "USB state changed - connected: $connected")
        
        if (connected) {
            Log.i(TAG, "USB connected - starting receiver service")
            startReceiverService(context)
        } else {
            Log.i(TAG, "USB disconnected - stopping receiver service")
            stopReceiverService(context)
        }
    }

    /**
     * Start the video receiver service
     */
    private fun startReceiverService(context: Context) {
        try {
            val serviceIntent = Intent(context, VideoReceiverService::class.java).apply {
                action = VideoReceiverService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start receiver service", e)
        }
    }

    /**
     * Stop the video receiver service
     */
    private fun stopReceiverService(context: Context) {
        try {
            val serviceIntent = Intent(context, VideoReceiverService::class.java).apply {
                action = VideoReceiverService.ACTION_STOP
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop receiver service", e)
        }
    }
}

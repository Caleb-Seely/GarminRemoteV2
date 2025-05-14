package com.garmin.android.apps.camera.click.comm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.garmin.android.apps.camera.click.comm.CameraAccessibilityService
import com.garmin.android.apps.camera.click.comm.utils.NotificationUtils
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.exception.InvalidStateException

private const val TAG = "MessageService"

class MessageService : Service() {
    private lateinit var connectIQ: ConnectIQ
    private lateinit var device: IQDevice
    private lateinit var app: IQApp
    private var isServiceRunning = false

    companion object {
        private const val EXTRA_DEVICE = "device"
        private const val EXTRA_APP_ID = "app_id"

        fun createIntent(context: Context, device: IQDevice, appId: String): Intent {
            return Intent(context, MessageService::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
                putExtra(EXTRA_APP_ID, appId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        NotificationUtils.createNotificationChannel(this)
        connectIQ = ConnectIQ.getInstance()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        if (intent == null) {
            Log.e(TAG, "Service started with null intent")
            stopSelf()
            return START_NOT_STICKY
        }

        device = intent.getParcelableExtra(EXTRA_DEVICE) ?: run {
            Log.e(TAG, "No device provided in intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: run {
            Log.e(TAG, "No app ID provided in intent")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isServiceRunning) {
            Log.d(TAG, "Starting service for device: ${device.friendlyName}")
            app = IQApp(appId)
            
            try {
                val notification = NotificationUtils.createForegroundNotification(this, device)
                Log.d(TAG, "Created notification with flags: ${notification.flags}")
                
                startForeground(NotificationUtils.NOTIFICATION_ID, notification)
                Log.d(TAG, "Started foreground service with notification")
                
                registerForMessages()
                isServiceRunning = true
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification", e)
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            Log.d(TAG, "Service already running for device: ${device.friendlyName}")
        }
        
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        isServiceRunning = false
        try {
            connectIQ.unregisterForApplicationEvents(device, app)
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error unregistering for app events", e)
        }
        super.onDestroy()
    }

    private fun registerForMessages() {
        try {
            connectIQ.registerForAppEvents(device, app) { _, _, message, _ ->
                Log.d(TAG, "Received message: ${message.joinToString()}")
                
                val intent = Intent(CameraAccessibilityService.ACTION_MESSAGE_RECEIVED).apply {
                    putExtra(CameraAccessibilityService.EXTRA_MESSAGE, message.joinToString())
                }
                sendBroadcast(intent)
                Log.d(TAG, "Broadcasted message to CameraAccessibilityService")
            }
            Log.d(TAG, "Successfully registered for app events")
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Failed to register for app events", e)
            stopSelf()
        }
    }
} 
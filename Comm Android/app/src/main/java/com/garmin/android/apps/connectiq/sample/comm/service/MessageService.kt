package com.garmin.android.apps.connectiq.sample.comm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.apps.connectiq.sample.comm.activities.DeviceActivity
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.exception.InvalidStateException

private const val TAG = "MessageService"
private const val NOTIFICATION_CHANNEL_ID = "GarminMessageChannel"
private const val NOTIFICATION_ID = 1

/**
 * Foreground service that maintains an active connection to Garmin devices
 * and handles message reception even when the app is in the background.
 */
class MessageService : Service() {
    private lateinit var connectIQ: ConnectIQ
    private lateinit var device: IQDevice
    private lateinit var app: IQApp

    companion object {
        private const val EXTRA_DEVICE = "device"
        private const val EXTRA_APP_ID = "app_id"

        /**
         * Creates an intent to start the MessageService with the specified device and app.
         * @param context The context to create the intent
         * @param device The Garmin device to communicate with
         * @param appId The ID of the app to receive messages from
         * @return An intent configured to start the MessageService
         */
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
        createNotificationChannel()
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

        app = IQApp(appId)
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Register for messages
        registerForMessages()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        try {
            connectIQ.unregisterForApplicationEvents(device, app)
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error unregistering for app events", e)
        }
        super.onDestroy()
    }

    /**
     * Creates the notification channel for Android O and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Garmin Message Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection to Garmin device active"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates the foreground service notification.
     * @return The notification to display
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, DeviceActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Garmin Message Service")
            .setContentText("Listening for messages from ${device.friendlyName}")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Registers for message events from the Garmin device.
     */
    private fun registerForMessages() {
        try {
            connectIQ.registerForAppEvents(device, app) { _, _, message, _ ->
                Log.d(TAG, "Received message: ${message.joinToString()}")
                // Here you could broadcast the message to the app or store it
                // for later processing
            }
            Log.d(TAG, "Successfully registered for app events")
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Failed to register for app events", e)
            stopSelf()
        }
    }
} 
package com.garmin.android.apps.camera.click.comm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Bundle
import android.util.Log
import com.garmin.android.apps.camera.click.comm.utils.NotificationUtils
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.google.firebase.analytics.FirebaseAnalytics

private const val TAG = "MessageService"

/**
 * Background service that handles communication between the Android app and Garmin device.
 * This service is responsible for:
 * - Maintaining a persistent connection with the Garmin device
 * - Receiving and processing messages from the device
 * - Sending messages to the device
 * - Forwarding messages to the CameraAccessibilityService
 * - Managing the service lifecycle and notifications
 */
class MessageService : Service() {
    private lateinit var connectIQ: ConnectIQ
    private lateinit var device: IQDevice
    private lateinit var app: IQApp
    private var isServiceRunning = false
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    companion object {
        private const val EXTRA_DEVICE = "device"
        private const val EXTRA_APP_ID = "app_id"
        private const val ACTION_SEND_MESSAGE = "SEND_MESSAGE"
        private const val EXTRA_MESSAGE_TYPE = "message_type"
        private const val EXTRA_MESSAGE_DETAILS = "message_details"

        // Message types for watch communication
        const val MESSAGE_TYPE_SHUTTER_SUCCESS = "SHUTTER_SUCCESS"
        const val MESSAGE_TYPE_SHUTTER_FAILED = "SHUTTER_FAILED"

        /**
         * Creates an intent to start the MessageService with the specified device and app.
         * @param context The context to create the intent
         * @param device The Garmin device to communicate with
         * @param appId The ID of the companion app
         * @return An intent configured to start the MessageService
         */
        fun createIntent(context: Context, device: IQDevice, appId: String): Intent {
            return Intent(context, MessageService::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
                putExtra(EXTRA_APP_ID, appId)
            }
        }
    }

    /**
     * Called when the service is created.
     * Initializes the service components and creates the notification channel.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        NotificationUtils.createNotificationChannel(this)
        connectIQ = ConnectIQ.getInstance()
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
    }

    /**
     * Called when the service is started.
     * Sets up the connection with the Garmin device and starts listening for messages.
     * 
     * @param intent The intent that started the service
     * @param flags Additional data about this start request
     * @param startId A unique integer representing this specific request to start
     * @return The return value indicates what semantics the system should use for the service's current started state
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        if (intent == null) {
            Log.e(TAG, "Service started with null intent")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_SEND_MESSAGE -> {
                if (!isServiceRunning) {
                    Log.e(TAG, "Service not running, cannot send message")
                    return START_NOT_STICKY
                }
                val messageType = intent.getStringExtra(EXTRA_MESSAGE_TYPE)
                val messageDetails = intent.getStringExtra(EXTRA_MESSAGE_DETAILS)
                if (messageType != null) {
                    sendMessage(messageType, messageDetails)
                }
                return START_NOT_STICKY
            }
            else -> {
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
        }
    }

    /**
     * Called when a client binds to the service.
     * This service does not support binding, so this method returns null.
     * 
     * @param intent The intent that was used to bind to this service
     * @return null since this service does not support binding
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the service is destroyed.
     * Performs cleanup of resources and unregisters message listeners.
     */
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

    /**
     * Sets up the message listener for receiving messages from the Garmin device.
     * This method:
     * - Registers a listener for messages from the companion app
     * - Forwards received messages to the CameraAccessibilityService
     * - Logs message events for analytics
     */
    private fun registerForMessages() {
        try {
            connectIQ.registerForAppEvents(device, app) { _, _, message, _ ->
                Log.d(TAG, "Received message: ${message.joinToString()}")
                val messageReceivedTime = System.currentTimeMillis()

                val bundle = Bundle().apply {
                    putString("device_name", device.friendlyName)
                    putString("device_id", device.deviceIdentifier.toString())
                    putString("message", message.joinToString())
                    putLong("message_received_time", messageReceivedTime)
                }
                firebaseAnalytics.logEvent("message_received_from_watch", bundle)
                
                val intent = Intent(CameraAccessibilityService.ACTION_MESSAGE_RECEIVED).apply {
                    putExtra(CameraAccessibilityService.EXTRA_MESSAGE, message.joinToString())
                    putExtra(CameraAccessibilityService.EXTRA_MESSAGE_TIME, messageReceivedTime)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                sendBroadcast(intent, null)
                Log.d(TAG, "Broadcast message to CameraAccessibilityService")
            }
            Log.d(TAG, "Successfully registered for app events")
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Failed to register for app events", e)
            stopSelf()
        }
    }

    /**
     * Sends a message to the connected Garmin watch.
     * @param messageType The type of message to send (e.g., SHUTTER_SUCCESS, SHUTTER_FAILED)
     * @param details Optional details about the message
     * @return true if the message was sent successfully, false otherwise
     */
    private fun sendMessage(messageType: String, details: String? = null): Boolean {
        if (!isServiceRunning) {
            Log.e(TAG, "MessageService not running")
            return false
        }

        return try {
            // Send only the details to the watch instead of the full message map
            connectIQ.sendMessage(device, app, details) { _, _, status ->
                Log.d(TAG, "Message type: $messageType")
                Log.d(TAG, "Message content: $details")
                Log.d(TAG, "Message send status: ${status.name}")

                // Log the total response time
                val bundle = Bundle().apply {
                    putString("message_type", messageType)
                    putString("message_content", details ?: "")
                    putString("send_status", status.name)
                }
                firebaseAnalytics.logEvent("message_sent_to_watch", bundle)
            }
            true
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error sending message: ConnectIQ is not in a valid state", e)
            false
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Error sending message: ConnectIQ service is unavailable", e)
            false
        }
    }
} 
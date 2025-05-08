package com.garmin.android.apps.connectiq.sample.comm

import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException


/**
 * Data class representing a message that can be sent to a Garmin device.
 * @property text The display text for the message
 * @property payload The actual data to be sent to the device
 */
data class Message(val text: String, val payload: Any)

/**
 * Service class that handles communication with a connected Garmin watch.
 * This class manages the ConnectIQ connection and provides methods for sending messages to the watch.
 */
class WatchMessagingService {
    companion object {
        private const val TAG = "WatchMessaging"
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"

        // Message types for watch communication
        const val MESSAGE_TYPE_SHUTTER_SUCCESS = "SHUTTER_SUCCESS"
        const val MESSAGE_TYPE_SHUTTER_FAILED = "SHUTTER_FAILED"
    }

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp
    private var isInitialized = false

    /**
     * Initializes the service with a connected Garmin device.
     * @return true if initialization was successful, false otherwise
     */
    fun initialize(): Boolean {
        return try {
            device = connectIQ.connectedDevices?.firstOrNull() ?: run {
                Log.e(TAG, "No connected Garmin device found")
                return false
            }
            myApp = IQApp(COMM_WATCH_ID)
            isInitialized = true
            Log.d(TAG, "WatchMessagingService initialized with device: ${device.friendlyName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WatchMessagingService", e)
            false
        }
    }

    /**
     * Sends a message to the connected Garmin watch.
     * @param messageType The type of message to send (e.g., SHUTTER_SUCCESS, SHUTTER_FAILED)
     * @param details Optional details about the message
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendMessage(messageType: String, details: String? = null): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "WatchMessagingService not initialized")
            return false
        }

        return try {
            // Send only the details to the watch instead of the full message map
            connectIQ.sendMessage(device, myApp, details) { _, _, status ->
                Log.d(TAG, "Message type: $messageType")
                Log.d(TAG, "Message content: $details")
                Log.d(TAG, "Message send status: ${status.name}")
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

    /**
     * Checks if the service is initialized and ready to send messages.
     * @return true if the service is initialized, false otherwise
     */
    fun isReady(): Boolean = isInitialized
} 
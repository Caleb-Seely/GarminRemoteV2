package com.garmin.android.apps.camera.click.comm

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQApp
import com.garmin.android.apps.camera.click.comm.utils.AccessibilityUtils

/**
 * CameraAccessibilityService is an Android Accessibility Service that:
 * 1. Listens for messages from a connected device (e.g., smartwatch)
 * 2. Detects when a camera app is active
 * 3. Automatically triggers the camera shutter when a message is received
 * 
 * This service requires the ACCESSIBILITY_SERVICE permission and must be enabled
 * in the device's accessibility settings.
 */
class CameraAccessibilityService : AccessibilityService() {
    companion object {
        // Tag for logging
        private const val TAG = "CameraAccessibility"
        
        // Debug flag to enable/disable additional logging and toasts
        private const val DEBUG = true
        
        // Set of known camera app package names to detect when a camera app is active
        private val CAMERA_PACKAGES = setOf(
            "com.google.android.GoogleCamera",  // Google Camera
            "com.sec.android.camera",           // Samsung Camera
            "com.android.camera",               // Generic Android Camera
            "com.android.camera2",              // Android Camera2 API
            "com.android.camera"                // Common package name for many camera apps
        )
        


        // Intent action and extra key for receiving messages
        const val ACTION_MESSAGE_RECEIVED = "com.garmin.android.apps.camera.click.comm.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "message"

        // Message types for watch communication
        private const val MESSAGE_TYPE_SHUTTER_SUCCESS = "SHUTTER_SUCCESS"
        private const val MESSAGE_TYPE_SHUTTER_FAILED = "SHUTTER_FAILED"
        
        // ConnectIQ constants
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
    }

    // Handler for posting delayed tasks to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Broadcast receiver for listening to incoming messages
    private lateinit var messageReceiver: BroadcastReceiver
    
    // Flag to track if the service is connected and ready
    private var isServiceConnected = false
    
    // Store messages received before the service is fully connected
    private var pendingMessage: String? = null
    
    // Track the last known camera app package name
    private var lastKnownCameraPackage: String? = null

    // ConnectIQ instance for sending messages
    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp

    // Watch messaging service
    private val watchMessagingService = WatchMessagingService()

    /**
     * Called when the accessibility service is connected.
     * Sets up the message receiver and processes any pending messages.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected called")
        isServiceConnected = true
        if (DEBUG) {
            Toast.makeText(this, "Accessibility service connected", Toast.LENGTH_SHORT).show()
        }

        // Initialize ConnectIQ
        try {
            device = connectIQ.connectedDevices?.firstOrNull() ?: run {
                Log.e(TAG, "No connected Garmin device found")
                return
            }
            myApp = IQApp(COMM_WATCH_ID)
            Log.d(TAG, "ConnectIQ initialized with device: ${device.friendlyName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ConnectIQ", e)
        }

        // Initialize watch messaging service
        if (!watchMessagingService.initialize()) {
            Log.e(TAG, "Failed to initialize watch messaging service")
        }

        setupMessageReceiver()
        
        // Process any message that arrived before the service was connected
        pendingMessage?.let { message ->
            Log.d(TAG, "Processing pending message: $message")
            handleCameraTrigger()
            pendingMessage = null
        }
    }

    /**
     * Sets up the broadcast receiver to listen for incoming messages.
     * The receiver will either process the message immediately or store it
     * if the service isn't fully connected yet.
     */
    private fun setupMessageReceiver() {
        Log.d(TAG, "Setting up message receiver")
        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast received with action: ${intent?.action}")
                if (intent?.action == ACTION_MESSAGE_RECEIVED) {
                    val message = intent.getStringExtra(EXTRA_MESSAGE)
                    Log.d(TAG, "Processing message: $message")
                    
                    if (isServiceConnected) {
                        // Process the message on the main thread
                        mainHandler.post {
                            handleCameraTrigger()
                        }
                    } else {
                        // Store the message for later processing
                        Log.d(TAG, "Service not yet connected, storing message for later processing")
                        pendingMessage = message
                    }
                }
            }
        }

        try {
            val filter = IntentFilter(ACTION_MESSAGE_RECEIVED)
            registerReceiver(messageReceiver, filter)
            Log.d(TAG, "Message receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering message receiver", e)
        }
    }

    /**
     * Handles the camera trigger action:
     * 1. Checks if a camera app is active
     * 2. If active, waits a short delay for the app to be ready
     * 3. Attempts to find and click the shutter button
     */
    private fun handleCameraTrigger() {
        Log.d(TAG, "handleCameraTrigger called")
        if (!isCameraAppActive()) {
            Log.d(TAG, "No camera app is currently active")
            watchMessagingService.sendMessage(
                WatchMessagingService.MESSAGE_TYPE_SHUTTER_FAILED,
                "Open camera app"
            )
            return
        }
        
            val shutterButton = findShutterButton()
            if (shutterButton != null) {
                Log.d(TAG, "Found shutter button, attempting to trigger")
                triggerShutter(shutterButton)
            } else {
                Log.d(TAG, "Could not find shutter button in active camera app")
            }

    }

    /**
     * Checks if a camera app is currently active by:
     * 1. Getting the root window of the active app
     * 2. Checking if its package name matches known camera apps
     * 
     * @return true if a camera app is active, false otherwise
     */
    private fun isCameraAppActive(): Boolean {
        Log.d(TAG, "Checking if camera app is active")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow is null")
            return false
        }
        
        val packageName = root.packageName?.toString()
        if (packageName == null) {
            Log.d(TAG, "packageName is null")
            return false
        }
        
        val isCameraApp = CAMERA_PACKAGES.contains(packageName)
        if (isCameraApp) {
            lastKnownCameraPackage = packageName
        }
        Log.d(TAG, "Current app: $packageName, is camera app: $isCameraApp")
        return isCameraApp
    }

    /**
     * Attempts to find the shutter button in the active camera app by:
     * 1. Finding the largest clickable node on the screen
     * 
     * @return The AccessibilityNodeInfo of the shutter button if found, null otherwise
     */
    private fun findShutterButton(): AccessibilityNodeInfo? {
        Log.d(TAG, "Attempting to find shutter button")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow is null in findShutterButton")
            return null
        }

        val shutterButton = AccessibilityUtils.largestClickableNode(root, TAG)
        if (shutterButton != null) {
            Log.d(TAG, "Found largest clickable node as shutter button")
            return shutterButton
        }

        Log.d(TAG, "No clickable nodes found on screen")
        return null
    }

    /**
     * Attempts to trigger the camera shutter by clicking the provided button.
     * 
     * @param button The AccessibilityNodeInfo of the shutter button to click
     */
    private fun triggerShutter(button: AccessibilityNodeInfo) {
        Log.d(TAG, "Attempting to trigger shutter button")
        if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "Successfully triggered shutter button")
            if (DEBUG) {
                Toast.makeText(this, "Photo taken!", Toast.LENGTH_SHORT).show()
            }
            watchMessagingService.sendMessage(
                WatchMessagingService.MESSAGE_TYPE_SHUTTER_SUCCESS,
                "Success!"
            )
        } else {
            Log.e(TAG, "Failed to click shutter button")
            watchMessagingService.sendMessage(
                WatchMessagingService.MESSAGE_TYPE_SHUTTER_FAILED,
                "Error tapping shutter"
            )
        }
    }

    /**
     * Called when an accessibility event occurs.
     * Logs detailed information about the event for debugging.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!DEBUG) return

        val packageName = event.packageName?.toString() ?: "unknown"
        val eventType = getEventTypeString(event.eventType)
        val className = event.className?.toString() ?: "unknown"
        val source = event.source?.toString() ?: "unknown"

        Log.d(TAG, """
            Package: $packageName
            Event Type: $eventType
            Class: $className
            Source: $source
            Event Time: ${event.eventTime}
            Window ID: ${event.windowId}
        """.trimIndent())

        // Log window state changes
        if (eventType == "TYPE_WINDOW_STATE_CHANGED") {
            Log.d(TAG, "Window state changed for package: $packageName")
            if (CAMERA_PACKAGES.contains(packageName)) {
                lastKnownCameraPackage = packageName
                Log.d(TAG, "Camera app window state changed: $packageName")
            }
        }
    }

    /**
     * Called when the accessibility service is interrupted.
     * Cleans up resources and unregisters the message receiver.
     */
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        isServiceConnected = false
        try {
            unregisterReceiver(messageReceiver)
            Log.d(TAG, "Message receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    /**
     * Called when the accessibility service is destroyed.
     * Cleans up resources and unregisters the message receiver.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service being destroyed")
        isServiceConnected = false
        super.onDestroy()
        try {
            unregisterReceiver(messageReceiver)
            Log.d(TAG, "Message receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Converts an accessibility event type integer to a human-readable string.
     * 
     * @param eventType The accessibility event type integer
     * @return A string representation of the event type
     */
    private fun getEventTypeString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "TYPE_VIEW_SELECTED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            else -> "UNKNOWN_TYPE($eventType)"
        }
    }

} 
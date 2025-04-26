package com.garmin.android.apps.connectiq.sample.comm

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
        
        // Set of possible content descriptions for the shutter button
        // Used to locate the shutter button in different camera apps
        private val SHUTTER_BUTTON_DESCRIPTIONS = setOf(
            "Shutter",
            "Take photo",
            "Capture",
            "Take picture",
            "Shutter button"
        )

        // Intent action and extra key for receiving messages
        const val ACTION_MESSAGE_RECEIVED = "com.garmin.android.apps.connectiq.sample.comm.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "message"
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
            return
        }

        // Add a small delay to ensure the camera app is fully ready
        mainHandler.postDelayed({
            val shutterButton = findShutterButton()
            if (shutterButton != null) {
                Log.d(TAG, "Found shutter button, attempting to trigger")
                triggerShutter(shutterButton)
            } else {
                Log.d(TAG, "Could not find shutter button in active camera app")
            }
        }, 500) // 500ms delay
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
     * 1. First trying to find it by content description
     * 2. If not found, looking for a clickable button in the center of the screen
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
        
        // First try to find by content description
        for (description in SHUTTER_BUTTON_DESCRIPTIONS) {
            Log.d(TAG, "Searching for button with description: $description")
            val nodes = root.findAccessibilityNodeInfosByText(description)
            if (nodes.isNotEmpty()) {
                Log.d(TAG, "Found ${nodes.size} nodes with description: $description")
                val button = nodes.firstOrNull { it.isClickable }
                if (button != null) {
                    Log.d(TAG, "Found clickable shutter button with description: $description")
                    return button
                }
            }
        }

        // If not found by description, try to find any clickable button in the center of the screen
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        Log.d(TAG, "Falling back to center position search at ($centerX, $centerY)")
        return findClickableButtonAtPosition(root, centerX, centerY)
    }

    /**
     * Searches for a clickable button at a specific screen position by:
     * 1. First checking for a focused node
     * 2. If not found, searching through all content nodes
     * 
     * @param root The root AccessibilityNodeInfo to search from
     * @param x The x-coordinate to search at
     * @param y The y-coordinate to search at
     * @return The AccessibilityNodeInfo of a clickable button if found, null otherwise
     */
    private fun findClickableButtonAtPosition(
        root: AccessibilityNodeInfo,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        Log.d(TAG, "Searching for clickable button at position ($x, $y)")
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (node != null && node.isClickable) {
            Log.d(TAG, "Found focused clickable node")
            return node
        }

        // If no focused node found, try to find any clickable node at the position
        val nodes = root.findAccessibilityNodeInfosByViewId("android:id/content")
        Log.d(TAG, "Found ${nodes.size} content nodes")
        for (node in nodes) {
            if (node.isClickable) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.contains(x, y)) {
                    Log.d(TAG, "Found clickable node at position")
                    return node
                }
            }
        }
        Log.d(TAG, "No clickable button found at position")
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
        } else {
            Log.e(TAG, "Failed to click shutter button")
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
package com.garmin.android.apps.camera.click.comm.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
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
import com.garmin.android.apps.camera.click.comm.utils.CameraGestureHandler
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.WatchMessagingService

private const val TAG = "CameraAcessabilityService"
/**
 * CameraAccessibilityService is an Android Accessibility Service that:
 * 1. Listens for messages from a connected device (e.g., smartwatch)
 * 2. Automatically triggers the camera shutter when a message is received
 * 
 * This service requires the ACCESSIBILITY_SERVICE permission and must be enabled
 * in the device's accessibility settings.
 */
class CameraAccessibilityService : AccessibilityService() {
    companion object {
        // Intent action and extra key for receiving messages
        const val ACTION_MESSAGE_RECEIVED = "com.garmin.android.apps.camera.click.comm.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "message"

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

    // Gesture handler for camera interactions
    private lateinit var gestureHandler: CameraGestureHandler

    /**
     * Called when the accessibility service is connected.
     * Sets up the message receiver and processes any pending messages.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected called")
        isServiceConnected = true

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

        // Initialize gesture handler
        gestureHandler = CameraGestureHandler(this)

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
     * 2. If active, tries to use saved button location or finds the button
     * 3. Attempts to click the shutter button
     */
    private fun handleCameraTrigger() {
        Log.d(TAG, "handleCameraTrigger called")
//        if (!isCameraAppActive()) {
//            Log.d(TAG, "No camera app is currently active")
//            watchMessagingService.sendMessage(
//                WatchMessagingService.MESSAGE_TYPE_SHUTTER_FAILED,
//                "Open camera app"
//            )
//            return
//        }

        // Get the current package name
        val packageName = rootInActiveWindow?.packageName?.toString() ?: return
        
        // Try to get saved button location first
        val savedButtonInfo = AccessibilityUtils.getLastKnownButtonInfo(packageName)
        if (savedButtonInfo != null) {
            Log.d(TAG, "Using saved button location for package: $packageName")
            // Create a new AccessibilityNodeInfo for the saved location
            val root = rootInActiveWindow
            if (root != null) {
                val node = AccessibilityUtils.findClickableNodeAtLocation(root, savedButtonInfo.bounds)
                if (node != null) {
                    triggerShutter(node)
                    return
                } else {
                    Log.d(TAG, "Saved location no longer has clickable node, searching again")
                }
            }
        }
            
        // If no saved location or it's no longer valid, find the button again
        val shutterButton = findShutterButton()
        if (shutterButton != null) {
            Log.d(TAG, "Found shutter button, attempting to trigger")
            triggerShutter(shutterButton)
        } else {
            Log.d(TAG, "Could not find shutter button in active camera app")
            watchMessagingService.sendMessage(
                WatchMessagingService.MESSAGE_TYPE_SHUTTER_FAILED,
                "Could not find shutter button"
            )
        }
    }

    /**
     * Checks if a camera app is currently active by:
     * 1. Getting the root window of the active app
     * 2. Checking if its package name matches known camera apps
     * 
     * @return true if a camera app is active, false otherwise
     */
//    private fun isCameraAppActive(): Boolean {
//        Log.d(TAG, "Checking if camera app is active")
//        val root = rootInActiveWindow
//        if (root == null) {
//            Log.d(TAG, "rootInActiveWindow is null")
//            return false
//        }
//
//        val packageName = root.packageName?.toString()
//        if (packageName == null) {
//            Log.d(TAG, "packageName is null")
//            return false
//        }
//
//        val isCameraApp = CAMERA_PACKAGES.contains(packageName)
//        if (isCameraApp) {
//            lastKnownCameraPackage = packageName
//        }
//        Log.d(TAG, "Current app: $packageName, is camera app: $isCameraApp")
//        return isCameraApp
//    }

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

        val packageName = root.packageName?.toString() ?: return null
        val shutterButton = AccessibilityUtils.largestClickableNode(root, packageName, TAG)
        if (shutterButton != null) {
            Log.d(TAG, "Found largest clickable node as shutter button")
            return shutterButton
        }

        Log.d(TAG, "No clickable nodes found on screen")
        return null
    }

    /**
     * Attempts to trigger the camera shutter by simulating a tap gesture on the provided button.
     * 
     * @param button The AccessibilityNodeInfo of the shutter button to tap
     */
    private fun triggerShutter(button: AccessibilityNodeInfo) {
        Log.d(TAG, "Attempting to trigger shutter button with multiple methods")

        // Get the button's bounds on screen
        val buttonBounds = Rect()
        button.getBoundsInScreen(buttonBounds)
        Log.d(TAG, "Button bounds: $buttonBounds")

        // Calculate the center point of the button
        val centerX = buttonBounds.centerX().toFloat()
        val centerY = buttonBounds.centerY().toFloat()
        Log.d(TAG, "Tap coordinates: ($centerX, $centerY)")

        // Try to perform a direct click first - this is often more reliable for actual buttons
        try {
            Log.d(TAG, "Attempting direct performAction CLICK on button")
            val clickResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Direct button click result: $clickResult")

            if (clickResult) {
                Log.d(TAG, "Direct button click successful")
                watchMessagingService.sendMessage(
                    WatchMessagingService.MESSAGE_TYPE_SHUTTER_SUCCESS,
                    "Success!"
                )
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during direct button click", e)
        }

        // Next try direct child button click if available
        try {
            for (i in 0 until button.childCount) {
                val child = button.getChild(i)
                if (child != null) {
                    Log.d(TAG, "Attempting click on child button $i")
                    val childClickResult = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Child button click result: $childClickResult")
                    if (childClickResult) {
                        Log.d(TAG, "Child button click successful")
                        watchMessagingService.sendMessage(
                            WatchMessagingService.MESSAGE_TYPE_SHUTTER_SUCCESS,
                            "Success with child button click!"
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during child button click", e)
        }

        // Fall back to using gestures if direct methods fail
        gestureHandler.attemptCameraGesture(centerX, centerY)
    }

    /**
     * Called when an accessibility event occurs.
     * Logs detailed information about the event for debugging.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Log click events
        Log.d(TAG, """
            ========== ACCESSIBILITY EVENT ==========
            Event Time: ${System.currentTimeMillis()}
            Package: ${event.packageName}
            Event Time Since Boot: ${event.eventTime}
            Window ID: ${event.windowId}
            Event Source: ${event.source}
            
            -- Event Properties --
            Class Name: ${event.className}
            Description: ${event.contentDescription}
            Text: ${event.text}
            Movement Granularity: ${event.movementGranularity}
            Action: ${event.action}
                       
            =======================================
        """.trimIndent())
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
} 
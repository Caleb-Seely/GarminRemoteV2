package com.garmin.android.apps.camera.click.comm.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Bundle
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
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.CommConstants
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils

private const val TAG = "CameraAccessibilityService"
/**
 * Accessibility service that handles camera trigger functionality.
 * This service is responsible for:
 * - Receiving messages from the Garmin device
 * - Detecting and interacting with camera apps
 * - Triggering camera actions (photo/video capture)
 * - Managing the accessibility service lifecycle
 */
class CameraAccessibilityService : AccessibilityService() {
    companion object {
        // Intent action and extra key for receiving messages
        const val ACTION_MESSAGE_RECEIVED = "com.garmin.android.apps.camera.click.comm.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_MESSAGE_TIME = "message_time"
    }

    // Handler for posting delayed tasks to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Broadcast receiver for listening to incoming messages
    private lateinit var messageReceiver: BroadcastReceiver
    
    // Flag to track if the service is connected and ready
    private var isServiceConnected = false
    
    // Store messages received before the service is fully connected
    private var pendingMessage: String? = null
    

    // ConnectIQ instance for sending messages
    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp

    // Track when a message is received
    private var messageReceivedTime: Long = 0

    /**
     * Called when the accessibility service is created.
     * Initializes necessary components and sets up the message receiver.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // Initialize AccessibilityUtils
        AccessibilityUtils.initialize(this)
        
        setupMessageReceiver()
    }

    /**
     * Sets up the broadcast receiver for incoming messages from the Garmin device.
     * This method:
     * - Creates a broadcast receiver to handle incoming messages
     * - Registers the receiver with the appropriate intent filter
     * - Processes messages either immediately or stores them for later
     */
    private fun setupMessageReceiver() {
        Log.d(TAG, "Setting up message receiver")
        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast received with action: ${intent?.action}")
                Toast.makeText(context, "Camera broadcast received", Toast.LENGTH_SHORT).show()
                if (intent?.action == ACTION_MESSAGE_RECEIVED) {
                    val message = intent.getStringExtra(EXTRA_MESSAGE)
                    messageReceivedTime = intent.getLongExtra(EXTRA_MESSAGE_TIME, System.currentTimeMillis())
                    Log.d(TAG, "Processing message: $message, received at: $messageReceivedTime")

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
            registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "Message receiver registered successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error registering message receiver", e)
        }
    }

    /**
     * Handles the camera trigger action:
     * 2. Tries to use saved button location or finds the button
     * 3. Attempts to click the shutter button
     */
    private fun handleCameraTrigger() {
        Log.d(TAG, "handleCameraTrigger called")
        FirebaseCrashlytics.getInstance().log("Camera command received from watch")

        // Initialize Firebase Analytics with debug mode
        AnalyticsUtils.initialize(this)
        Log.d(TAG, "Firebase Analytics initialized for camera command")

        val currentPackage = getCurrentApp()

        // Log camera command event
        AnalyticsUtils.logCameraCommand(
            deviceName = device.friendlyName,
            cameraPackage = currentPackage ?: "unknown",
            serviceState = if (isServiceConnected) "connected" else "disconnected"
        )

        // Get the current package name
        val packageName = rootInActiveWindow?.packageName?.toString() ?: run {
            FirebaseCrashlytics.getInstance().log("No active window package name found")
            return
        }
        
        // Try to get saved button location first
        val savedButtonInfo = AccessibilityUtils.getLastKnownButtonInfo()
        if (savedButtonInfo != null) {
            Log.d(TAG, "Using saved button location")
            Toast.makeText(this, "Using saved button location", Toast.LENGTH_SHORT).show()
            FirebaseCrashlytics.getInstance().log("Using saved button location")
            // Create a new AccessibilityNodeInfo for the saved location
            val root = rootInActiveWindow
            if (root != null) {
                val node = AccessibilityUtils.findClickableNodeAtLocation(root, savedButtonInfo.bounds)
                if (node != null) {
                    triggerShutter(node)
                    return
                } else {
                    Log.d(TAG, "Saved location no longer has clickable node, searching again")
                    FirebaseCrashlytics.getInstance().log("Saved button location no longer valid, searching again")
                }
            }
        }
            
        // If no saved location or it's no longer valid, find the button again
        val shutterButton = findShutterButton()
        if (shutterButton != null) {
            Log.d(TAG, "Found button, attempting to trigger")
            Toast.makeText(this, "Found largest button", Toast.LENGTH_SHORT).show()
            FirebaseCrashlytics.getInstance().log("Found shutter button in package: $packageName")
            triggerShutter(shutterButton)
        } else {
            Log.d(TAG, "Could not find button in active app")
            Toast.makeText(this, "No button found", Toast.LENGTH_SHORT).show()
            FirebaseCrashlytics.getInstance().log("No button found in package: $packageName")
            sendMessageToWatch(MessageService.MESSAGE_TYPE_SHUTTER_FAILED, "No shutter found")
        }
    }

    private fun getCurrentApp(): String? {
        val root = rootInActiveWindow
        val packageName = root?.packageName?.toString()
        return packageName
    }

    /**
     * Attempts to find the shutter button in the active camera app by:
     * 1. Finding the largest clickable node on the screen
     * 
     * @return The AccessibilityNodeInfo of the shutter button if found, null otherwise
     */
    private fun findShutterButton(): AccessibilityNodeInfo? {
        Log.d(TAG, "Attempting to largest node")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow is null in findShutterButton")
            return null
        }

        val packageName = root.packageName?.toString() ?: return null
        val shutterButton = AccessibilityUtils.largestClickableNode(root, packageName, TAG)
        if (shutterButton != null) {
            Log.d(TAG, "Found largest clickable node")
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
        Log.d(TAG, "Attempting to trigger shutter button")
        FirebaseCrashlytics.getInstance().log("Attempting to trigger shutter button")

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
                FirebaseCrashlytics.getInstance().log("Shutter button clicked successfully")
                
                // Calculate and log time spent
                val timeSpent = System.currentTimeMillis() - messageReceivedTime
                val bundle = Bundle().apply {
                    putString("device_name", device.friendlyName)
                    putLong("time_spent_ms", timeSpent)
                    putString("method", "direct_click")
                }
                FirebaseAnalytics.getInstance(this).logEvent("shutter_response_time", bundle)
                
                sendMessageToWatch(MessageService.MESSAGE_TYPE_SHUTTER_SUCCESS, "Success!")
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
                        
                        // Calculate and log time spent
                        val timeSpent = System.currentTimeMillis() - messageReceivedTime
                        val bundle = Bundle().apply {
                            putString("device_name", device.friendlyName)
                            putLong("time_spent_ms", timeSpent)
                            putString("method", "child_click")
                        }
                        FirebaseAnalytics.getInstance(this).logEvent("shutter_response_time", bundle)
                        
                        sendMessageToWatch(MessageService.MESSAGE_TYPE_SHUTTER_SUCCESS, "Success w/ child click!")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during child button click", e)
        }
    }

    /**
     * Called when an accessibility event occurs.
     * This method is used to detect and interact with camera apps.
     * 
     * @param event The accessibility event that occurred
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
     * Called when the system wants to interrupt the feedback this service is providing.
     * This is part of the AccessibilityService interface but not used in this implementation.
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
     * Sends a message to the watch using the MessageService.
     * @param messageType The type of message to send
     * @param details Optional details about the message
     */
    private fun sendMessageToWatch(messageType: String, details: String? = null) {
        try {
            val intent = Intent(this, MessageService::class.java).apply {
                action = "SEND_MESSAGE"
                putExtra("message_type", messageType)
                putExtra("message_details", details)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to watch", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Called when the accessibility service is connected.
     * Sets up the message receiver and processes any pending messages.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected called")
        FirebaseCrashlytics.getInstance().log("Accessibility service connected")
        isServiceConnected = true

        // Initialize ConnectIQ
        try {
            device = connectIQ.connectedDevices?.firstOrNull() ?: run {
                Log.e(TAG, "No connected Garmin device found")
                FirebaseCrashlytics.getInstance().log("No connected Garmin device found")
                return
            }
            myApp = IQApp(CommConstants.COMM_WATCH_ID)
            Log.d(TAG, "ConnectIQ initialized with device: ${device.friendlyName}")
            FirebaseCrashlytics.getInstance().log("ConnectIQ initialized with device: ${device.friendlyName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ConnectIQ", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        
        // Process any message that arrived before the service was connected
        pendingMessage?.let { message ->
            Log.d(TAG, "Processing pending message: $message")
            handleCameraTrigger()
            pendingMessage = null
        }
    }
} 
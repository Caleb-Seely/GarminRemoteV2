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
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore

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
    private var device: IQDevice? = null
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
            deviceName = device?.friendlyName ?: "unknown_device",
            cameraPackage = currentPackage ?: "unknown",
            serviceState = if (isServiceConnected) "connected" else "disconnected"
        )

        // Get the current package name
        val packageName = rootInActiveWindow?.packageName?.toString() ?: run {
            FirebaseCrashlytics.getInstance().log("No active window package name found")
            return
        }
        
        // Try to get saved button location first
//        val savedButtonInfo = AccessibilityUtils.getLastKnownButtonInfo()
//        if (savedButtonInfo != null) {
//            Log.d(TAG, "Using saved button location")
//            Toast.makeText(this, "Using saved button location", Toast.LENGTH_SHORT).show()
//            FirebaseCrashlytics.getInstance().log("Using saved button location")
//            // Create a new AccessibilityNodeInfo for the saved location
//            val root = rootInActiveWindow
//            if (root != null) {
//                val node = AccessibilityUtils.findClickableNodeAtLocation(root, savedButtonInfo.bounds)
//                if (node != null) {
//                    triggerShutter(node)
//                    return
//                } else {
//                    Log.d(TAG, "Saved location no longer has clickable node, searching again")
//                    FirebaseCrashlytics.getInstance().log("Saved button location no longer valid, searching again")
//                }
//            }
//        }
            
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
     * Attempts to find the shutter button in the active camera app using enhanced detection.
     * This method uses multiple strategies in order of preference:
     * 1. Package-specific detection patterns
     * 2. Saved location from previous successful detections
     * 3. Content description matching
     * 4. Largest clickable node (fallback)
     * 5. Position-based detection
     * 
     * @return The AccessibilityNodeInfo of the shutter button if found, null otherwise
     */
    private fun findShutterButton(): AccessibilityNodeInfo? {
        Log.d(TAG, "Attempting enhanced shutter button detection")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow is null in findShutterButton")
            FirebaseCrashlytics.getInstance().log("rootInActiveWindow is null in findShutterButton")
            showToast("No active window for detection.")
            return null
        }

        val packageName = root.packageName?.toString() ?: run {
            Log.d(TAG, "No package name found in root")
            FirebaseCrashlytics.getInstance().log("No package name found in root")
            showToast("No package name found in root window.")
            return null
        }

        // Always collect and store all candidates for this app
        val candidates = AccessibilityUtils.getAllShutterButtonCandidates(root, packageName)
        CameraAppCandidateStore.updateCandidatesForApp(this, packageName, candidates)

        // Strategy 1: Check for a user-preferred button for this specific app
        val userPreferredButtonInfo = AccessibilityUtils.loadUserPreferredButton(this, packageName)
        if (userPreferredButtonInfo != null) {
            val preferredNode = AccessibilityUtils.findClickableNodeAtLocation(root, userPreferredButtonInfo.bounds)
            if (preferredNode != null) {
                Log.d(TAG, "Found user-preferred button for $packageName")
                showToast("Using your saved button for this app.")
                return preferredNode
            } else {
                Log.w(TAG, "User-preferred button not found on screen for $packageName. Searching all.")
                showToast("Your saved button isn't on screen. Searching again.")
            }
        }

        Log.d(TAG, "Detecting shutter button for package: $packageName")
        
        // Use the enhanced detection method
        val shutterButton = AccessibilityUtils.findShutterButtonEnhanced(root, packageName, TAG, this::showToast)
        
        if (shutterButton != null) {
            Log.d(TAG, "Enhanced detection found shutter button")
            FirebaseCrashlytics.getInstance().log("Enhanced detection found shutter button in package: $packageName")
            
            // Log button details for debugging
            val bounds = Rect()
            shutterButton.getBoundsInScreen(bounds)
            Log.d(TAG, """
                Shutter button details:
                Package: $packageName
                Resource ID: ${shutterButton.viewIdResourceName}
                Content Description: ${shutterButton.contentDescription}
                Class Name: ${shutterButton.className}
                Bounds: $bounds
                Clickable: ${shutterButton.isClickable}
            """.trimIndent())
            
            return shutterButton
        }

        Log.d(TAG, "Enhanced detection failed to find shutter button")
        FirebaseCrashlytics.getInstance().log("Enhanced detection failed to find shutter button in package: $packageName")
        showToast("No shutter button found.")
        
        // Fallback to original method for backward compatibility
        Log.d(TAG, "Falling back to original detection method")
        val fallbackButton = AccessibilityUtils.largestClickableNode(root, packageName, TAG)
        if (fallbackButton != null) {
            Log.d(TAG, "Fallback method found button")
            FirebaseCrashlytics.getInstance().log("Fallback method found shutter button")
            showToast("Fallback: Found largest clickable node.")
            return fallbackButton
        }

        Log.d(TAG, "No clickable nodes found on screen with any method")
        showToast("No clickable nodes found on screen.")
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

        val packageName = rootInActiveWindow?.packageName?.toString() ?: "unknown"

        // Try to perform a direct click first - this is often more reliable for actual buttons
        try {
            Log.d(TAG, "Attempting direct performAction CLICK on button")
            val clickStartTime = System.currentTimeMillis()
            val clickResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val clickTime = System.currentTimeMillis() - clickStartTime
            Log.d(TAG, "Direct button click result: $clickResult")

            if (clickResult) {
                Log.d(TAG, "Direct button click successful")
                FirebaseCrashlytics.getInstance().log("Shutter button clicked successfully")
                
                // Log successful click analytics
                AnalyticsUtils.logShutterButtonClick(
                    packageName = packageName,
                    buttonInfo = button,
                    clickMethod = "direct_click",
                    success = true,
                    responseTimeMs = clickTime,
                    deviceName = device?.friendlyName
                )
                
                // Calculate and log time spent
                val timeSpent = System.currentTimeMillis() - messageReceivedTime
                val deviceName = device?.friendlyName ?: "unknown_device" // Safe access

                val bundle = Bundle().apply {
                    putString("device_name", deviceName)
                    putLong("time_spent_ms", timeSpent)
                    putString("method", "direct_click")
                }
                FirebaseAnalytics.getInstance(this).logEvent("shutter_response_time", bundle)
                
                sendMessageToWatch(MessageService.MESSAGE_TYPE_SHUTTER_SUCCESS, "Success!")
                return
            } else {
                // Log failed direct click
                AnalyticsUtils.logShutterButtonClick(
                    packageName = packageName,
                    buttonInfo = button,
                    clickMethod = "direct_click",
                    success = false,
                    responseTimeMs = clickTime,
                    deviceName = device?.friendlyName
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during direct button click", e)
            // Log failed direct click due to exception
            AnalyticsUtils.logShutterButtonClick(
                packageName = packageName,
                buttonInfo = button,
                clickMethod = "direct_click_exception",
                success = false,
                deviceName = device?.friendlyName
            )
        }

        // Next try direct child button click if available
        try {
            for (i in 0 until button.childCount) {
                val child = button.getChild(i)
                if (child != null) {
                    Log.d(TAG, "Attempting click on child button $i")
                    val childClickStartTime = System.currentTimeMillis()
                    val childClickResult = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val childClickTime = System.currentTimeMillis() - childClickStartTime
                    Log.d(TAG, "Child button click result: $childClickResult")
                    
                    if (childClickResult) {
                        Log.d(TAG, "Child button click successful")
                        
                        // Log successful child click analytics
                        AnalyticsUtils.logShutterButtonClick(
                            packageName = packageName,
                            buttonInfo = child,
                            clickMethod = "child_click_$i",
                            success = true,
                            responseTimeMs = childClickTime,
                            deviceName = device?.friendlyName
                        )
                        
                        // Calculate and log time spent
                        val timeSpent = System.currentTimeMillis() - messageReceivedTime
                        val deviceName = device?.friendlyName ?: "unknown_device" // Safe access

                        val bundle = Bundle().apply {
                            putString("device_name", deviceName)
                            putLong("time_spent_ms", timeSpent)
                            putString("method", "child_click")
                        }
                        FirebaseAnalytics.getInstance(this).logEvent("shutter_response_time", bundle)
                        
                        sendMessageToWatch(MessageService.MESSAGE_TYPE_SHUTTER_SUCCESS, "Success w/ child click!")
                        return
                    } else {
                        // Log failed child click
                        AnalyticsUtils.logShutterButtonClick(
                            packageName = packageName,
                            buttonInfo = child,
                            clickMethod = "child_click_$i",
                            success = false,
                            responseTimeMs = childClickTime,
                            deviceName = device?.friendlyName
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during child button click", e)
            // Log failed child click due to exception
            AnalyticsUtils.logShutterButtonClick(
                packageName = packageName,
                buttonInfo = button,
                clickMethod = "child_click_exception",
                success = false,
                deviceName = device?.friendlyName
            )
        }

        // Log overall failure
        AnalyticsUtils.logShutterButtonClick(
            packageName = packageName,
            buttonInfo = button,
            clickMethod = "all_methods_failed",
            success = false,
            deviceName = device?.friendlyName
        )
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
            device = connectIQ.connectedDevices?.firstOrNull()
            if (device == null) {
                Log.e(TAG, "No connected Garmin device found")
                FirebaseCrashlytics.getInstance().log("No connected Garmin device found")
                // Don't return early - continue with service initialization
            } else {
                myApp = IQApp(CommConstants.COMM_WATCH_ID)
                Log.d(TAG, "ConnectIQ initialized with device: ${device!!.friendlyName}")
                FirebaseCrashlytics.getInstance().log("ConnectIQ initialized with device: ${device!!.friendlyName}")
            }
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 
package com.garmin.android.apps.camera.click.comm.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQApp
import com.garmin.android.apps.camera.click.comm.CommConstants
import com.garmin.android.apps.camera.click.comm.service.handlers.ShutterActionHandler
import com.garmin.android.apps.camera.click.comm.service.handlers.WatchCommunicationHandler
import com.garmin.android.apps.camera.click.comm.config.AppConfig
import com.garmin.android.apps.camera.click.comm.config.PreferencesKeys
import com.garmin.android.apps.camera.click.comm.repository.DevicePreferencesRepository
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import androidx.core.app.NotificationCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val TAG = "CameraAccessibilityService"

/**
 * Accessibility service that handles camera trigger functionality.
 * This service is responsible for:
 * - Receiving messages from the Garmin device
 * - Coordinating button detection and camera actions via ShutterActionHandler
 * - Communicating results back to the watch via WatchCommunicationHandler
 * - Managing the accessibility service lifecycle
 *
 * This service now uses handler classes for better separation of concerns.
 */
@AndroidEntryPoint
class CameraAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_MESSAGE_RECEIVED = "com.garmin.android.apps.camera.click.comm.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_MESSAGE_TIME = "message_time"
        const val FAILURE_RECOVERY_NOTIFICATION_ID = 1002
        const val PREF_LAST_TRIGGER_TIME = "last_trigger_time"
        const val PREF_LAST_TRIGGER_SUCCESS = "last_trigger_success"
        private const val PREFS_NAME = PreferencesKeys.Files.CAMERA_CLICK_PREFS
    }

    private fun persistTriggerResult(success: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_TRIGGER_TIME, System.currentTimeMillis())
            .putBoolean(PREF_LAST_TRIGGER_SUCCESS, success)
            .apply()
        if (success) {
            preferencesRepository.isSetupComplete = true
        }
    }

    // Injected handlers
    @Inject
    lateinit var shutterActionHandler: ShutterActionHandler

    @Inject
    lateinit var watchCommunicationHandler: WatchCommunicationHandler

    @Inject
    lateinit var preferencesRepository: DevicePreferencesRepository

    // Handler for posting delayed tasks to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())

    // Coroutine scope tied to service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Broadcast receiver for listening to incoming messages
    private lateinit var messageReceiver: BroadcastReceiver

    // Flag to track if the service is connected and ready
    private var isServiceConnected = false

    // Store messages received before the service is fully connected
    private var pendingMessage: String? = null

    // ConnectIQ instance for device info
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
        Log.d(TAG, "Service onCreate - using handlers for better separation of concerns")
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
                showToastSafely("Camera broadcast received")
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
     * Handles the camera trigger action by launching a coroutine.
     * This method is called from BroadcastReceiver, so it must be synchronous.
     */
    private fun handleCameraTrigger() {
        serviceScope.launch {
            try {
                withTimeout(12000L) {
                    handleCameraTriggerAsync()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Camera trigger timed out after 12 seconds")
                FirebaseCrashlytics.getInstance().log("Camera trigger timeout")
                watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "Timeout")
                showToastSafely("Camera trigger timed out")
            } catch (e: CancellationException) {
                Log.d(TAG, "Camera trigger cancelled")
                throw e // Re-throw to propagate cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in handleCameraTrigger", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "Failed")
                showToastSafely("Unexpected error - please try again")
            }
        }
    }

    /**
     * Async version of camera trigger handling.
     * This is where the actual work happens with non-blocking delays.
     */
    private suspend fun handleCameraTriggerAsync() {
        Log.d(TAG, "handleCameraTriggerAsync called")
        FirebaseCrashlytics.getInstance().log("Camera command received from watch")

        // Initialize Firebase Analytics
        try {
            AnalyticsUtils.initialize(this)
            Log.d(TAG, "Firebase Analytics initialized for camera command")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics", e)
            // Continue without analytics - not critical
        }

        val currentPackage = getCurrentApp()

        // Log camera command event
        try {
            AnalyticsUtils.logCameraCommand(
                deviceName = device?.friendlyName ?: "unknown_device",
                cameraPackage = currentPackage ?: "unknown",
                serviceState = if (isServiceConnected) "connected" else "disconnected"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log camera command analytics", e)
            // Continue without this specific analytics event
        }

        // Get the root accessibility node with async retry logic
        var root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No active window found on first attempt, retrying...")
            delay(100) // NON-BLOCKING delay
            root = rootInActiveWindow
        }

        if (root == null) {
            Log.e(TAG, "No active window found after retry")
            FirebaseCrashlytics.getInstance().log("No active window package name found")
            watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "Failed")
            return
        }

        val packageName = root.packageName?.toString()
        if (packageName == null) {
            Log.e(TAG, "No package name found")
            FirebaseCrashlytics.getInstance().log("No package name found in active window")
            watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "Failed")
            showToastSafely("No camera app detected")
            return
        }

        Log.d(TAG, "Camera app package: $packageName")

        // Get app name for better error messages
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }

        // Use ShutterActionHandler to find the button (with built-in async retry logic)
        val shutterButton = shutterActionHandler.findShutterButtonAsync(root, packageName, this@CameraAccessibilityService)

        if (shutterButton != null) {
            Log.d(TAG, "Found button, attempting to trigger")
            FirebaseCrashlytics.getInstance().log("Found shutter button in package: $packageName")

            // Use ShutterActionHandler to trigger the shutter
            val result = shutterActionHandler.triggerShutterAsync(
                button = shutterButton,
                packageName = packageName,
                deviceName = device?.friendlyName,
                context = this@CameraAccessibilityService,
                messageReceivedTime = messageReceivedTime
            )

            // Wait for photo to actually be saved before reporting success to watch
            if (result.success) {
                if (isVideoActionButton(shutterButton)) {
                    persistTriggerResult(true)
                    watchCommunicationHandler.sendSuccessMessageAsync(this@CameraAccessibilityService, "Success")
                    showToastSafely("✓ Video action")
                } else {
                    val clickTime = System.currentTimeMillis()
                    val photoConfirmed = waitForPhotoCapture(packageName)
                    val latencyMs = System.currentTimeMillis() - clickTime
                    AnalyticsUtils.logPhotoConfirmation(photoConfirmed, latencyMs, packageName)

                    persistTriggerResult(photoConfirmed)
                    if (photoConfirmed) {
                        watchCommunicationHandler.sendSuccessMessageAsync(this@CameraAccessibilityService, "Success")
                        showToastSafely("✓ Photo captured")
                    } else {
                        FirebaseCrashlytics.getInstance().log("Click succeeded but no photo detected for $packageName")
                        watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "no_photo_detected")
                        showToastSafely("✗ Photo not saved — try \"Configure Camera Button\"", Toast.LENGTH_LONG)
                        showFailureRecoveryNotification()
                    }
                }
            } else {
                watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "Failed")
                showToastSafely("✗ Button click failed in $appName", Toast.LENGTH_LONG)
                showFailureRecoveryNotification()
            }
        } else {
            Log.d(TAG, "Could not find button in active app")
            showToastSafely("No button found in $appName\nTry selecting manually", Toast.LENGTH_LONG)
            FirebaseCrashlytics.getInstance().log("No button found in package: $packageName")
            persistTriggerResult(false)
            watchCommunicationHandler.sendFailureMessageAsync(this@CameraAccessibilityService, "Failed")
            showFailureRecoveryNotification()
        }
    }

    /**
     * Gets the current foreground app package name.
     */
    private fun getCurrentApp(): String? {
        val root = rootInActiveWindow
        return root?.packageName?.toString()
    }

    /**
     * Called when an accessibility event occurs.
     * This method logs events for debugging purposes.
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

        // Cancel all pending coroutines
        serviceScope.cancel()

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
     * Observes MediaStore for a new image within the timeout window.
     * Samsung devices can delay MediaStore writes, so we use 8 seconds.
     * Apps like Snapchat/Instagram write to scoped storage rather than MediaStore,
     * so for those we skip MediaStore verification entirely and trust the button click.
     * No READ_MEDIA_IMAGES permission needed — ContentObserver only receives
     * URI change notifications, it does not read file content.
     */
    private val scopedStorageApps = setOf(
        "com.snapchat.android",
        "com.instagram.android"
    )

    private suspend fun waitForPhotoCapture(packageName: String, timeoutMs: Long = 8000L): Boolean {
        if (packageName in scopedStorageApps) {
            return true
        }
        val photoSaved = CompletableDeferred<Boolean>()
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                photoSaved.complete(true)
            }
        }
        return try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            withTimeoutOrNull(timeoutMs) { photoSaved.await() } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error observing MediaStore for $packageName", e)
            false
        } finally {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun isVideoActionButton(button: AccessibilityNodeInfo): Boolean {
        val desc = button.contentDescription?.toString()?.lowercase() ?: ""
        val resId = button.viewIdResourceName?.lowercase() ?: ""
        val videoTerms = listOf("record video", "start recording", "stop recording",
                                "start video", "stop video", "video record")
        return videoTerms.any { desc.contains(it) } ||
               resId.contains("record_button") || resId.contains("stop_button") ||
               resId.contains("video_record")
    }

    private fun showToastSafely(message: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            Toast.makeText(this, message, duration).show()
        } catch (e: Exception) {
            Log.e(TAG, "Toast failed", e)
        }
    }

    private fun showFailureRecoveryNotification() {
        try {
            val intent = Intent(this, com.garmin.android.apps.camera.click.comm.activities.ManualShutterButtonSelectionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, AppConfig.Notifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Camera didn't capture")
                .setContentText("Open the app to configure your camera button")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(FAILURE_RECOVERY_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show failure recovery notification", e)
        }
    }

    /**
     * Called when the accessibility service is connected.
     * Sets up the ConnectIQ connection and processes any pending messages.
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
}

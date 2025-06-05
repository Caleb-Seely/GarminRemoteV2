package com.garmin.android.apps.camera.click.comm.activities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.service.MessageService
import com.garmin.android.apps.camera.click.comm.utils.NotificationUtils
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.garmin.android.apps.camera.click.comm.utils.CameraUtils
import com.garmin.android.apps.camera.click.comm.CommConstants
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.graphics.Color
import android.provider.Settings
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

private const val TAG = "DeviceActivity"
private const val EXTRA_IQ_DEVICE = "IQDevice"
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
private const val PREFS_NAME = "CameraClickPrefs"
private const val KEY_AUTO_LAUNCH_CAMERA = "auto_launch_camera"

/**
 * Activity that handles communication with a specific Garmin device.
 * This activity allows users to interact with a selected device, send messages, and manage the companion app.
 * 
 * Key responsibilities:
 * - Manages device connection status and UI updates
 * - Handles opening and monitoring the companion app on the device
 * - Controls camera trigger functionality
 * - Manages background service for message handling
 * - Handles accessibility service requirements
 */
class DeviceActivity : Activity() {

    /**
     * Companion object containing static utility methods for the activity.
     */
    companion object {
        /**
         * Creates an intent to start the DeviceActivity with a specific device.
         * @param context The context to create the intent
         * @param device The Garmin device to communicate with
         * @return An intent configured to start the DeviceActivity
         */
        fun getIntent(context: Context, device: IQDevice?): Intent {
            val intent = Intent(context, DeviceActivity::class.java)
            intent.putExtra(EXTRA_IQ_DEVICE, device)
            return intent
        }
    }

    private var deviceStatusView: TextView? = null
    private var openAppButtonView: TextView? = null
    private var serviceToggleView: TextView? = null
    private var autoLaunchSwitch: Switch? = null
    private var isServiceRunning = false

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp
    private lateinit var prefs: SharedPreferences
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var appIsOpen = false

    /**
     * Listener for app open status changes that updates the UI accordingly.
     * This listener handles the response when attempting to open the companion app on the device.
     */
    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        Log.d(TAG, "App status changed: ${status.name}")
        Toast.makeText(applicationContext, "App Status: " + status.name, Toast.LENGTH_SHORT).show()

        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
            openAppButtonView?.setText(R.string.open_app_already_open)
        } else {
            appIsOpen = false
            openAppButtonView?.setText(R.string.prompt_watch_app)
        }
    }

    /**
     * Initializes the activity and sets up the UI components.
     * This method:
     * - Initializes Firebase Analytics
     * - Sets up device-specific UI elements
     * - Configures click listeners for various actions
     * - Checks and requests necessary permissions
     * 
     * @param savedInstanceState The saved instance state
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        // Initialize Firebase Analytics
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
        myApp = IQApp(CommConstants.COMM_WATCH_ID)
        appIsOpen = false

        // Log device activity screen view with device details
        val bundle = Bundle().apply {
            putString("device_name", device.friendlyName)
            putString("device_id", device.deviceIdentifier.toString())
            putString("connection_status", device.status?.name ?: "unknown")
        }
        AnalyticsUtils.logScreenView("device_activity", "DeviceActivity", bundle)

        val deviceNameView = findViewById<TextView>(R.id.devicename)
        deviceStatusView = findViewById(R.id.devicestatus)
        openAppButtonView = findViewById(R.id.openapp)
        autoLaunchSwitch = findViewById(R.id.auto_launch_switch)

        deviceNameView?.text = device.friendlyName
        deviceStatusView?.text = device.status?.name
        device.status?.let { updateDeviceStatusColor(it) }
        openAppButtonView?.setOnClickListener { openMyApp() }

        // Initialize auto-launch switch state
        autoLaunchSwitch?.isChecked = prefs.getBoolean(KEY_AUTO_LAUNCH_CAMERA, false)
        autoLaunchSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_LAUNCH_CAMERA, isChecked).apply()
            AnalyticsUtils.logFeatureUsage("auto_launch_camera", "toggle", isChecked)
        }

        // Add click listener for the tap to send test button
        findViewById<TextView>(R.id.taptosend)?.setOnClickListener {
            AnalyticsUtils.logFeatureUsage("test_message", "button_click", true)
            onItemClick("Test")
        }

        // Add click listener for the camera button
        findViewById<TextView>(R.id.camera_button)?.setOnClickListener {
            FirebaseCrashlytics.getInstance().log("Camera launch button clicked")
            AnalyticsUtils.logFeatureUsage("camera_launch", "button_click", true)
            CameraUtils.launchCamera(this)
        }

        // Check permissions and show dialogs if needed
        checkAndRequestPermissions()

        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
    }

    private fun checkAndRequestPermissions() {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                FirebaseCrashlytics.getInstance().log("Notification permission not granted")
                AnalyticsUtils.logPermissionState("POST_NOTIFICATIONS", false)
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                FirebaseCrashlytics.getInstance().log("Notification permission already granted")
                AnalyticsUtils.logPermissionState("POST_NOTIFICATIONS", true)
            }
        }

        // Check accessibility service
        if (!isAccessibilityServiceEnabled()) {
            FirebaseCrashlytics.getInstance().log("Accessibility service not enabled")
            AnalyticsUtils.logServiceState("accessibility", "disabled")
            showAccessibilityDialog()
        } else {
            FirebaseCrashlytics.getInstance().log("Accessibility service enabled")
            AnalyticsUtils.logServiceState("accessibility", "enabled")
        }
    }

    /**
     * Checks if the accessibility service is enabled for the app.
     * This is required for the camera trigger functionality to work properly.
     * 
     * @return true if the accessibility service is enabled, false otherwise
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        
        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.packageName == packageName &&
            service.resolveInfo.serviceInfo.name == "com.garmin.android.apps.camera.click.comm.service.CameraAccessibilityService"
        }
    }

    /**
     * Shows a dialog explaining why accessibility service is required and provides
     * a button to open the accessibility settings.
     */
    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_dialog_title)
            .setMessage(R.string.accessibility_dialog_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                FirebaseCrashlytics.getInstance().log("Opening accessibility settings")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(false)  // Make dialog non-dismissible
            .create()
            .show()
    }

    /**
     * Called when the activity resumes. Registers for device and app events.
     * This method ensures the UI stays in sync with the device state.
     */
    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity onResume")
        listenByDeviceEvents()
        getMyAppStatus()

    }

    /**
     * Called when the activity is paused. Unregisters event listeners.
     * Note: We don't unregister app events here as they're handled by the service.
     */
    public override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity onPause")

        try {
            connectIQ.unregisterForDeviceEvents(device)
        } catch (_: InvalidStateException) {
            Log.e(TAG, "Error unregistering for device events")
        }
    }

    /**
     * Called when the activity is destroyed. Stops the message service.
     * This ensures proper cleanup of resources when the activity is closed.
     */
    public override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy")
        stopService(MessageService.createIntent(this, device, CommConstants.COMM_WATCH_ID))
    }

    /**
     * Opens the companion app on the connected Garmin device.
     * This method:
     * - Attempts to open the app using ConnectIQ SDK
     * - Logs the attempt for analytics
     * - Shows appropriate UI feedback
     */
    private fun openMyApp() {
        Log.d(TAG, "Opening app on device")
        Toast.makeText(this, "Prompting watch...", Toast.LENGTH_SHORT).show()

        // Log the app open attempt
        AnalyticsUtils.logWatchAppOpen(
            deviceName = device.friendlyName,
            deviceId = device.deviceIdentifier.toString(),
            status = device.status?.name ?: "unknown"
        )

        try {
            connectIQ.openApplication(device, myApp, openAppListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app", e)
            // Log the error
            AnalyticsUtils.logWatchAppOpen(
                deviceName = device.friendlyName,
                deviceId = device.deviceIdentifier.toString(),
                status = "error: ${e.message}"
            )
        }
    }

    /**
     * Registers for device status updates and updates the UI accordingly.
     * This method ensures the UI reflects the current connection state of the device.
     */
    private fun listenByDeviceEvents() {
        Log.d(TAG, "Registering for device events")
        try {
            connectIQ.registerForDeviceEvents(device) { _, status ->
                Log.d(TAG, "Device status changed: ${status.name}")
                deviceStatusView?.text = status.name
                updateDeviceStatusColor(status)
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error registering for device events", e)
        }
    }

    /**
     * Checks if the companion app is installed on the device and starts the message service.
     * This method:
     * - Queries the ConnectIQ SDK for app installation status
     * - Starts the MessageService if the app is installed
     * - Shows a dialog if the app is not installed
     */
    private fun getMyAppStatus() {
        Log.d(TAG, "Checking app status")
        try {
            connectIQ.getApplicationInfo(CommConstants.COMM_WATCH_ID, device, object :
                ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    Log.d(TAG, "App is installed, starting message service")
                    // Start the message service when the app is confirmed to be installed
                    startService(MessageService.createIntent(this@DeviceActivity, device, CommConstants.COMM_WATCH_ID))
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    Log.d(TAG, "Garmin ConnectIQ App is not installed")
                    AlertDialog.Builder(this@DeviceActivity)
                        .setTitle(R.string.missing_widget)
                        .setMessage(R.string.missing_widget_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show()
                }
            })
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error getting app info", e)
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Service unavailable", e)
        }
    }

    /**
     * Handles message selection and sends the selected message to the device.
     * @param message The message payload to send
     */
    private fun onItemClick(message: String) {
        Log.d(TAG, "Sending message: $message")
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                Log.d(TAG, "Message send status: ${status.name}")
                Toast.makeText(this@DeviceActivity, status.name, Toast.LENGTH_SHORT).show()
                
                // Log camera command event
                val params = Bundle().apply {
                    putString("device_name", device.friendlyName)
                    putString("message", message)
                }
                firebaseAnalytics.logEvent("message_sent_to_watch", params)
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error sending message", e)
            Toast.makeText(this, "ConnectIQ is not in a valid state", Toast.LENGTH_SHORT).show()
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Service unavailable", e)
            Toast.makeText(
                this,
                "ConnectIQ service is unavailable. Is Garmin Connect Mobile installed and running?",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private fun updateDeviceStatusColor(status: IQDevice.IQDeviceStatus) {
        deviceStatusView?.setTextColor(
            when (status) {
                IQDevice.IQDeviceStatus.CONNECTED -> ContextCompat.getColor(this, R.color.primary_dark)
                IQDevice.IQDeviceStatus.NOT_CONNECTED -> ContextCompat.getColor(this, R.color.error)
                else -> ContextCompat.getColor(this, R.color.warning) // For UNKNOWN and other states
            }
        )
    }
}
package com.garmin.android.apps.camera.click.comm.activities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.garmin.android.apps.camera.click.comm.utils.AccessibilityUtils
import com.garmin.android.apps.camera.click.comm.views.ButtonLocationOverlay
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils
import android.widget.Button
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager
import com.garmin.android.apps.camera.click.comm.repository.DeveloperNoteContentRepository
import com.garmin.android.apps.camera.click.comm.dialogs.DeveloperNoteDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.View
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.widget.SwitchCompat

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
class DeviceActivity : AppCompatActivity() {

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
    private var deviceStatusBadge: com.google.android.material.card.MaterialCardView? = null
    private var openAppButtonView: com.google.android.material.card.MaterialCardView? = null
    private var openAppTextView: TextView? = null
    private var serviceToggleView: TextView? = null
    private var autoLaunchSwitch: androidx.appcompat.widget.SwitchCompat? = null
    private var isServiceRunning = false

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private var device: IQDevice? = null
    private lateinit var myApp: IQApp
    private lateinit var prefs: SharedPreferences
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var buttonLocationOverlay: ButtonLocationOverlay

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
            openAppTextView?.setText(R.string.open_app_already_open)
        } else {
            appIsOpen = false
            openAppTextView?.setText(R.string.prompt_watch_app)
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

        // Set status bar color to black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.BLACK
        }

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize Firebase Analytics
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val deviceExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IQ_DEVICE, IQDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_IQ_DEVICE) as? IQDevice
        }
        if (deviceExtra == null) {
            Log.e(TAG, "Device extra missing or invalid in intent. Finishing activity.")
            Toast.makeText(this, R.string.device_missing_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        device = deviceExtra
        myApp = IQApp(CommConstants.COMM_WATCH_ID)
        appIsOpen = false

        // Initialize button location overlay
        buttonLocationOverlay = findViewById(R.id.button_location_overlay)
        updateButtonLocationOverlay()

        // Log device activity screen view with device details
        val bundle = Bundle().apply {
            putString("device_name", device?.friendlyName ?: "unknown")
            putString("device_id", device?.deviceIdentifier?.toString() ?: "unknown")
            putString("connection_status", device?.status?.name ?: "unknown")
        }
        AnalyticsUtils.logScreenView("device_activity", "DeviceActivity", bundle)

        val deviceNameView = findViewById<TextView>(R.id.devicename)
        deviceStatusView = findViewById(R.id.devicestatus)
        deviceStatusBadge = deviceStatusView?.parent as? com.google.android.material.card.MaterialCardView
        openAppButtonView = findViewById(R.id.openapp)
        openAppTextView = findViewById<TextView>(R.id.openapp_text)
        autoLaunchSwitch = findViewById(R.id.auto_launch_switch)

        deviceNameView?.text = device?.friendlyName ?: "Unknown Device"
        deviceStatusView?.text = device?.status?.name ?: "Unknown Status"
        device?.status?.let { updateDeviceStatusColor(it) }
        
        // Premium open app button animation
        openAppButtonView?.setOnClickListener { 
            val pressAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_press)
            val releaseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_release)
            
            it.startAnimation(pressAnimation)
            pressAnimation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    it.startAnimation(releaseAnimation)
                    openMyApp()
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        }

        // Initialize auto-launch switch state
        autoLaunchSwitch?.isChecked = prefs.getBoolean(KEY_AUTO_LAUNCH_CAMERA, false)
        autoLaunchSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_LAUNCH_CAMERA, isChecked).apply()
            AnalyticsUtils.logFeatureUsage("auto_launch_camera", "toggle", isChecked)
        }

        // Add click listener for the tap to send test button
        findViewById<MaterialCardView>(R.id.taptosend)?.setOnClickListener {
            val pressAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_press)
            val releaseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_release)
            
            it.startAnimation(pressAnimation)
            pressAnimation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    it.startAnimation(releaseAnimation)
                    AnalyticsUtils.logFeatureUsage("test_message", "premium_card_click", true)
                    onItemClick("Test")
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        }

        // Add click listener for the premium camera button
        findViewById<LinearLayout>(R.id.camera_button)?.setOnClickListener {
            // Launch camera immediately
            FirebaseCrashlytics.getInstance().log("Premium camera launch button clicked")
            AnalyticsUtils.logFeatureUsage("camera_launch", "premium_button_click", true)
            CameraUtils.launchCamera(this@DeviceActivity)
            
            // Optional: Add animations that don't block the camera launch
            try {
                val pressAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_press)
                val releaseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_release)
                
                it.startAnimation(pressAnimation)
                pressAnimation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                    override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                    override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                        it.startAnimation(releaseAnimation)
                    }
                    override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                })
            } catch (e: Exception) {
                // If animations fail, don't let it affect camera launch
                Log.w(TAG, "Animation failed but camera should still launch", e)
            }
        }

        // Add click listener for the square camera button
        findViewById<View>(R.id.square_camera_button)?.setOnClickListener {
            FirebaseCrashlytics.getInstance().log("Square camera launch button clicked")
            AnalyticsUtils.logFeatureUsage("camera_launch", "square_button_click", true)
            CameraUtils.launchCamera(this)
        }

        // Premium manual shutter selection button
        val manualButton = findViewById<MaterialCardView>(R.id.manual_shutter_selection_button)
        manualButton.setOnClickListener {
            // Premium card animation
            val pressAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_press)
            val releaseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.premium_button_release)
            
            it.startAnimation(pressAnimation)
            pressAnimation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    it.startAnimation(releaseAnimation)
                    
                    if (CameraAppCandidateStore.candidatesByApp.isEmpty()) {
                        Toast.makeText(this@DeviceActivity, R.string.no_camera_apps_detected_yet, Toast.LENGTH_LONG).show()
                    } else {
                        startActivity(Intent(this@DeviceActivity, ManualShutterButtonSelectionActivity::class.java))
                    }
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        }

        // Debug: Log available content versions
        logAvailableContentVersions()

        // Check permissions and show dialogs if needed
        checkAndRequestPermissions()

        // Add premium enter animations for cards
        addPremiumEnterAnimations()

    // Timing checks are triggered after app verification (onApplicationInfoReceived)

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
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_dialog_title)
            .setMessage(R.string.accessibility_dialog_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                FirebaseCrashlytics.getInstance().log("Opening accessibility settings")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(false)  // Make dialog non-dismissible
            .create()
        
        dialog.show()
        
        // Apply custom styling to the buttons after the dialog is shown
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(ContextCompat.getColor(this@DeviceActivity, R.color.primary_color))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(this@DeviceActivity, R.color.text_secondary))
        }
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

        device?.let { dev ->
            try {
                connectIQ.unregisterForDeviceEvents(dev)
            } catch (_: InvalidStateException) {
                Log.e(TAG, "Error unregistering for device events")
            }
        }
    }

    /**
     * Called when the activity is destroyed. Stops the message service.
     * This ensures proper cleanup of resources when the activity is closed.
     */
    public override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy")
        device?.let {
            stopService(MessageService.createIntent(this, it, CommConstants.COMM_WATCH_ID))
        }
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
        device?.let { dev ->
            AnalyticsUtils.logWatchAppOpen(
                deviceName = dev.friendlyName,
                deviceId = dev.deviceIdentifier.toString(),
                status = dev.status?.name ?: "unknown"
            )
        }

        device?.let { dev ->
            try {
                connectIQ.openApplication(dev, myApp, openAppListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening app", e)
                // Log the error
                AnalyticsUtils.logWatchAppOpen(
                    deviceName = dev.friendlyName,
                    deviceId = dev.deviceIdentifier.toString(),
                    status = "error: ${e.message}"
                )
            }
        }
    }

    /**
     * Registers for device status updates and updates the UI accordingly.
     * This method ensures the UI reflects the current connection state of the device.
     */
    private fun listenByDeviceEvents() {
        Log.d(TAG, "Registering for device events")
        device?.let { dev ->
            try {
                connectIQ.registerForDeviceEvents(dev) { _, status ->
                    Log.d(TAG, "Device status changed: ${status.name}")
                    deviceStatusView?.text = status.name
                    updateDeviceStatusColor(status)
                }
            } catch (e: InvalidStateException) {
                Log.e(TAG, "Error registering for device events", e)
            }
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
        device?.let { dev ->
            try {
                connectIQ.getApplicationInfo(CommConstants.COMM_WATCH_ID, dev, object :
                    ConnectIQ.IQApplicationInfoListener {
                    override fun onApplicationInfoReceived(app: IQApp) {
                        Log.d(TAG, "App is installed, starting message service")
                        // Start the message service when the app is confirmed to be installed
                        startService(MessageService.createIntent(this@DeviceActivity, dev, CommConstants.COMM_WATCH_ID))

                        // Record an app launch for review timing and then run the full timing checks
                        InAppReviewUtils.trackAppLaunch(this@DeviceActivity)
                        
                        // Invalidate options menu in case rate app button should now be visible
                        invalidateOptionsMenu()

                        // Run the comprehensive priority-based timing checks now that verification succeeded
                        checkTimingTriggersWithPriority()
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
    }

    /**
     * Handles message selection and sends the selected message to the device.
     * @param message The message payload to send
     */
    private fun onItemClick(message: String) {
        Log.d(TAG, "Sending message: $message")
        device?.let { dev ->
            try {
                connectIQ.sendMessage(dev, myApp, message) { _, _, status ->
                    Log.d(TAG, "Message send status: ${status.name}")
                    
                    // Success feedback
                    runOnUiThread {
                        if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                            // Success toast without animation
                            Toast.makeText(this@DeviceActivity, "✓ Message sent successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@DeviceActivity, "❌ ${status.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Log camera command event
                    val params = Bundle().apply {
                        putString("device_name", dev.friendlyName)
                        putString("message", message)
                        putString("status", status.name)
                    }
                    firebaseAnalytics.logEvent("message_sent_to_watch", params)
                }
            } catch (e: InvalidStateException) {
                Log.e(TAG, "Error sending message", e)
                Toast.makeText(this, "❌ ConnectIQ is not in a valid state", Toast.LENGTH_SHORT).show()
            } catch (e: ServiceUnavailableException) {
                Log.e(TAG, "Service unavailable", e)
                Toast.makeText(
                    this,
                    "❌ ConnectIQ service is unavailable. Is Garmin Connect Mobile installed and running?",
                    Toast.LENGTH_LONG
                ).show()
            }
        } ?: run {
            Log.e(TAG, "Device is null, cannot send message")
            Toast.makeText(this, "❌ Device not available", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateDeviceStatusColor(status: IQDevice.IQDeviceStatus) {
        // Always keep text white for good contrast
        deviceStatusView?.setTextColor(ContextCompat.getColor(this, R.color.white))
        
        // Update badge background color based on connection status
        deviceStatusBadge?.setCardBackgroundColor(
            when (status) {
                IQDevice.IQDeviceStatus.CONNECTED -> ContextCompat.getColor(this, R.color.success)
                IQDevice.IQDeviceStatus.NOT_CONNECTED -> ContextCompat.getColor(this, R.color.error)
                else -> ContextCompat.getColor(this, R.color.warning) // For UNKNOWN and other states
            }
        )
    }

    private fun updateButtonLocationOverlay() {
        val buttonInfo = AccessibilityUtils.getLastKnownButtonInfo()
        buttonLocationOverlay.setButtonInfo(buttonInfo)
    }

        /**
     * Creates the options menu for this activity.
     * @param menu The options menu in which items are placed
     * @return true to display the menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.device, menu)
        
        // Only show the "Rate App" menu item after 5 days
        val rateAppItem = menu.findItem(R.id.rate_app)
        val daysSinceInstall = InAppReviewUtils.getDaysSinceFirstInstall(this)
        val shouldShowRateApp = daysSinceInstall >= 5
        
        rateAppItem?.isVisible = shouldShowRateApp
        
        Log.d(TAG, "Rate app menu item visibility: $shouldShowRateApp (days since install: $daysSinceInstall)")
        
        // Track when rate app button becomes available for the first time
        if (shouldShowRateApp && daysSinceInstall < 10) { // Only log for first few days it's available
            AnalyticsUtils.logFeatureUsage("rate_app", "button_available", true)
        }
        
        return true
    }

    /**
     * Handles menu item selection.
     * @param item The selected menu item
     * @return true if the event was handled
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.help -> {
                AnalyticsUtils.logFeatureUsage("help", "menu_click", true)
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.rate_app -> {
                openAppStoreForRating()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Opens the app store listing for rating the app.
     * Tries Google Play Store first, then falls back to web browser.
     */
    private fun openAppStoreForRating() {
        try {
            AnalyticsUtils.logFeatureUsage("rate_app", "menu_click", true)
            
            // Try to open Google Play Store app
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            startActivity(playStoreIntent)
            
            Log.d(TAG, "Opened Play Store app for rating")
            AnalyticsUtils.logFeatureUsage("rate_app", "play_store_opened", true)
            
            // Show appreciation message
            Toast.makeText(this, "Thanks for taking the time to rate CameraClick! 📸⭐", Toast.LENGTH_SHORT).show()
            
        } catch (e: ActivityNotFoundException) {
            // Fallback to web browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                startActivity(webIntent)
                
                Log.d(TAG, "Opened Play Store web page for rating")
                AnalyticsUtils.logFeatureUsage("rate_app", "web_store_opened", true)
                
                // Show appreciation message
                Toast.makeText(this, "Thanks for taking the time to rate CameraClick! 📸⭐", Toast.LENGTH_SHORT).show()
                
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open app store for rating", e2)
                AnalyticsUtils.logError("rate_app", "store_open_failed", e2.message ?: "unknown")
                Toast.makeText(this, "Unable to open app store", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Checks if we should show the review prompt and displays it if appropriate.
     * This is called at natural moments in the app flow when the user has
     * successfully completed an action (like connecting to a device).
     */
    private fun checkAndShowReviewPrompt() {
        if (InAppReviewUtils.shouldShowReviewPrompt(this)) {
            Log.d(TAG, "Showing in-app review prompt")
            AnalyticsUtils.logFeatureUsage("in_app_review", "prompt_shown", true)
            
            InAppReviewUtils.launchInAppReview(this) { success ->
                Log.d(TAG, "In-app review completed: $success")
                AnalyticsUtils.logFeatureUsage("in_app_review", "completed", success)
            }
        }
    }

    /**
     * Debug method to log all available content versions
     */
    private fun logAvailableContentVersions() {
        try {
            Log.d(TAG, "=== Developer Note Content Debug ===")
            Log.d(TAG, "Total content count: ${DeveloperNoteContentRepository.getContentCount()}")
            
            for (i in 0..4) {  // Check versions 0-4 to see what's available
                val content = DeveloperNoteContentRepository.getContentForVersion(this, i)
                if (content != null) {
                    Log.d(TAG, "Version $i: '${content.title}' - '${content.message.take(50)}...'")
                } else {
                    Log.d(TAG, "Version $i: NOT AVAILABLE")
                }
            }
            
            val currentVersion = DeveloperNoteManager.getCurrentVersionIndex(this)
            Log.d(TAG, "Current version index: $currentVersion")
            Log.d(TAG, "=== End Debug ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging content versions", e)
        }
    }
    
    /**
     * Shows the developer note popup for testing purposes, bypassing timing conditions.
     * This method cycles through all 4 versions (0, 1, 2, 3) and then resets to 0.
     */
    private fun showDeveloperNoteForTesting() {
        try {
            // Get the current version or start from 0 if cycle is complete
            var currentVersion = DeveloperNoteManager.getCurrentVersionIndex(this)
            
            Log.d(TAG, "Test button clicked - current version index: $currentVersion")
            
            // If cycle is complete (-1) or invalid, reset to 0
            if (currentVersion == -1 || currentVersion > 3) {
                currentVersion = 0
                DeveloperNoteManager.resetPopupState(this)
                Log.d(TAG, "Reset to version 0 for testing")
            }
            
            val content = DeveloperNoteContentRepository.getContentForVersion(this, currentVersion)
            
            if (content != null) {
                Log.d(TAG, "Showing test developer note popup - version $currentVersion")
                Log.d(TAG, "Content title: ${content.title}")
                Log.d(TAG, "Content message preview: ${content.message.take(50)}...")
                
                // Show toast to indicate which version is being shown
                Toast.makeText(this, "Showing version $currentVersion of 4", Toast.LENGTH_SHORT).show()
                
                // Create and show the dialog fragment
                val dialog = DeveloperNoteDialogFragment.newInstance(content)
                
                try {
                    // Use a special tag to indicate this is a test popup
                    dialog.show(supportFragmentManager, "developer_note_test")
                    
                    // Log test popup display
                    val params = android.os.Bundle().apply {
                        putInt("version", currentVersion)
                        putBoolean("is_test", true)
                        putString("content_preview", content.message.take(50))
                        putString("timestamp", System.currentTimeMillis().toString())
                    }
                    AnalyticsUtils.logEvent("developer_note_test_popup_shown", params)
                    
                    FirebaseCrashlytics.getInstance().log("Developer note test popup shown - version $currentVersion")
                    
                    // Advance to next version for next test
                    // But handle the cycling manually for testing to ensure we see all versions
                    val nextVersion = if (currentVersion >= 3) {
                        // After showing version 3, reset to 0 for next test
                        0
                    } else {
                        currentVersion + 1
                    }
                    
                    // Set the next version directly for testing
                    val prefs = getSharedPreferences("developer_note_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("developer_note_popup_version_index", nextVersion).apply()
                    
                    Log.d(TAG, "Advanced to next version: $nextVersion")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing test developer note dialog", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                    AnalyticsUtils.logError("developer_note", "test_dialog_show_failed", e.message ?: "unknown")
                    Toast.makeText(this, "Error showing test popup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                
            } else {
                Log.w(TAG, "No content found for test developer note version $currentVersion")
                Toast.makeText(this, "No content available for version $currentVersion", Toast.LENGTH_SHORT).show()
                
                // Reset and try version 0
                DeveloperNoteManager.resetPopupState(this)
                Log.d(TAG, "Reset popup state and trying version 0")
                
                val resetContent = DeveloperNoteContentRepository.getContentForVersion(this, 0)
                if (resetContent != null) {
                    val dialog = DeveloperNoteDialogFragment.newInstance(resetContent)
                    dialog.show(supportFragmentManager, "developer_note_test")
                    
                    // Set to version 1 for next test
                    val prefs = getSharedPreferences("developer_note_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("developer_note_popup_version_index", 1).apply()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in showDeveloperNoteForTesting", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "test_popup_failed", e.message ?: "unknown")
            Toast.makeText(this, "Error showing test popup: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks timing triggers with priority system to avoid overwhelming users.
     * Priority: Review prompt first, then developer note on next app launch.
     */
    private fun checkTimingTriggersWithPriority() {
        try {
            // Check if review prompt should be shown (higher priority)
            val shouldShowReview = InAppReviewUtils.shouldShowReviewPrompt(this)
            val shouldShowDeveloperNote = DeveloperNoteManager.shouldShowPopup(this)
            
            Log.d(TAG, "Timing check - Review: $shouldShowReview, Developer Note: $shouldShowDeveloperNote")
            
            if (shouldShowReview) {
                // Show review prompt first (higher priority)
                Log.d(TAG, "Showing review prompt (priority over developer note)")
                showReviewPrompt()
                
                // If developer note should also show, defer it to next launch
                if (shouldShowDeveloperNote) {
                    deferDeveloperNoteToNextLaunch()
                }
            } else if (shouldShowDeveloperNote) {
                // Only show developer note if review isn't showing
                Log.d(TAG, "Showing developer note popup")
                showDeveloperNotePopup()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking timing triggers", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Defers the developer note to the next app launch by adjusting its timing.
     * This prevents overwhelming users with multiple popups in one session.
     */
    private fun deferDeveloperNoteToNextLaunch() {
        try {
            Log.d(TAG, "Deferring developer note to next app launch to avoid overwhelming user")

            com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.deferToNextLaunch(this)

            // Log analytics for deferred popup
            val params = Bundle().apply {
                putString("reason", "review_prompt_priority")
                putString("deferred_to", "next_launch")
                putString("location", "device_activity")
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_deferred", params)

            FirebaseCrashlytics.getInstance().log("Developer note deferred to next launch due to review prompt priority")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deferring developer note", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
    
    /**
     * Shows the developer note popup automatically based on timing conditions.
     * This advances the version cycle and is triggered by timing, not user action.
     */
    private fun showDeveloperNotePopup() {
        try {
            val currentVersion = DeveloperNoteManager.getCurrentVersionIndex(this)
            val content = DeveloperNoteContentRepository.getContentForVersion(this, currentVersion)
            
            if (content != null) {
                Log.d(TAG, "Showing automatic developer note popup - version $currentVersion")
                
                // Create and show the dialog fragment
                val dialog = DeveloperNoteDialogFragment.newInstance(content)
                dialog.show(supportFragmentManager, "developer_note_auto")
                
                // The dialog fragment records popup shown; avoid double-recording here
                
                // Log analytics for automatic popup
                val params = Bundle().apply {
                    putInt("version", currentVersion)
                    putBoolean("automatic_trigger", true)
                    putBoolean("from_device_activity", true)
                    putString("location", "device_activity")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_auto_popup_shown", params)
                
                FirebaseCrashlytics.getInstance().log("Developer note popup shown automatically on device page - version $currentVersion")
                
            } else {
                Log.w(TAG, "No content found for developer note version $currentVersion")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing automatic developer note popup", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "auto_popup_failed", e.message ?: "unknown")
        }
    }
    
    /**
     * Shows the Google Play in-app review prompt on the device page.
     */
    private fun showReviewPrompt() {
        try {
            Log.d(TAG, "Showing in-app review prompt on device page")
            AnalyticsUtils.logFeatureUsage("in_app_review", "prompt_shown", true)
            
            InAppReviewUtils.launchInAppReview(this) { success ->
                Log.d(TAG, "Review prompt completed: $success")
                
                // Log analytics for review completion
                val params = Bundle().apply {
                    putBoolean("success", success)
                    putBoolean("from_device_activity", true)
                    putString("location", "device_activity")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("in_app_review_completed", params)
                
                FirebaseCrashlytics.getInstance().log("In-app review prompt completed on device page - success: $success")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing review prompt", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("in_app_review", "prompt_failed", e.message ?: "unknown")
        }
    }

    /**
     * Adds premium enter animations to make the interface feel more polished and engaging.
     */
    private fun addPremiumEnterAnimations() {
        try {
            Log.d(TAG, "Initializing premium animations")
            
            // Ensure all views are visible first
            findViewById<LinearLayout>(R.id.device_info_section)?.alpha = 1f
            findViewById<MaterialCardView>(R.id.main_card)?.alpha = 1f
            findViewById<MaterialCardView>(R.id.auto_launch_section)?.alpha = 1f
            findViewById<LinearLayout>(R.id.camera_button)?.alpha = 1f
            
            // Optional: Add subtle animations later if desired
            // For now, just ensure everything is visible
            Log.d(TAG, "All views set to visible")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up animations", e)
            // Fallback: make sure all views are visible
            findViewById<LinearLayout>(R.id.device_info_section)?.alpha = 1f
            findViewById<MaterialCardView>(R.id.main_card)?.alpha = 1f
            findViewById<LinearLayout>(R.id.auto_launch_section)?.alpha = 1f
            findViewById<LinearLayout>(R.id.camera_button)?.alpha = 1f
        }
    }
}
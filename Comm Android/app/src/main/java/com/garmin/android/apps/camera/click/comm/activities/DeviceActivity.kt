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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
import com.garmin.android.apps.camera.click.comm.utils.logPermissionStateWithCrashlytics
import com.garmin.android.apps.camera.click.comm.utils.logServiceStateWithCrashlytics
import com.garmin.android.apps.camera.click.comm.utils.logFeatureUsageWithCrashlytics
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.garmin.android.apps.camera.click.comm.viewmodels.DeviceActivityViewModel
import com.garmin.android.apps.camera.click.comm.helpers.DeviceActivityViews
import com.garmin.android.apps.camera.click.comm.helpers.DeviceActivityAnimationHelper
import com.garmin.android.apps.camera.click.comm.helpers.DeviceActivityDialogManager
import com.garmin.android.apps.camera.click.comm.repository.DevicePreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "DeviceActivity"
private const val EXTRA_IQ_DEVICE = "IQDevice"
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
private const val PREFS_NAME = "CameraClickPrefs"
private const val KEY_AUTO_LAUNCH_CAMERA = "auto_launch_camera"

/**
 * Activity that handles communication with a specific Garmin device.
 * This activity uses MVVM architecture with DeviceActivityViewModel.
 *
 * Key responsibilities:
 * - UI binding and rendering
 * - Observing ViewModel state changes
 * - Handling UI interactions and delegating to ViewModel
 * - Managing Android-specific UI components (dialogs, animations)
 * - Permission request handling
 */
@AndroidEntryPoint
class DeviceActivity : AppCompatActivity() {

    @Inject lateinit var preferencesRepository: DevicePreferencesRepository

    // ViewModel
    private val viewModel: DeviceActivityViewModel by viewModels()

    // Helper classes
    private lateinit var views: DeviceActivityViews
    private lateinit var animationHelper: DeviceActivityAnimationHelper
    private lateinit var dialogManager: DeviceActivityDialogManager

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

    // Device reference (passed from intent)
    private var device: IQDevice? = null

    /**
     * Initializes the activity and sets up the UI components.
     * This method:
     * - Initializes the ViewModel with the device
     * - Sets up LiveData observers
     * - Configures UI views and click listeners
     * - Checks and requests necessary permissions
     *
     * @param savedInstanceState The saved instance state
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        // Set status bar color to black (modern approach)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.BLACK

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Get device from intent
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

        // Initialize ViewModel with device
        viewModel.initializeWithDevice(deviceExtra)

        // Initialize helper classes
        views = DeviceActivityViews.bind(this)
        animationHelper = DeviceActivityAnimationHelper(this)
        dialogManager = DeviceActivityDialogManager(this)

        // Setup LiveData observers
        setupObservers()

        // Setup UI views
        setupViews()

        // Setup click listeners
        setupClickListeners()

        // Check permissions and show dialogs if needed
        checkAndRequestPermissions()

        // Add premium enter animations for cards
        animationHelper.setupEnterAnimations(views)

        // Enable Firebase Analytics
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
    }

    /**
     * Setup LiveData observers for ViewModel state changes
     */
    private fun setupObservers() {
        // Observe device status changes
        viewModel.deviceStatus.observe(this) { status ->
            views.deviceStatusView.text = status.name
            updateDeviceStatusColor(status)
        }

        // Observe app status changes
        viewModel.appStatus.observe(this) { appStatus ->
            views.openAppTextView.text = if (appStatus.isOpen) {
                getString(R.string.open_app_already_open)
            } else {
                getString(R.string.prompt_watch_app)
            }
        }

        // Observe auto-launch preference
        viewModel.autoLaunchEnabled.observe(this) { enabled ->
            views.autoLaunchSwitch.isChecked = enabled
        }

        // Observe message send results
        viewModel.messageSendResult.observe(this) { result ->
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        }

        // Observe toast messages
        viewModel.toastMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Observe accessibility dialog trigger
        viewModel.showAccessibilityDialog.observe(this) { shouldShow ->
            if (shouldShow) {
                dialogManager.showAccessibilityDialog()
            }
        }

        // Observe app not installed dialog trigger
        viewModel.showAppNotInstalledDialog.observe(this) { shouldShow ->
            if (shouldShow) {
                dialogManager.showAppNotInstalledDialog()
                viewModel.onAppNotInstalledDialogShown()
            }
        }

        // Observe review prompt trigger
        viewModel.showReviewPrompt.observe(this) { shouldShow ->
            if (shouldShow) {
                dialogManager.checkAndShowReviewPrompt()
                viewModel.onReviewPromptShown()
            }
        }

        // Observe developer note trigger
        viewModel.showDeveloperNote.observe(this) { event ->
            event?.let {
                dialogManager.showDeveloperNotePopup(it.version, it.isAutomatic)
                viewModel.onDeveloperNoteShown()
            }
        }

        // Observe rate app menu visibility
        viewModel.shouldShowRateAppMenu.observe(this) { shouldShow ->
            invalidateOptionsMenu()
        }

        // Observe button location info
        viewModel.buttonLocationInfo.observe(this) { buttonInfo ->
            views.buttonLocationOverlay.setButtonInfo(buttonInfo)
        }

        // Observe MessageService start trigger
        viewModel.startMessageService.observe(this) { device ->
            startMessageServiceIfPermitted(device)
        }
    }

    /**
     * Setup UI views and initial states
     */
    private fun setupViews() {
        views.deviceNameView.text = device?.friendlyName ?: "Unknown Device"
    }

    /**
     * Setup click listeners for all interactive UI elements
     */
    private fun setupClickListeners() {
        // Setup-incomplete banner configure button
        views.btnBannerConfigure?.setOnClickListener {
            startActivity(Intent(this, ManualShutterButtonSelectionActivity::class.java))
        }

        // Open app button with animation
        views.openAppButtonView.setOnClickListener {
            animationHelper.animateOpenAppButton(it) {
                viewModel.openWatchApp()
            }
        }

        // Auto-launch switch
        views.autoLaunchSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoLaunchCamera(isChecked)
        }

        // Test message button
        views.tapToSendButton.setOnClickListener {
            animationHelper.animateCardClick(it) {
                AnalyticsUtils.logFeatureUsage("test_message", "premium_card_click", true)
                viewModel.sendMessageToWatch("Test")
            }
        }

        // Premium camera button
        views.cameraButton.setOnClickListener {
            handleCameraLaunch("premium_button_click", withAnimation = true, view = it)
        }

        // Square camera button
        views.squareCameraButton.setOnClickListener {
            handleCameraLaunch("square_button_click", withAnimation = false, view = null)
        }

        // Manual shutter selection button
        updateManualSelectionButtonVisibility()
        views.manualSelectionButton.setOnClickListener {
            animationHelper.animateCardClick(it) {
                startActivity(Intent(this, ManualShutterButtonSelectionActivity::class.java))
            }
        }
    }

    /**
     * Updates the visibility of the manual selection button based on whether
     * camera app candidates have been detected.
     */
    private fun updateManualSelectionButtonVisibility() {
        // Load candidates from preferences if not already loaded
        if (CameraAppCandidateStore.candidatesByApp.isEmpty()) {
            CameraAppCandidateStore.loadAllFromPrefs(this)
        }

        // Update visibility based on whether we have candidates
        if (CameraAppCandidateStore.candidatesByApp.isEmpty()) {
            views.manualSelectionButton.visibility = View.GONE
        } else {
            views.manualSelectionButton.visibility = View.VISIBLE
        }
    }

    private fun checkAndRequestPermissions() {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                logPermissionStateWithCrashlytics("POST_NOTIFICATIONS", false)
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                logPermissionStateWithCrashlytics("POST_NOTIFICATIONS", true)
            }
        }

        // Check accessibility service
        if (!isAccessibilityServiceEnabled()) {
            logServiceStateWithCrashlytics("accessibility", "disabled")
            dialogManager.showAccessibilityDialog()
        } else {
            logServiceStateWithCrashlytics("accessibility", "enabled")
        }
    }

    /**
     * Handles the result of permission requests.
     * When notification permission is granted, this method starts the MessageService.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Notification permission granted by user")
                    logPermissionStateWithCrashlytics("POST_NOTIFICATIONS", true)

                    // Now that we have permission, start the MessageService if we have a device
                    device?.let { dev ->
                        Log.d(TAG, "Starting MessageService after permission grant")
                        startMessageServiceIfPermitted(dev)
                    } ?: run {
                        Log.w(TAG, "Device is null, cannot start MessageService after permission grant")
                    }
                } else {
                    Log.w(TAG, "Notification permission denied by user")
                    logPermissionStateWithCrashlytics("POST_NOTIFICATIONS", false)
                    Toast.makeText(
                        this,
                        "Notification permission is required for the app to receive messages from your watch",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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
     * Called when the activity resumes. Registers for device and app events.
     * This method ensures the UI stays in sync with the device state.
     */
    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity onResume")
        viewModel.registerForDeviceEvents()
        viewModel.checkWatchAppStatus()
        viewModel.trackAppLaunch(this)
        viewModel.checkTimingTriggers(this)

        // Update button visibility in case candidates were added while activity was paused
        updateManualSelectionButtonVisibility()
        updateSetupIncompleteBanner()
    }

    private fun updateSetupIncompleteBanner() {
        val showBanner = !preferencesRepository.isSetupComplete
        views.setupIncompleteBanner?.visibility = if (showBanner) View.VISIBLE else View.GONE
    }

    /**
     * Called when the activity is paused. Unregisters event listeners.
     */
    public override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity onPause")
        viewModel.unregisterFromDeviceEvents()
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
     * Starts the MessageService if notification permission is granted (Android 13+) or unconditionally (older Android).
     */
    private fun startMessageServiceIfPermitted(dev: IQDevice) {
        val serviceIntent = MessageService.createIntent(this, dev, CommConstants.COMM_WATCH_ID)

        // On Android 13+ ensure notification permission is granted before starting a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted - deferring service start until permission is granted")
                FirebaseCrashlytics.getInstance().log("MessageService not started - missing notification permission")
                // Request permission; the activity flow already requests it in checkAndRequestPermissions()
                return
            }
        }

        // Start the service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting MessageService as foreground service")
                startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Starting MessageService")
                startService(serviceIntent)
            }
            Log.d(TAG, "MessageService start command sent successfully")
            FirebaseCrashlytics.getInstance().log("MessageService started for ${dev.friendlyName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MessageService", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            Toast.makeText(this, "Failed to start message service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun updateDeviceStatusColor(status: IQDevice.IQDeviceStatus) {
        // Always keep text white for good contrast
        views.deviceStatusView.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Update badge background color based on connection status
        views.deviceStatusBadge?.setCardBackgroundColor(
            when (status) {
                IQDevice.IQDeviceStatus.CONNECTED -> ContextCompat.getColor(this, R.color.success)
                IQDevice.IQDeviceStatus.NOT_CONNECTED -> ContextCompat.getColor(this, R.color.error)
                else -> ContextCompat.getColor(this, R.color.warning) // For UNKNOWN and other states
            }
        )
    }

    /**
     * Creates the options menu for this activity.
     * @param menu The options menu in which items are placed
     * @return true to display the menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.device, menu)

        // Show/hide rate app menu item based on ViewModel state
        val rateAppItem = menu.findItem(R.id.rate_app)
        val shouldShowRateApp = viewModel.shouldShowRateAppMenu.value ?: false
        rateAppItem?.isVisible = shouldShowRateApp

        return true
    }

    /**
     * Handles menu item selection.
     * @param item The selected menu item
     * @return true if the event was handled
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.system_status -> {
                AnalyticsUtils.logFeatureUsage("compatibility_check", "menu_click", true)
                startActivity(Intent(this, SystemStatusActivity::class.java))
                true
            }
            R.id.help -> {
                AnalyticsUtils.logFeatureUsage("help", "menu_click", true)
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.rate_app -> {
                dialogManager.openAppStoreForRating()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Unified camera launch handler that consolidates duplicate code.
     * This method handles camera launch with optional animations and consistent logging.
     *
     * @param source The source button identifier for analytics (e.g., "premium_button_click")
     * @param withAnimation Whether to play press/release animations
     * @param view The view to animate (required if withAnimation is true)
     */
    private fun handleCameraLaunch(source: String, withAnimation: Boolean, view: android.view.View?) {
        // Log the launch
        FirebaseCrashlytics.getInstance().log("Camera launch button clicked: $source")
        AnalyticsUtils.logFeatureUsage("camera_launch", source, true)

        // Use animation helper for unified handling
        animationHelper.animateCameraLaunch(if (withAnimation) view else null) {
            CameraUtils.launchCamera(this@DeviceActivity)
        }
    }
}
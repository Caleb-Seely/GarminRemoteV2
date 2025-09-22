package com.garmin.android.apps.camera.click.comm.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.adapter.IQDeviceAdapter
import com.garmin.android.apps.camera.click.comm.utils.CameraUtils
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Event
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import android.widget.Button
import android.widget.Toast
import com.garmin.android.apps.camera.click.comm.utils.AccessibilityUtils
import com.garmin.android.apps.camera.click.comm.activities.ManualShutterButtonSelectionActivity
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.garmin.android.apps.camera.click.comm.repository.DeveloperNoteContentRepository
import com.garmin.android.apps.camera.click.comm.dialogs.DeveloperNoteDialogFragment
import com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils

private const val TAG = "MainActivity"
private const val PREFS_NAME = "CameraClickPrefs"
private const val KEY_AUTO_LAUNCH_CAMERA = "auto_launch_camera"
private const val KEY_PREFERRED_DEVICE_ID = "preferred_device_id"

/**
 * Main activity of the CameraClick application that handles device discovery and management.
 * This activity serves as the entry point for the app and manages the ConnectIQ SDK lifecycle.
 * 
 * Key responsibilities:
 * - Initializes and manages the ConnectIQ SDK for Garmin device communication
 * - Discovers and displays available Garmin devices
 * - Handles device selection and navigation to device-specific screens
 * - Manages app preferences and auto-launch settings
 * - Integrates with Firebase Analytics for usage tracking
 */
class MainActivity : AppCompatActivity() {

    private lateinit var connectIQ: ConnectIQ
    private lateinit var adapter: IQDeviceAdapter
    private lateinit var prefs: SharedPreferences
    private var sessionStartTime: Long = 0

    private var autoLaunchAttempted = false  // Flag to track if we've already tried auto-launching
    private var isSdkReady = false
    private var isFirstLaunch = true

    /**
     * Listener for ConnectIQ SDK events that handles initialization status and device updates.
     * This listener is responsible for managing the SDK's lifecycle and responding to state changes.
     */
    private val connectIQListener: ConnectIQ.ConnectIQListener =
        object : ConnectIQ.ConnectIQListener {
            /**
             * Handles SDK initialization errors and updates the UI accordingly.
             * @param errStatus The error status returned by the SDK
             */
            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                setEmptyState(getString(R.string.initialization_error) + ": " + errStatus.name)
                isSdkReady = false
                FirebaseCrashlytics.getInstance().log("ConnectIQ SDK initialization failed: ${errStatus.name}")
            }

            /**
             * Called when the SDK is ready for use. Triggers device discovery.
             */
            override fun onSdkReady() {
                isSdkReady = true
                FirebaseCrashlytics.getInstance().log("ConnectIQ SDK ready")
                loadDevices(tryAutoLaunch = true)
            }

            /**
             * Called when the SDK is shutting down. Updates the internal state.
             */
            override fun onSdkShutDown() {
                isSdkReady = false
                FirebaseCrashlytics.getInstance().log("ConnectIQ SDK shut down")
            }
        }

    /**
     * Initializes the activity, sets up the UI, and initializes the ConnectIQ SDK.
     * This method:
     * - Initializes Firebase Analytics and Crashlytics
     * - Sets up user preferences
     * - Configures the UI components
     * - Initializes the ConnectIQ SDK
     * 
     * @param savedInstanceState The saved instance state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStartTime = System.currentTimeMillis()
        
    // Firebase/Crashlytics initialized in Application class (CameraClickApplication)

        // Initialize preferences first
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Calculate time since last session if available
        val lastSessionEnd = prefs.getLong("last_session_end", 0)
        val timeSinceLastSession = if (lastSessionEnd > 0) {
            System.currentTimeMillis() - lastSessionEnd
        } else null

        // Initialize Firebase Analytics with detailed logging
        try {
            // Analytics initialized by Application. Keep app open and engagement logs here.
            AnalyticsUtils.logAppOpen(this, isFirstLaunch)
            AnalyticsUtils.logUserEngagement(0, timeSinceLastSession)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        setContentView(R.layout.activity_main)

        // Set status bar color to black
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.BLACK
        }

        // Load all saved candidate lists on app start
        CameraAppCandidateStore.loadAllFromPrefs(this)

        setupUi()
    setupConnectIQSdk()
    }

    /**
     * Called when the activity resumes. Reloads devices if the SDK is ready.
     */
    public override fun onResume() {
        super.onResume()
        if (isSdkReady) {
            loadDevices()
        }
    }

    /**
     * Called when the activity is destroyed. Ensures proper cleanup of SDK resources.
     * This method:
     * - Logs session duration
     * - Saves session end time
     * - Releases ConnectIQ SDK resources
     */
    public override fun onDestroy() {
        super.onDestroy()
        
        // Log session duration
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        AnalyticsUtils.logUserEngagement(sessionDuration)
        
        // Save session end time
        prefs.edit().putLong("last_session_end", System.currentTimeMillis()).apply()
        
        releaseConnectIQSdk()
    }

    /**
     * Releases ConnectIQ SDK resources and unregisters all event listeners.
     * This ensures proper cleanup when the activity is destroyed.
     */
    private fun releaseConnectIQSdk() {
        try {
            // It is a good idea to unregister everything and shut things down to
            // release resources and prevent unwanted callbacks.
            connectIQ.unregisterAllForEvents()
            connectIQ.shutdown(this)
        } catch (e: InvalidStateException) {
            // This is usually because the SDK was already shut down
            // so no worries.
        }
    }

    /**
     * Sets up the user interface components including the RecyclerView and adapter.
     * This method initializes the device list UI and configures the adapter for displaying devices.
     */
    private fun setupUi() {
        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Setup UI.
        adapter = IQDeviceAdapter(
            onItemClickListener = { onItemClick(it) },
            onSetDefaultDeviceListener = { onSetDefaultDevice(it) },
            getPreferredDeviceId = { prefs.getString(KEY_PREFERRED_DEVICE_ID, null) }
        )
        findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        
        // Setup developer note button
        setupDeveloperNoteButton()
    }
    
    /**
     * Sets up the developer note floating action button.
     * This button allows users to learn about the developer and provides a permanent way
     * to access developer information beyond the automatic timing triggers.
     */
    private fun setupDeveloperNoteButton() {
        val developerNoteButton = findViewById<FloatingActionButton>(R.id.fab_developer_note)

        try {
            // Only show the developer note button if the app has been installed for at least 5 days
            val daysSinceInstall = com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.getDaysSinceFirstInstall(this)

            if (daysSinceInstall >= 5) {
                developerNoteButton.visibility = View.VISIBLE
                developerNoteButton.setOnClickListener {
                    Log.d(TAG, "Developer note button clicked from MainActivity")
                    FirebaseCrashlytics.getInstance().log("Developer note button clicked from MainActivity")
                    AnalyticsUtils.logFeatureUsage("developer_note", "main_button_clicked", true)

                    showDeveloperNoteFromMain()
                }
            } else {
                // Hide the button until the app has been installed long enough
                developerNoteButton.visibility = View.GONE
                Log.d(TAG, "Developer note button hidden - days since install: $daysSinceInstall")
                FirebaseCrashlytics.getInstance().log("Developer note button hidden - days since install: $daysSinceInstall")
            }

        } catch (e: Exception) {
            // If anything goes wrong, keep the button hidden and record the error
            try {
                developerNoteButton.visibility = View.GONE
            } catch (_: Exception) {
                // ignore
            }
            Log.e(TAG, "Error evaluating developer note visibility", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
    
    /**
     * Shows the developer note popup from the main activity.
     * This shows the current version in the cycle, providing users with
     * the appropriate message based on their progression.
     */
    private fun showDeveloperNoteFromMain() {
        try {
            // Get the current version from the normal cycle progression
            // This respects the user's position in the 4-message cycle
            val currentVersion = com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.getCurrentVersionIndex(this)
            
            // If the cycle is complete, show the first version
            val versionToShow = if (currentVersion == -1) 0 else currentVersion
            
            val content = DeveloperNoteContentRepository.getContentForVersion(this, versionToShow)
            
            if (content != null) {
                Log.d(TAG, "Showing developer note from main activity - version $versionToShow")
                
                // Create and show the dialog fragment
                val dialog = DeveloperNoteDialogFragment.newInstance(content)
                dialog.show(supportFragmentManager, "developer_note_main")
                
                // Log analytics for main activity popup
                val params = Bundle().apply {
                    putInt("version", versionToShow)
                    putBoolean("from_main_activity", true)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_main_popup_shown", params)
                
                FirebaseCrashlytics.getInstance().log("Developer note popup shown from main activity - version $versionToShow")
                
                // Note: We don't call recordPopupShown() here because this is user-initiated,
                // not part of the automatic timing cycle. The dialog fragment will record shown
                // for automatic flows; for user-initiated flows we intentionally don't advance
                // the automatic cycle here.
                
            } else {
                Log.w(TAG, "No content found for developer note version $versionToShow")
                Toast.makeText(this, "Developer information temporarily unavailable", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing developer note from main activity", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "main_popup_failed", e.message ?: "unknown")
            Toast.makeText(this, "Unable to show developer information", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handles device selection and navigates to the DeviceActivity.
     * @param device The selected Garmin device
     */
    private fun onItemClick(device: IQDevice) {
        startActivity(DeviceActivity.getIntent(this, device))
    }

    /**
     * Handles setting or removing a device as the default auto-launch device.
     * @param device The device to set/remove as default
     */
    private fun onSetDefaultDevice(device: IQDevice) {
        val currentPreferred = prefs.getString(KEY_PREFERRED_DEVICE_ID, null)
        val deviceId = device.deviceIdentifier.toString()
        
        if (currentPreferred == deviceId) {
            // Remove as default device
            prefs.edit().remove(KEY_PREFERRED_DEVICE_ID).apply()
            
            // Log analytics
            AnalyticsUtils.logDefaultDeviceSet(
                device.friendlyName ?: deviceId,
                deviceId,
                false
            )
            
            // Show confirmation toast
            android.widget.Toast.makeText(
                this,
                "${device.friendlyName ?: deviceId} removed as default device",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            Log.d(TAG, "Removed default device: ${device.friendlyName}")
        } else {
            // Set as default device
            prefs.edit().putString(KEY_PREFERRED_DEVICE_ID, deviceId).apply()
            
            // Log analytics
            AnalyticsUtils.logDefaultDeviceSet(
                device.friendlyName ?: deviceId,
                deviceId,
                true
            )
            
            // Show confirmation toast
            android.widget.Toast.makeText(
                this,
                "${device.friendlyName ?: deviceId} set as default device",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            Log.d(TAG, "Set default device: ${device.friendlyName} (${deviceId})")
        }
        
        // Refresh the adapter to update the visual indicators
        adapter.notifyDataSetChanged()
    }

    /**
     * Initializes the ConnectIQ SDK with wireless connection type.
     * This method sets up the SDK for wireless communication with Garmin devices.
     */
    private fun setupConnectIQSdk() {
        // Here we are specifying that we want to use a WIRELESS bluetooth connection.
        // We could have just called getInstance() which would by default create a version
        // for WIRELESS, unless we had previously gotten an instance passing TETHERED
        // as the connection type.
        connectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS)
        FirebaseCrashlytics.getInstance().log("Initializing ConnectIQ SDK")

        // Initialize the SDK
        connectIQ.initialize(this, true, connectIQListener)
    }

    /**
     * Creates the options menu for the activity.
     * @param menu The menu to inflate
     * @return true to display the menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
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
            R.id.load_devices -> {
                AnalyticsUtils.logFeatureUsage("load_devices", "menu_click", true)
                loadDevices(tryAutoLaunch = false)
                true
            }
            R.id.reliability_test -> {
                AnalyticsUtils.logFeatureUsage("reliability_test", "menu_click", true)
                startActivity(Intent(this, ReliabilityTestActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Loads the list of available devices and optionally attempts to auto-launch the first device.
     * This method:
     * - Retrieves the list of known devices
     * - Updates device status
     * - Registers for device status updates
     * - Optionally auto-launches the first device
     * 
     * @param tryAutoLaunch Whether to attempt auto-launching the first device
     */
    private fun loadDevices(tryAutoLaunch: Boolean = false) {
        try {
            // Retrieve the list of known devices
            val devices = connectIQ.knownDevices ?: listOf()
            
            // Get the connectivity status for each device for initial state
            devices.forEach {
                it.status = connectIQ.getDeviceStatus(it)
                AnalyticsUtils.logDeviceConnection(it.friendlyName, it.status?.name ?: "unknown")
            }

            if (devices.isNotEmpty()) {
                adapter.submitList(devices)
                hideEmptyState() // Hide empty state when devices are found
                
                // Register for device status updates
                devices.forEach {
                    connectIQ.registerForDeviceEvents(it) { device, status ->
                        adapter.updateDeviceStatus(device, status)
                        AnalyticsUtils.logDeviceConnection(device.friendlyName, status.name)
                    }
                }

                // Auto-launch based on user preference or first connected device
                if (tryAutoLaunch && !autoLaunchAttempted) {
                    autoLaunchAttempted = true
                    
                    val deviceToLaunch = getDeviceForAutoLaunch(devices)
                    if (deviceToLaunch != null) {
                        Log.d(TAG, "Auto-launching device: ${deviceToLaunch.friendlyName}")
                        startActivity(DeviceActivity.getIntent(this, deviceToLaunch))
                        
                        // Only auto-launch camera if the preference is enabled
                        if (prefs.getBoolean(KEY_AUTO_LAUNCH_CAMERA, false)) {
                            // Launch camera after a short delay to ensure device connection is established
                            Handler(Looper.getMainLooper()).postDelayed({
                                CameraUtils.launchCamera(this)
                            }, 1000)
                        }
                    } else {
                        Log.d(TAG, "No suitable device found for auto-launch")
                    }
                }
            } else {
                setEmptyState(getString(R.string.no_devices))
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error loading devices", e)
            setEmptyState(getString(R.string.initialization_error))
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Service unavailable", e)
            setEmptyState(getString(R.string.service_unavailable))
        }
    }

    /**
     * Updates the empty state text when no devices are available or an error occurs.
     * @param text The text to display in the empty state
     */
    private fun setEmptyState(text: String) {
        findViewById<TextView>(android.R.id.empty)?.apply {
            this.text = text
            visibility = View.VISIBLE
        }
        findViewById<RecyclerView>(android.R.id.list)?.visibility = View.GONE
    }

    /**
     * Hides the empty state and shows the device list.
     */
    private fun hideEmptyState() {
        findViewById<TextView>(android.R.id.empty)?.visibility = View.GONE
        findViewById<RecyclerView>(android.R.id.list)?.visibility = View.VISIBLE
    }
    
    /**
     * Determines which device should be auto-launched based on user preferences and device status.
     * Priority order:
     * 1. User's preferred device (if connected)
     * 2. First connected device
     * 3. null (no auto-launch)
     * 
     * @param devices List of available devices
     * @return The device to auto-launch, or null if none should be launched
     */
    private fun getDeviceForAutoLaunch(devices: List<IQDevice>): IQDevice? {
        if (devices.isEmpty()) return null
        
        val preferredDeviceId = prefs.getString(KEY_PREFERRED_DEVICE_ID, null)
        val preferredDevice = preferredDeviceId?.let { deviceId ->
            devices.find { it.deviceIdentifier.toString() == deviceId }
        }
        
        // First try to find and use the preferred device if it's connected
        preferredDevice?.let { device ->
            if (device.status == IQDevice.IQDeviceStatus.CONNECTED) {
                Log.d(TAG, "Auto-launching preferred device: ${device.friendlyName}")
                
                // Log analytics - preferred device launch
                AnalyticsUtils.logAutoLaunchAttempt(
                    preferredDeviceName = device.friendlyName,
                    actualDeviceName = device.friendlyName,
                    usedFallback = false,
                    success = true
                )
                
                return device
            } else {
                Log.d(TAG, "Preferred device ${device.friendlyName} is not connected (${device.status})")
            }
        }
        
        // Fall back to first connected device
        val firstConnectedDevice = devices.find { it.status == IQDevice.IQDeviceStatus.CONNECTED }
        if (firstConnectedDevice != null) {
            Log.d(TAG, "Auto-launching first connected device: ${firstConnectedDevice.friendlyName}")
            
            // Log analytics - fallback device launch
            AnalyticsUtils.logAutoLaunchAttempt(
                preferredDeviceName = preferredDevice?.friendlyName,
                actualDeviceName = firstConnectedDevice.friendlyName,
                usedFallback = true,
                success = true
            )
            
            return firstConnectedDevice
        }
        
        Log.d(TAG, "No connected devices available for auto-launch")
        
        // Log analytics - failed auto-launch
        AnalyticsUtils.logAutoLaunchAttempt(
            preferredDeviceName = preferredDevice?.friendlyName,
            actualDeviceName = null,
            usedFallback = false,
            success = false
        )
        
        return null
    }
    
    /**
     * Checks timing triggers with priority system to avoid overwhelming users.
     * Priority: Review prompt first, then developer note on next app launch.
     */
    private fun checkTimingTriggersWithPriority() {
        try {
            Log.d(TAG, "=== CHECKING TIMING TRIGGERS ===")
            
            // Always track app launch for review prompting
            com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.trackAppLaunch(this)
            
            // Debug current state first
            debugTimingIssues()
            
            // Check if review prompt should be shown (higher priority)
            val shouldShowReview = com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.shouldShowReviewPrompt(this)
            val shouldShowDeveloperNote = com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.shouldShowPopup(this)
            
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
            } else {
                Log.d(TAG, "No timing triggers ready to show")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking timing triggers", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
    
    /**
     * Comprehensive debug method to understand timing trigger issues
     */
    private fun debugTimingIssues() {
        Log.d(TAG, "=== COMPREHENSIVE TIMING DEBUG ===")
        
        try {
            // Review Debug
            val reviewPrefs = getSharedPreferences("in_app_review_prefs", Context.MODE_PRIVATE)
            val reviewFirstInstall = reviewPrefs.getLong("first_install_date", 0)
            val reviewLastRequest = reviewPrefs.getLong("last_review_request", 0)
            val reviewCompleted = reviewPrefs.getBoolean("review_completed", false)
            val reviewLaunchCount = reviewPrefs.getInt("launch_count", 0)
            
            Log.d(TAG, "REVIEW STATE:")
            Log.d(TAG, "  First install: $reviewFirstInstall (${java.util.Date(reviewFirstInstall)})")
            Log.d(TAG, "  Last request: $reviewLastRequest (${if (reviewLastRequest > 0) java.util.Date(reviewLastRequest) else "Never"})")
            Log.d(TAG, "  Completed: $reviewCompleted")
            Log.d(TAG, "  Launch count: $reviewLaunchCount")
            Log.d(TAG, "  Days since install: ${com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.getDaysSinceFirstInstall(this)}")
            Log.d(TAG, "  Should show: ${com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.shouldShowReviewPrompt(this)}")
            
            // Developer Note Debug
            val devPrefs = getSharedPreferences("developer_note_prefs", Context.MODE_PRIVATE)
            val devFirstInstall = devPrefs.getLong("developer_note_first_install_date", 0)
            val devLastPopup = devPrefs.getLong("developer_note_last_popup_date", 0)
            val devVersion = devPrefs.getInt("developer_note_popup_version_index", 0)
            
            Log.d(TAG, "DEVELOPER NOTE STATE:")
            Log.d(TAG, "  First install: $devFirstInstall (${java.util.Date(devFirstInstall)})")
            Log.d(TAG, "  Last popup: $devLastPopup (${if (devLastPopup > 0) java.util.Date(devLastPopup) else "Never"})")
            Log.d(TAG, "  Version index: $devVersion")
            Log.d(TAG, "  Days since install: ${com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.getDaysSinceFirstInstall(this)}")
            Log.d(TAG, "  Days since last popup: ${com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.getDaysSinceLastPopup(this)}")
            Log.d(TAG, "  Should show: ${com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.shouldShowPopup(this)}")
            
            // Current time
            Log.d(TAG, "CURRENT TIME: ${System.currentTimeMillis()} (${java.util.Date()})")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in debug", e)
        }
        
        Log.d(TAG, "=== END COMPREHENSIVE DEBUG ===")
    }
    
    /**
     * Defers the developer note to the next app launch by adjusting its timing.
     */
    private fun deferDeveloperNoteToNextLaunch() {
        try {
            Log.d(TAG, "Deferring developer note to next app launch to avoid overwhelming user")

            // Use centralized manager to defer to next launch
            com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.deferToNextLaunch(this)

            Log.d(TAG, "Developer note deferred via DeveloperNoteManager")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deferring developer note", e)
        }
    }
    
    /**
     * Shows the developer note popup automatically based on timing conditions.
     */
    private fun showDeveloperNotePopup() {
        try {
            val currentVersion = com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.getCurrentVersionIndex(this)
            val content = DeveloperNoteContentRepository.getContentForVersion(this, currentVersion)
            
            if (content != null) {
                Log.d(TAG, "Showing automatic developer note popup - version $currentVersion")
                
                val dialog = DeveloperNoteDialogFragment.newInstance(content)
                dialog.show(supportFragmentManager, "developer_note_auto")
                
                // The dialog fragment records popup shown; avoid double-recording here
                
                Log.d(TAG, "Developer note popup shown successfully")
                
            } else {
                Log.w(TAG, "No content found for developer note version $currentVersion")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing automatic developer note popup", e)
        }
    }
    
    /**
     * Shows the Google Play in-app review prompt.
     */
    private fun showReviewPrompt() {
        try {
            Log.d(TAG, "Attempting to show in-app review prompt")
            
            com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.launchInAppReview(this) { success ->
                Log.d(TAG, "Review prompt completed: $success")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing review prompt", e)
        }
    }
    
    /**
     * Test method to force trigger timing checks for debugging.
     */
    fun testTimingTriggers() {
        Log.d(TAG, "=== TESTING TIMING TRIGGERS ===")
        
        // Reset both states first
        com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.resetPopupState(this)
        com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.resetReviewState(this)
        
        // Set install dates to 6 days ago to trigger both
        com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager.setTestInstallDate(this, 6)
        com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.setTestInstallDate(this, 6)
        
        // Track a launch to initialize review state
        com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils.trackAppLaunch(this)
        
        // Debug current state
        debugTimingIssues()
        
        // Force trigger the checks with priority system
        checkTimingTriggersWithPriority()
        
        Log.d(TAG, "=== END TESTING ===")
    }

}
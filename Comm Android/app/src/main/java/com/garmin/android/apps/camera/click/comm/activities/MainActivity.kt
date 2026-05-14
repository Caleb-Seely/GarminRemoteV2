package com.garmin.android.apps.camera.click.comm.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.adapter.IQDeviceAdapter
import com.garmin.android.apps.camera.click.comm.dialogs.DeveloperNoteDialogFragment
import com.garmin.android.apps.camera.click.comm.repository.DeveloperNoteContentRepository
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import com.garmin.android.apps.camera.click.comm.utils.CameraUtils
import com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils
import com.garmin.android.apps.camera.click.comm.repository.DevicePreferencesRepository
import com.garmin.android.apps.camera.click.comm.viewmodels.MainActivityViewModel
import com.garmin.android.connectiq.IQDevice
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "MainActivity"

/**
 * Main activity of the CameraClick application that handles device discovery and management.
 * This activity serves as the entry point for the app and manages the ConnectIQ SDK lifecycle.
 *
 * Refactored to use MVVM pattern with MainActivityViewModel.
 *
 * Key responsibilities:
 * - Observes ViewModel LiveData and updates UI accordingly
 * - Handles user interactions and delegates to ViewModel
 * - Manages UI state and displays dialogs
 * - Provides navigation to other activities
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferencesRepository: DevicePreferencesRepository

    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var adapter: IQDeviceAdapter
    private var isFirstLaunch = true
    private var sdkInitialized = false

    /**
     * Initializes the activity, sets up the UI, and initializes the ConnectIQ SDK.
     * This method:
     * - Sets up the content view and status bar
     * - Loads camera candidate lists
     * - Tracks app launch with ViewModel
     * - Sets up observers for ViewModel LiveData
     * - Initializes the UI and ConnectIQ SDK via ViewModel
     *
     * @param savedInstanceState The saved instance state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect first-time users to the onboarding wizard
        if (preferencesRepository.isFirstAppLaunch) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Set status bar color to black (modern approach)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.BLACK

        // Load all saved candidate lists on app start
        CameraAppCandidateStore.loadAllFromPrefs(this)

        // Track app launch through ViewModel
        viewModel.trackAppLaunch(this, isFirstLaunch)

        // Setup observers before UI to ensure we're ready to receive updates
        setupObservers()

        // Setup UI
        setupUi()

        // Initialize ConnectIQ SDK through ViewModel
        viewModel.initializeConnectIQSdk(this)
        sdkInitialized = true

        // Check timing triggers (review prompt, developer notes)
        viewModel.checkTimingTriggers(this)
    }

    /**
     * Sets up observers for all ViewModel LiveData.
     * This method observes:
     * - devices: Updates the adapter with the new device list
     * - sdkStatus: Handles SDK state changes
     * - emptyState: Shows/hides empty state
     * - autoLaunchDevice: Navigates to DeviceActivity
     * - launchCamera: Launches camera with delay
     * - showReviewPrompt: Shows review prompt
     * - showDeveloperNote: Shows developer note dialog
     * - showDeveloperNoteButton: Shows/hides FAB
     * - toastMessage: Shows toasts
     * - preferredDeviceId: Updates adapter
     */
    private fun setupObservers() {
        // Observe devices list
        viewModel.devices.observe(this) { devices ->
            adapter.submitList(devices)
        }

        // Observe SDK status
        viewModel.sdkStatus.observe(this) { status ->
            when (status) {
                is MainActivityViewModel.SdkStatus.Ready -> {
                    Log.d(TAG, "SDK Ready - loading devices")
                    // SDK is ready, devices will be loaded by onSdkReady callback
                }
                is MainActivityViewModel.SdkStatus.Error -> {
                    Log.e(TAG, "SDK Error: ${status.errorStatus}")
                    Toast.makeText(this, "Error initializing Garmin SDK: ${status.errorStatus}", Toast.LENGTH_LONG).show()
                }
                is MainActivityViewModel.SdkStatus.ShutDown -> {
                    Log.d(TAG, "SDK Shut Down")
                }
                is MainActivityViewModel.SdkStatus.Initializing -> {
                    Log.d(TAG, "SDK Initializing...")
                }
            }
        }

        // Observe empty state
        viewModel.emptyState.observe(this) { message ->
            if (message != null) {
                setEmptyState(message)
            } else {
                hideEmptyState()
            }
        }

        // Observe auto-launch device
        viewModel.autoLaunchDevice.observe(this) { device ->
            device?.let {
                startActivity(DeviceActivity.getIntent(this, it))
                viewModel.onAutoLaunchConsumed()
            }
        }

        // Observe launch camera
        viewModel.launchCamera.observe(this) { shouldLaunch ->
            if (shouldLaunch) {
                // Launch camera after a short delay to ensure device connection is established
                Handler(Looper.getMainLooper()).postDelayed({
                    CameraUtils.launchCamera(this)
                }, 1000)
                viewModel.onLaunchCameraConsumed()
            }
        }

        // Observe review prompt
        viewModel.showReviewPrompt.observe(this) { shouldShow ->
            if (shouldShow) {
                showReviewPrompt()
                viewModel.onReviewPromptShown()
            }
        }

        // Observe developer note
        viewModel.showDeveloperNote.observe(this) { event ->
            event?.let {
                showDeveloperNoteDialog(it.version, it.isAutomatic)
                viewModel.onDeveloperNoteShown()
            }
        }

        // Observe developer note button visibility
        viewModel.showDeveloperNoteButton.observe(this) { shouldShow ->
            findViewById<FloatingActionButton>(R.id.fab_developer_note)?.visibility =
                if (shouldShow) View.VISIBLE else View.GONE
        }

        // Observe toast messages
        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Observe preferred device ID changes to update adapter
        viewModel.preferredDeviceId.observe(this) { _ ->
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Called when the activity resumes. Reloads devices through ViewModel.
     * Only loads devices if SDK is already ready to avoid InvalidStateException.
     */
    public override fun onResume() {
        super.onResume()
        // Only load devices if SDK is ready (onSdkReady callback will load on first init)
        if (viewModel.sdkStatus.value is com.garmin.android.apps.camera.click.comm.viewmodels.MainActivityViewModel.SdkStatus.Ready) {
            viewModel.loadDevices()
        }
    }

    /**
     * Called when the activity is destroyed. Ensures proper cleanup of SDK resources.
     * Delegates to ViewModel for session tracking and SDK shutdown.
     */
    public override fun onDestroy() {
        super.onDestroy()

        // Log session end through ViewModel
        viewModel.logSessionEnd()

        // Shutdown ConnectIQ SDK through ViewModel (only if it was initialized)
        if (sdkInitialized) {
            viewModel.shutdownConnectIQSdk(this)
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

        // Setup adapter with ViewModel integration
        adapter = IQDeviceAdapter(
            onItemClickListener = { onItemClick(it) },
            onSetDefaultDeviceListener = { onSetDefaultDevice(it) },
            getPreferredDeviceId = { viewModel.getPreferredDeviceId() }
        )
        findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // Setup developer note button
        setupDeveloperNoteButton()

        // Setup empty state Garmin Connect button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_open_garmin_connect)?.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage("com.garmin.android.apps.connectmobile")
            if (intent != null) startActivity(intent)
        }
    }

    /**
     * Sets up the developer note floating action button.
     * This button allows users to learn about the developer and provides a permanent way
     * to access developer information beyond the automatic timing triggers.
     * Visibility is controlled by ViewModel observer.
     */
    private fun setupDeveloperNoteButton() {
        val developerNoteButton = findViewById<FloatingActionButton>(R.id.fab_developer_note)

        try {
            // Set click listener - visibility is handled by observer
            developerNoteButton.setOnClickListener {
                Log.d(TAG, "Developer note button clicked from MainActivity")
                FirebaseCrashlytics.getInstance().log("Developer note button clicked from MainActivity")
                viewModel.showDeveloperNoteFromButton(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up developer note button", e)
            FirebaseCrashlytics.getInstance().recordException(e)
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
     * Delegates to ViewModel which handles preferences and analytics.
     * @param device The device to set/remove as default
     */
    private fun onSetDefaultDevice(device: IQDevice) {
        val currentPreferred = viewModel.getPreferredDeviceId()
        val deviceId = device.deviceIdentifier.toString()

        val isCurrentlyPreferred = currentPreferred == deviceId
        viewModel.setPreferredDevice(device, !isCurrentlyPreferred)

        // Adapter refresh is handled by observer
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
                viewModel.loadDevices(tryAutoLaunch = false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    /**
     * Shows the empty state layout when no devices are available.
     * @param text Unused — message is shown via layout strings
     */
    private fun setEmptyState(text: String) {
        findViewById<LinearLayout>(android.R.id.empty)?.visibility = View.VISIBLE
        findViewById<RecyclerView>(android.R.id.list)?.visibility = View.GONE
    }

    /**
     * Hides the empty state and shows the device list.
     */
    private fun hideEmptyState() {
        findViewById<LinearLayout>(android.R.id.empty)?.visibility = View.GONE
        findViewById<RecyclerView>(android.R.id.list)?.visibility = View.VISIBLE
    }
    
    /**
     * Shows the Google Play in-app review prompt.
     * Called when ViewModel triggers review prompt.
     */
    private fun showReviewPrompt() {
        try {
            Log.d(TAG, "Attempting to show in-app review prompt")

            InAppReviewUtils.launchInAppReview(this) { success ->
                Log.d(TAG, "Review prompt completed: $success")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing review prompt", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Shows the developer note dialog.
     * Called when ViewModel triggers developer note display.
     *
     * @param version The version index of the developer note to show
     * @param isAutomatic Whether this is an automatic popup or user-initiated
     */
    private fun showDeveloperNoteDialog(version: Int, isAutomatic: Boolean) {
        try {
            val content = DeveloperNoteContentRepository.getContentForVersion(this, version)

            if (content != null) {
                Log.d(TAG, "Showing developer note dialog - version $version, automatic: $isAutomatic")

                val dialog = DeveloperNoteDialogFragment.newInstance(content)
                val tag = if (isAutomatic) "developer_note_auto" else "developer_note_main"
                dialog.show(supportFragmentManager, tag)

                // Log analytics
                val params = Bundle().apply {
                    putInt("version", version)
                    putBoolean("automatic", isAutomatic)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_popup_shown", params)

                FirebaseCrashlytics.getInstance().log("Developer note popup shown - version $version, automatic: $isAutomatic")

            } else {
                Log.w(TAG, "No content found for developer note version $version")
                Toast.makeText(this, "Developer information temporarily unavailable", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing developer note dialog", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            Toast.makeText(this, "Unable to show developer information", Toast.LENGTH_SHORT).show()
        }
    }

}
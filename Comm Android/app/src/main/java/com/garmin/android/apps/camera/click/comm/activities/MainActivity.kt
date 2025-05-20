/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.camera.click.comm.activities

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
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

private const val TAG = "MainActivity"
private const val PREFS_NAME = "CameraClickPrefs"
private const val KEY_AUTO_LAUNCH_CAMERA = "auto_launch_camera"

/**
 * Main activity of the application that handles device discovery and management.
 * This activity serves as the entry point for the app and manages the ConnectIQ SDK lifecycle.
 */
class MainActivity : Activity() {

    private lateinit var connectIQ: ConnectIQ
    private lateinit var adapter: IQDeviceAdapter
    private lateinit var prefs: SharedPreferences

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
            }

            /**
             * Called when the SDK is ready for use. Triggers device discovery.
             */
            override fun onSdkReady() {
                isSdkReady = true
                loadDevices(tryAutoLaunch = true)
            }

            /**
             * Called when the SDK is shutting down. Updates the internal state.
             */
            override fun onSdkShutDown() {
                isSdkReady = false
            }
        }

    /**
     * Initializes the activity, sets up the UI, and initializes the ConnectIQ SDK.
     * @param savedInstanceState The saved instance state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContentView(R.layout.activity_main)

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
     */
    public override fun onDestroy() {
        super.onDestroy()
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
     */
    private fun setupUi() {
        // Setup UI.
        adapter = IQDeviceAdapter { onItemClick(it) }
        findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
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
     * Initializes the ConnectIQ SDK with wireless connection type.
     */
    private fun setupConnectIQSdk() {
        // Here we are specifying that we want to use a WIRELESS bluetooth connection.
        // We could have just called getInstance() which would by default create a version
        // for WIRELESS, unless we had previously gotten an instance passing TETHERED
        // as the connection type.
        connectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS)

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
            R.id.load_devices -> {
                loadDevices(tryAutoLaunch = false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Loads the list of available devices and optionally attempts to auto-launch the first device.
     * @param tryAutoLaunch Whether to attempt auto-launching the first device
     */
    private fun loadDevices(tryAutoLaunch: Boolean = false) {
        try {
            // Retrieve the list of known devices
            val devices = connectIQ.knownDevices ?: listOf()
            
            // Get the connectivity status for each device for initial state
            devices.forEach {
                it.status = connectIQ.getDeviceStatus(it)
            }

            if (devices.isNotEmpty()) {
                adapter.submitList(devices)
                
                // Register for device status updates
                devices.forEach {
                    connectIQ.registerForDeviceEvents(it) { device, status ->
                        adapter.updateDeviceStatus(device, status)
                    }
                }

                // Always auto-launch the first device on first open
                if (tryAutoLaunch && !autoLaunchAttempted) {
                    autoLaunchAttempted = true
                    // Start DeviceActivity with the first device
                    startActivity(DeviceActivity.getIntent(this, devices[0]))
                    
                    // Only auto-launch camera if the preference is enabled
                    if (prefs.getBoolean(KEY_AUTO_LAUNCH_CAMERA, false)) {
                        // Launch camera after a short delay to ensure device connection is established
                        Handler(Looper.getMainLooper()).postDelayed({
                            CameraUtils.launchCamera(this)
                        }, 1000)
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
        findViewById<TextView>(android.R.id.empty)?.text = text
    }
}
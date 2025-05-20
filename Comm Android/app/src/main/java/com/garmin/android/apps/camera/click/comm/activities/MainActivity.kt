/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.camera.click.comm.activities

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.adapter.IQDeviceAdapter
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Main activity of the application that handles device discovery and management.
 * This activity serves as the entry point for the app and manages the ConnectIQ SDK lifecycle.
 */
class MainActivity : Activity() {

    private lateinit var connectIQ: ConnectIQ
    private lateinit var adapter: IQDeviceAdapter

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
     * Loads and displays the list of known Garmin devices.
     * This function retrieves device information and registers for status updates.
     * If a device is connected and this is the first launch, it will automatically launch the DeviceActivity.
     */
    fun loadDevices(tryAutoLaunch: Boolean = false) {
        try {
            // Retrieve the list of known devices.
            val devices = connectIQ.knownDevices ?: listOf()
            // Get connected devices to check for auto-launch
            val connectedDevices = connectIQ.connectedDevices ?: listOf()

            // Get the connectivity status for each device for initial state.
            devices.forEach {
                it.status = connectIQ.getDeviceStatus(it)
            }
            // Try to find the first connected device and launch its activity if requested
            if (tryAutoLaunch) {
                autoLaunchAttempted = true  // Mark that we've attempted auto-launch

                val firstConnectedDevice = devices.find {
                    it.status == IQDevice.IQDeviceStatus.CONNECTED
                }

                if (firstConnectedDevice != null) {
                    // Launch the device activity for the first connected device
                    startActivity(DeviceActivity.getIntent(this, firstConnectedDevice))
                    return
                }
            }

            // Update ui list with the devices data
            adapter.submitList(devices)

            // Let's register for device status updates.
            devices.forEach {
                connectIQ.registerForDeviceEvents(it) { device, status ->
                    adapter.updateDeviceStatus(device, status)
                }
            }

        } catch (exception: InvalidStateException) {
            // This generally means you forgot to call initialize(), but since
            // we are in the callback for initialize(), this should never happen
        } catch (exception: ServiceUnavailableException) {
            // This will happen if for some reason your app was not able to connect
            // to the ConnectIQ service running within Garmin Connect Mobile.  This
            // could be because Garmin Connect Mobile is not installed or needs to
            // be upgraded.
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
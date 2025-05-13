/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.apps.connectiq.sample.comm.adapter.MessagesAdapter
import com.garmin.android.apps.connectiq.sample.comm.service.MessageService
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.garmin.android.apps.connectiq.sample.comm.utils.CameraUtils


private const val TAG = "DeviceActivity"
private const val EXTRA_IQ_DEVICE = "IQDevice"
private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"

// TODO Add a valid store app id.
private const val STORE_APP_ID = ""

/**
 * Activity that handles communication with a specific Garmin device.
 * This activity allows users to interact with a selected device, send messages, and manage the companion app.
 */
class DeviceActivity : Activity() {

    /**
     * Companion object containing static utility methods for the activity.
     */
    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100

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

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp

    private var appIsOpen = false

    /**
     * Listener for app open status changes that updates the UI accordingly.
     */
    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        Log.d(TAG, "App status changed: ${status.name}")
        Toast.makeText(applicationContext, "App Status: " + status.name, Toast.LENGTH_SHORT).show()

        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
            openAppButtonView?.setText(R.string.open_app_already_open)
        } else {
            appIsOpen = false
            openAppButtonView?.setText(R.string.open_app_open)
        }
    }

    /**
     * Initializes the activity and sets up the UI components.
     * @param savedInstanceState The saved instance state
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
        myApp = IQApp(COMM_WATCH_ID)
        appIsOpen = false

        val deviceNameView = findViewById<TextView>(R.id.devicename)
        deviceStatusView = findViewById(R.id.devicestatus)
        openAppButtonView = findViewById(R.id.openapp)

        deviceNameView?.text = device.friendlyName
        deviceStatusView?.text = device.status?.name
        openAppButtonView?.setOnClickListener { openMyApp() }

        // Add click listener for the tap to send button
        findViewById<TextView>(R.id.taptosend)?.setOnClickListener {
            onItemClick("Test")
        }

        // Add click listener for the camera button
        findViewById<TextView>(R.id.camera_button)?.setOnClickListener {
            if (checkCameraPermission()) {
                CameraUtils.launchCamera(this)
            }
        }
    }

    /**
     * Called when the activity resumes. Registers for device and app events.
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
     */
    public override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy")
        stopService(MessageService.createIntent(this, device, COMM_WATCH_ID))
    }

    /**
     * Opens the companion app on the connected Garmin device.
     */
    private fun openMyApp() {
        Log.d(TAG, "Opening app on device")
        Toast.makeText(this, "Opening app...", Toast.LENGTH_SHORT).show()

        try {
            connectIQ.openApplication(device, myApp, openAppListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app", e)
        }
    }

    /**
     * Registers for device status updates and updates the UI accordingly.
     */
    private fun listenByDeviceEvents() {
        Log.d(TAG, "Registering for device events")
        try {
            connectIQ.registerForDeviceEvents(device) { _, status ->
                Log.d(TAG, "Device status changed: ${status.name}")
                deviceStatusView?.text = status.name
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error registering for device events", e)
        }
    }

    /**
     * Checks if the companion app is installed on the device and updates the UI accordingly.
     */
    private fun getMyAppStatus() {
        Log.d(TAG, "Checking app status")
        try {
            connectIQ.getApplicationInfo(COMM_WATCH_ID, device, object :
                ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    Log.d(TAG, "App is installed, starting message service")
                    // Start the message service when the app is confirmed to be installed
                    startService(MessageService.createIntent(this@DeviceActivity, device, COMM_WATCH_ID))
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    Log.d(TAG, "App is not installed")
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
    private fun onItemClick(message: Any) {
        Log.d(TAG, "Sending message: $message")
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                Log.d(TAG, "Message send status: ${status.name}")
                Toast.makeText(this@DeviceActivity, status.name, Toast.LENGTH_SHORT).show()
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

    private fun checkCameraPermission(): Boolean {
        return if (CameraUtils.hasCameraPermission(this)) {
            true
        } else {
            Log.d(TAG, "Requesting camera permission")
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CameraUtils.launchCamera(this)
            } else {
                Toast.makeText(this, "Camera permission is required to use the camera", Toast.LENGTH_LONG).show()
                CameraUtils.launchCamera(this)
            }
        }
    }
}
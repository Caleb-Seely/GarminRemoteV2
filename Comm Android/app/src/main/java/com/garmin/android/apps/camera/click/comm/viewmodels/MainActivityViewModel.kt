package com.garmin.android.apps.camera.click.comm.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils
import com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MainActivity that manages device discovery and ConnectIQ SDK lifecycle.
 *
 * Responsibilities:
 * - ConnectIQ SDK initialization and management
 * - Device discovery and status monitoring
 * - Auto-launch logic for preferred devices
 * - Developer note and review prompt timing
 * - Analytics and session tracking
 *
 * This ViewModel follows MVVM architecture to separate UI from business logic.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "MainActivityViewModel"
        const val PREFS_NAME = "CameraClickPrefs"
        const val KEY_AUTO_LAUNCH_CAMERA = "auto_launch_camera"
        const val KEY_PREFERRED_DEVICE_ID = "preferred_device_id"
    }

    // SharedPreferences
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ConnectIQ SDK instance
    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()

    // Session tracking
    private val sessionStartTime: Long = System.currentTimeMillis()
    private var autoLaunchAttempted = false

    // LiveData for UI state
    private val _devices = MutableLiveData<List<IQDevice>>()
    val devices: LiveData<List<IQDevice>> = _devices

    private val _sdkStatus = MutableLiveData<SdkStatus>()
    val sdkStatus: LiveData<SdkStatus> = _sdkStatus

    private val _emptyState = MutableLiveData<String?>()
    val emptyState: LiveData<String?> = _emptyState

    private val _autoLaunchDevice = MutableLiveData<IQDevice?>()
    val autoLaunchDevice: LiveData<IQDevice?> = _autoLaunchDevice

    private val _launchCamera = MutableLiveData<Boolean>()
    val launchCamera: LiveData<Boolean> = _launchCamera

    private val _showReviewPrompt = MutableLiveData<Boolean>()
    val showReviewPrompt: LiveData<Boolean> = _showReviewPrompt

    private val _showDeveloperNote = MutableLiveData<DeveloperNoteEvent?>()
    val showDeveloperNote: LiveData<DeveloperNoteEvent?> = _showDeveloperNote

    private val _showDeveloperNoteButton = MutableLiveData<Boolean>()
    val showDeveloperNoteButton: LiveData<Boolean> = _showDeveloperNoteButton

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _preferredDeviceId = MutableLiveData<String?>()
    val preferredDeviceId: LiveData<String?> = _preferredDeviceId

    /**
     * Data classes for UI events
     */
    data class DeveloperNoteEvent(
        val version: Int,
        val isAutomatic: Boolean
    )

    sealed class SdkStatus {
        object Initializing : SdkStatus()
        object Ready : SdkStatus()
        data class Error(val errorStatus: ConnectIQ.IQSdkErrorStatus) : SdkStatus()
        object ShutDown : SdkStatus()
    }

    /**
     * ConnectIQ SDK listener
     */
    private val connectIQListener: ConnectIQ.ConnectIQListener = object : ConnectIQ.ConnectIQListener {
        override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
            Log.e(TAG, "ConnectIQ SDK initialization failed: ${errStatus.name}")
            FirebaseCrashlytics.getInstance().log("ConnectIQ SDK initialization failed: ${errStatus.name}")
            _sdkStatus.postValue(SdkStatus.Error(errStatus))
            _emptyState.postValue("Initialization error: ${errStatus.name}")
        }

        override fun onSdkReady() {
            Log.d(TAG, "ConnectIQ SDK ready")
            FirebaseCrashlytics.getInstance().log("ConnectIQ SDK ready")
            _sdkStatus.postValue(SdkStatus.Ready)
            loadDevices(tryAutoLaunch = true)
        }

        override fun onSdkShutDown() {
            Log.d(TAG, "ConnectIQ SDK shut down")
            FirebaseCrashlytics.getInstance().log("ConnectIQ SDK shut down")
            _sdkStatus.postValue(SdkStatus.ShutDown)
        }
    }

    init {
        // Load initial preferred device ID
        _preferredDeviceId.value = prefs.getString(KEY_PREFERRED_DEVICE_ID, null)

        // Migrate existing users to new popup system
        DeveloperNoteManager.migrateExistingUsers(getApplication())

        // Check developer note button visibility
        updateDeveloperNoteButtonVisibility()
    }

    /**
     * Initialize the ConnectIQ SDK
     */
    fun initializeConnectIQSdk(context: Context) {
        Log.d(TAG, "Initializing ConnectIQ SDK")
        FirebaseCrashlytics.getInstance().log("Initializing ConnectIQ SDK")
        _sdkStatus.value = SdkStatus.Initializing
        connectIQ.initialize(context, true, connectIQListener)
    }

    /**
     * Load devices from ConnectIQ SDK
     */
    fun loadDevices(tryAutoLaunch: Boolean = false) {
        viewModelScope.launch {
            try {
                val knownDevices = connectIQ.knownDevices ?: emptyList()
                Log.d(TAG, "Loading devices - found ${knownDevices.size} devices")

                // Get device status for each device
                knownDevices.forEach { device ->
                    device.status = connectIQ.getDeviceStatus(device)
                    AnalyticsUtils.logDeviceConnection(device.friendlyName, device.status?.name ?: "unknown")
                    Log.d(TAG, "Device: ${device.friendlyName}, Status: ${device.status?.name}")
                }

                if (knownDevices.isNotEmpty()) {
                    Log.d(TAG, "Posting ${knownDevices.size} devices to LiveData")
                    _devices.postValue(knownDevices)
                    _emptyState.postValue(null)

                    // Register for device status updates
                    registerForDeviceEvents(knownDevices)

                    // Auto-launch logic
                    if (tryAutoLaunch && !autoLaunchAttempted) {
                        autoLaunchAttempted = true
                        val deviceToLaunch = getDeviceForAutoLaunch(knownDevices)

                        if (deviceToLaunch != null) {
                            Log.d(TAG, "Auto-launching device: ${deviceToLaunch.friendlyName}")
                            _autoLaunchDevice.postValue(deviceToLaunch)

                            // Check if we should also launch camera
                            if (prefs.getBoolean(KEY_AUTO_LAUNCH_CAMERA, false)) {
                                _launchCamera.postValue(true)
                            }
                        } else {
                            Log.d(TAG, "No suitable device found for auto-launch")
                        }
                    }
                } else {
                    _devices.postValue(emptyList())
                    _emptyState.postValue("No devices")
                }
            } catch (e: InvalidStateException) {
                Log.e(TAG, "Error loading devices - invalid state", e)
                _emptyState.postValue("Initialization error")
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (e: ServiceUnavailableException) {
                Log.e(TAG, "Error loading devices - service unavailable", e)
                _emptyState.postValue("Service unavailable")
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    /**
     * Register for device status events
     */
    private fun registerForDeviceEvents(devices: List<IQDevice>) {
        devices.forEach { device ->
            try {
                connectIQ.registerForDeviceEvents(device) { dev, status ->
                    Log.d(TAG, "Device ${dev.friendlyName} status changed: ${status.name}")
                    AnalyticsUtils.logDeviceConnection(dev.friendlyName, status.name)

                    // Update ONLY the status in the existing device list
                    // Don't replace the device object, as 'dev' from callback may be incomplete
                    val updatedDevices = _devices.value?.map { existingDevice ->
                        if (existingDevice.deviceIdentifier == dev.deviceIdentifier) {
                            existingDevice.apply { this.status = status }
                        } else {
                            existingDevice
                        }
                    }

                    if (updatedDevices != null) {
                        Log.d(TAG, "Posting updated device list with ${updatedDevices.size} devices")
                        _devices.postValue(updatedDevices)
                    } else {
                        Log.w(TAG, "Device list was null when trying to update status")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering for device events: ${device.friendlyName}", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    /**
     * Unregister from all device events
     */
    fun unregisterAllDeviceEvents() {
        try {
            connectIQ.unregisterAllForEvents()
            Log.d(TAG, "Unregistered from all device events")
        } catch (e: InvalidStateException) {
            Log.w(TAG, "Error unregistering from device events", e)
        }
    }

    /**
     * Shutdown the ConnectIQ SDK
     */
    fun shutdownConnectIQSdk(context: Context) {
        try {
            unregisterAllDeviceEvents()
            connectIQ.shutdown(context)
            Log.d(TAG, "ConnectIQ SDK shutdown")
        } catch (e: InvalidStateException) {
            Log.w(TAG, "ConnectIQ SDK already in invalid state during shutdown", e)
        } catch (e: IllegalArgumentException) {
            // Receiver was never registered (e.g. SDK not fully initialized) or already unregistered
            Log.w(TAG, "ConnectIQ SDK receiver not registered during shutdown — already shut down?", e)
        }
    }

    /**
     * Set or remove a device as the preferred device
     */
    fun setPreferredDevice(device: IQDevice, isPreferred: Boolean) {
        val deviceId = device.deviceIdentifier.toString()

        if (isPreferred) {
            prefs.edit().putString(KEY_PREFERRED_DEVICE_ID, deviceId).apply()
            _preferredDeviceId.value = deviceId
            _toastMessage.value = "${device.friendlyName ?: deviceId} set as default device"
            Log.d(TAG, "Set default device: ${device.friendlyName} (${deviceId})")

            AnalyticsUtils.logDefaultDeviceSet(
                device.friendlyName ?: deviceId,
                deviceId,
                true
            )
        } else {
            prefs.edit().remove(KEY_PREFERRED_DEVICE_ID).apply()
            _preferredDeviceId.value = null
            _toastMessage.value = "${device.friendlyName ?: deviceId} removed as default device"
            Log.d(TAG, "Removed default device: ${device.friendlyName}")

            AnalyticsUtils.logDefaultDeviceSet(
                device.friendlyName ?: deviceId,
                deviceId,
                false
            )
        }
    }

    /**
     * Get the preferred device ID
     */
    fun getPreferredDeviceId(): String? {
        return prefs.getString(KEY_PREFERRED_DEVICE_ID, null)
    }

    /**
     * Determine which device should be auto-launched
     */
    private fun getDeviceForAutoLaunch(devices: List<IQDevice>): IQDevice? {
        if (devices.isEmpty()) return null

        val preferredDeviceId = prefs.getString(KEY_PREFERRED_DEVICE_ID, null)
        val preferredDevice = preferredDeviceId?.let { deviceId ->
            devices.find { it.deviceIdentifier.toString() == deviceId }
        }

        // Try preferred device if connected
        preferredDevice?.let { device ->
            if (device.status == IQDevice.IQDeviceStatus.CONNECTED) {
                Log.d(TAG, "Auto-launching preferred device: ${device.friendlyName}")
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

        // Fallback to first connected device
        val firstConnectedDevice = devices.find { it.status == IQDevice.IQDeviceStatus.CONNECTED }
        if (firstConnectedDevice != null) {
            Log.d(TAG, "Auto-launching first connected device: ${firstConnectedDevice.friendlyName}")
            AnalyticsUtils.logAutoLaunchAttempt(
                preferredDeviceName = preferredDevice?.friendlyName,
                actualDeviceName = firstConnectedDevice.friendlyName,
                usedFallback = true,
                success = true
            )
            return firstConnectedDevice
        }

        Log.d(TAG, "No connected devices available for auto-launch")
        AnalyticsUtils.logAutoLaunchAttempt(
            preferredDeviceName = preferredDevice?.friendlyName,
            actualDeviceName = null,
            usedFallback = false,
            success = false
        )

        return null
    }

    /**
     * Track app launch and log analytics
     */
    fun trackAppLaunch(context: Context, isFirstLaunch: Boolean) {
        val lastSessionEnd = prefs.getLong("last_session_end", 0)
        val timeSinceLastSession = if (lastSessionEnd > 0) {
            System.currentTimeMillis() - lastSessionEnd
        } else null

        AnalyticsUtils.logAppOpen(context, isFirstLaunch)
        AnalyticsUtils.logUserEngagement(0, timeSinceLastSession)

        // Track for review prompt
        InAppReviewUtils.trackAppLaunch(context)
    }

    /**
     * Log session duration on destroy
     */
    fun logSessionEnd() {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        AnalyticsUtils.logUserEngagement(sessionDuration)
        prefs.edit().putLong("last_session_end", System.currentTimeMillis()).apply()
    }

    /**
     * Check timing triggers for review and developer notes
     */
    fun checkTimingTriggers(context: Context) {
        viewModelScope.launch {
            try {
                val shouldShowReview = InAppReviewUtils.shouldShowReviewPrompt(context)
                val shouldShowDeveloperNote = DeveloperNoteManager.shouldShowPopup(context)

                Log.d(TAG, "Timing check - Review: $shouldShowReview, Developer Note: $shouldShowDeveloperNote")

                if (shouldShowReview) {
                    Log.d(TAG, "Showing review prompt (priority over developer note)")
                    _showReviewPrompt.postValue(true)

                    // Defer developer note if both should show
                    if (shouldShowDeveloperNote) {
                        deferDeveloperNoteToNextLaunch(context)
                    }
                } else if (shouldShowDeveloperNote) {
                    Log.d(TAG, "Showing developer note popup")
                    val currentVersion = DeveloperNoteManager.getCurrentVersionIndex(context)
                    _showDeveloperNote.postValue(DeveloperNoteEvent(currentVersion, true))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking timing triggers", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    /**
     * Mark that review prompt was shown
     */
    fun onReviewPromptShown() {
        _showReviewPrompt.value = false
    }

    /**
     * Mark that developer note was shown
     */
    fun onDeveloperNoteShown() {
        _showDeveloperNote.value = null
    }

    /**
     * Show developer note from button (user-initiated)
     */
    fun showDeveloperNoteFromButton(context: Context) {
        try {
            val currentVersion = DeveloperNoteManager.getCurrentVersionIndex(context)
            val versionToShow = if (currentVersion == -1) 0 else currentVersion

            Log.d(TAG, "Showing developer note from button - version $versionToShow")
            _showDeveloperNote.postValue(DeveloperNoteEvent(versionToShow, false))

            AnalyticsUtils.logFeatureUsage("developer_note", "main_button_clicked", true)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing developer note from button", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            _toastMessage.postValue("Unable to show developer information")
        }
    }

    /**
     * Update developer note button visibility
     * Changed: Show AFTER popup has been shown (not just 5 days)
     */
    private fun updateDeveloperNoteButtonVisibility() {
        try {
            val popupShown = DeveloperNoteManager.isPopupShown(getApplication())
            _showDeveloperNoteButton.postValue(popupShown)
            Log.d(TAG, "Developer note button visibility: $popupShown (popup shown: $popupShown)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating developer note button visibility", e)
            _showDeveloperNoteButton.postValue(false)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Defer developer note to next launch
     */
    private fun deferDeveloperNoteToNextLaunch(context: Context) {
        try {
            Log.d(TAG, "Deferring developer note to next app launch to avoid overwhelming user")
            DeveloperNoteManager.deferToNextLaunch(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error deferring developer note", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Mark auto-launch as consumed
     */
    fun onAutoLaunchConsumed() {
        _autoLaunchDevice.value = null
    }

    /**
     * Mark launch camera as consumed
     */
    fun onLaunchCameraConsumed() {
        _launchCamera.value = false
    }
}

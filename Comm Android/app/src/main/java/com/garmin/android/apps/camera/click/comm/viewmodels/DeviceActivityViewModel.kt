package com.garmin.android.apps.camera.click.comm.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.garmin.android.apps.camera.click.comm.CommConstants
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils
import com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.detection.ButtonLocationRepository
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for DeviceActivity that manages all business logic and state.
 *
 * Responsibilities:
 * - ConnectIQ device connection and status management
 * - Companion app status monitoring and control
 * - Message sending to watch
 * - Permission state tracking
 * - Auto-launch camera preference
 * - Review and developer note timing logic
 * - Analytics and error logging
 *
 * This ViewModel follows MVVM architecture to separate UI from business logic.
 */
@HiltViewModel
class DeviceActivityViewModel @Inject constructor(
    application: Application,
    private val buttonLocationRepository: ButtonLocationRepository
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "DeviceActivityViewModel"
        const val PREFS_NAME = "CameraClickPrefs"
        const val KEY_AUTO_LAUNCH_CAMERA = "auto_launch_camera"
    }

    // ConnectIQ instances
    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private var myApp: IQApp? = null

    // SharedPreferences
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Analytics
    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(application)
    }

    // LiveData for UI state
    private val _deviceStatus = MutableLiveData<IQDevice.IQDeviceStatus>()
    val deviceStatus: LiveData<IQDevice.IQDeviceStatus> = _deviceStatus

    private val _appStatus = MutableLiveData<AppStatus>()
    val appStatus: LiveData<AppStatus> = _appStatus

    private val _autoLaunchEnabled = MutableLiveData<Boolean>()
    val autoLaunchEnabled: LiveData<Boolean> = _autoLaunchEnabled

    private val _messageSendResult = MutableLiveData<MessageSendResult>()
    val messageSendResult: LiveData<MessageSendResult> = _messageSendResult

    private val _showAccessibilityDialog = MutableLiveData<Boolean>()
    val showAccessibilityDialog: LiveData<Boolean> = _showAccessibilityDialog

    private val _showAppNotInstalledDialog = MutableLiveData<Boolean>()
    val showAppNotInstalledDialog: LiveData<Boolean> = _showAppNotInstalledDialog

    private val _showReviewPrompt = MutableLiveData<Boolean>()
    val showReviewPrompt: LiveData<Boolean> = _showReviewPrompt

    private val _showDeveloperNote = MutableLiveData<DeveloperNoteEvent?>()
    val showDeveloperNote: LiveData<DeveloperNoteEvent?> = _showDeveloperNote

    private val _shouldShowRateAppMenu = MutableLiveData<Boolean>()
    val shouldShowRateAppMenu: LiveData<Boolean> = _shouldShowRateAppMenu

    private val _buttonLocationInfo = MutableLiveData<ShutterButtonInfo?>()
    val buttonLocationInfo: LiveData<ShutterButtonInfo?> = _buttonLocationInfo

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _startMessageService = MutableLiveData<IQDevice>()
    val startMessageService: LiveData<IQDevice> = _startMessageService

    // Current device reference
    private var currentDevice: IQDevice? = null

    /**
     * Data classes for UI events
     */
    data class MessageSendResult(
        val success: Boolean,
        val status: String,
        val message: String
    )

    data class AppStatus(
        val isOpen: Boolean,
        val displayText: String
    )

    data class DeveloperNoteEvent(
        val version: Int,
        val isAutomatic: Boolean
    )

    /**
     * Initialize the ViewModel with a device
     */
    fun initializeWithDevice(device: IQDevice) {
        Log.d(TAG, "Initializing ViewModel with device: ${device.friendlyName}")
        currentDevice = device
        myApp = IQApp(CommConstants.COMM_WATCH_ID)

        // Migrate existing users to new popup system
        DeveloperNoteManager.migrateExistingUsers(getApplication())

        // Load initial state
        _deviceStatus.value = device.status
        _autoLaunchEnabled.value = prefs.getBoolean(KEY_AUTO_LAUNCH_CAMERA, false)
        _appStatus.value = AppStatus(isOpen = false, displayText = "")

        // Load button location info
        updateButtonLocationInfo()

        // Log analytics
        logDeviceScreenView(device)

        // Check rate app menu visibility
        updateRateAppMenuVisibility()
    }

    /**
     * Register for device status updates
     */
    fun registerForDeviceEvents() {
        Log.d(TAG, "Registering for device events")
        currentDevice?.let { dev ->
            try {
                connectIQ.registerForDeviceEvents(dev) { _, status ->
                    Log.d(TAG, "Device status changed: ${status.name}")
                    _deviceStatus.postValue(status)
                }
            } catch (e: InvalidStateException) {
                Log.e(TAG, "Error registering for device events", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    /**
     * Unregister from device events
     */
    fun unregisterFromDeviceEvents() {
        Log.d(TAG, "Unregistering from device events")
        currentDevice?.let { dev ->
            try {
                connectIQ.unregisterForDeviceEvents(dev)
            } catch (_: InvalidStateException) {
                Log.e(TAG, "Error unregistering for device events")
            }
        }
    }

    /**
     * Open the companion app on the watch
     */
    fun openWatchApp() {
        Log.d(TAG, "Opening app on device")
        _toastMessage.value = "Prompting watch..."

        currentDevice?.let { dev ->
            myApp?.let { app ->
                // Log analytics
                AnalyticsUtils.logWatchAppOpen(
                    deviceName = dev.friendlyName,
                    deviceId = dev.deviceIdentifier.toString(),
                    status = dev.status?.name ?: "unknown"
                )

                try {
                    connectIQ.openApplication(dev, app) { _, _, status ->
                        Log.d(TAG, "App status changed: ${status.name}")
                        _toastMessage.postValue("App Status: ${status.name}")

                        val isOpen = status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING
                        _appStatus.postValue(
                            AppStatus(
                                isOpen = isOpen,
                                displayText = if (isOpen) "App Already Open" else "Tap to Open Watch App"
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening app", e)
                    AnalyticsUtils.logWatchAppOpen(
                        deviceName = dev.friendlyName,
                        deviceId = dev.deviceIdentifier.toString(),
                        status = "error: ${e.message}"
                    )
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    /**
     * Send a message to the watch
     */
    fun sendMessageToWatch(message: String) {
        Log.d(TAG, "Sending message: $message")
        currentDevice?.let { dev ->
            myApp?.let { app ->
                try {
                    connectIQ.sendMessage(dev, app, message) { _, _, status ->
                        Log.d(TAG, "Message send status: ${status.name}")

                        val result = MessageSendResult(
                            success = status == ConnectIQ.IQMessageStatus.SUCCESS,
                            status = status.name,
                            message = if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                                "✓ Message sent successfully!"
                            } else {
                                "❌ ${status.name}"
                            }
                        )
                        _messageSendResult.postValue(result)

                        // Log analytics
                        val params = Bundle().apply {
                            putString("device_name", dev.friendlyName)
                            putString("message", message)
                            putString("status", status.name)
                        }
                        firebaseAnalytics.logEvent("message_sent_to_watch", params)
                    }
                } catch (e: InvalidStateException) {
                    Log.e(TAG, "Error sending message", e)
                    _messageSendResult.postValue(
                        MessageSendResult(
                            success = false,
                            status = "ERROR",
                            message = "❌ ConnectIQ is not in a valid state"
                        )
                    )
                    FirebaseCrashlytics.getInstance().recordException(e)
                } catch (e: ServiceUnavailableException) {
                    Log.e(TAG, "Service unavailable", e)
                    _messageSendResult.postValue(
                        MessageSendResult(
                            success = false,
                            status = "ERROR",
                            message = "❌ ConnectIQ service is unavailable. Is Garmin Connect Mobile installed and running?"
                        )
                    )
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        } ?: run {
            Log.e(TAG, "Device or app is null, cannot send message")
            _messageSendResult.postValue(
                MessageSendResult(
                    success = false,
                    status = "ERROR",
                    message = "❌ Device not available"
                )
            )
        }
    }

    /**
     * Check the watch app installation status and start MessageService
     */
    fun checkWatchAppStatus() {
        Log.d(TAG, "Checking app status")
        currentDevice?.let { dev ->
            // Start MessageService immediately as primary path
            // The app verification is nice-to-have but not essential for functionality
            Log.d(TAG, "Starting MessageService (primary path)")
            _startMessageService.postValue(dev)

            myApp?.let { app ->
                // Still attempt app verification for informational purposes
                try {
                    Log.d(TAG, "Attempting app verification for app ID: ${CommConstants.COMM_WATCH_ID}")
                    connectIQ.getApplicationInfo(CommConstants.COMM_WATCH_ID, dev, object :
                        ConnectIQ.IQApplicationInfoListener {
                        override fun onApplicationInfoReceived(receivedApp: IQApp) {
                            Log.d(TAG, "App verification: Watch app IS installed on ${dev.friendlyName}")
                            FirebaseCrashlytics.getInstance().log("Watch app verified installed on ${dev.friendlyName}")
                        }

                        override fun onApplicationNotInstalled(applicationId: String) {
                            Log.w(TAG, "App verification: Watch app NOT installed on device")
                            FirebaseCrashlytics.getInstance().log("Watch app not installed on ${dev.friendlyName}")
                            _showAppNotInstalledDialog.postValue(true)
                        }
                    })
                    Log.d(TAG, "App verification request sent")
                } catch (e: InvalidStateException) {
                    Log.w(TAG, "ConnectIQ SDK not ready for app verification", e)
                    FirebaseCrashlytics.getInstance().log("ConnectIQ SDK not ready: ${e.message}")
                } catch (e: ServiceUnavailableException) {
                    Log.w(TAG, "ConnectIQ service unavailable for app verification", e)
                    FirebaseCrashlytics.getInstance().log("ConnectIQ service unavailable: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error during app verification", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        } ?: run {
            Log.e(TAG, "Device is null, cannot check app status or start service")
        }
    }

    /**
     * Update auto-launch camera preference
     */
    fun setAutoLaunchCamera(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LAUNCH_CAMERA, enabled).apply()
        _autoLaunchEnabled.value = enabled
        AnalyticsUtils.logFeatureUsage("auto_launch_camera", "toggle", enabled)
        Log.d(TAG, "Auto-launch camera set to: $enabled")
    }

    /**
     * Get the current device
     */
    fun getCurrentDevice(): IQDevice? = currentDevice

    /**
     * Track app launch for review timing
     */
    fun trackAppLaunch(context: Context) {
        InAppReviewUtils.trackAppLaunch(context)
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
     * Mark that app not installed dialog was shown
     */
    fun onAppNotInstalledDialogShown() {
        _showAppNotInstalledDialog.value = false
    }

    /**
     * Update rate app menu visibility
     * Changed: Show AFTER popup has been shown (not just 5 days)
     */
    fun updateRateAppMenuVisibility() {
        val popupShown = DeveloperNoteManager.isPopupShown(getApplication())
        _shouldShowRateAppMenu.value = popupShown

        Log.d(TAG, "Rate app menu visibility: $popupShown (popup shown: $popupShown)")

        // Track when rate app button becomes available for the first time
        if (popupShown) {
            AnalyticsUtils.logFeatureUsage("rate_app", "button_available", true)
        }
    }

    /**
     * Update button location overlay info
     */
    private fun updateButtonLocationInfo() {
        val buttonInfo = buttonLocationRepository.getLastKnownButtonInfo()
        _buttonLocationInfo.value = buttonInfo
    }

    /**
     * Refresh button location info (call this after button detection changes)
     */
    fun refreshButtonLocationInfo() {
        updateButtonLocationInfo()
    }

    /**
     * Log device screen view analytics
     */
    private fun logDeviceScreenView(device: IQDevice) {
        val bundle = Bundle().apply {
            putString("device_name", device.friendlyName ?: "unknown")
            putString("device_id", device.deviceIdentifier?.toString() ?: "unknown")
            putString("connection_status", device.status?.name ?: "unknown")
        }
        AnalyticsUtils.logScreenView("device_activity", "DeviceActivity", bundle)
    }

    /**
     * Defer developer note to next launch to avoid overwhelming user
     */
    private fun deferDeveloperNoteToNextLaunch(context: Context) {
        try {
            Log.d(TAG, "Deferring developer note to next app launch to avoid overwhelming user")

            DeveloperNoteManager.deferToNextLaunch(context)

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
}

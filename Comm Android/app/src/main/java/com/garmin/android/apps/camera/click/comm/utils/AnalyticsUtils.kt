package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

private const val TAG = "AnalyticsUtils"

object AnalyticsUtils {
    @Volatile
    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * Initialize AnalyticsUtils. Prefer passing a FirebaseAnalytics instance from Application
     * but keeping the old context-only signature for backward compatibility.
     */
    @JvmOverloads
    fun initialize(context: Context, analytics: FirebaseAnalytics? = null) {
        if (firebaseAnalytics != null) return
        synchronized(this) {
            if (firebaseAnalytics == null) {
                firebaseAnalytics = analytics ?: Firebase.analytics
                firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
            }
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        if (eventName.isBlank()) {
            Log.w(TAG, "Attempted to log event with blank name")
            return
        }
        
        firebaseAnalytics?.let { analytics ->
            try {
                analytics.logEvent(eventName, params)
                Log.d(TAG, "Successfully logged event: $eventName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log event: $eventName", e)
            }
        } ?: Log.e(TAG, "Firebase Analytics not initialized")
    }

    /**
     * Set user properties for better segmentation and analysis
     */
    fun setUserProperties(context: Context) {
        firebaseAnalytics?.let { analytics ->
            try {
                analytics.setUserProperty("app_version", getAppVersion(context))
                analytics.setUserProperty("device_model", android.os.Build.MODEL)
                analytics.setUserProperty("android_version", android.os.Build.VERSION.RELEASE)
                Log.d(TAG, "User properties set successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set user properties", e)
            }
        }
    }

    /**
     * Set user property for preferred camera detection method
     */
    fun setPreferredDetectionMethod(method: String) {
        firebaseAnalytics?.setUserProperty("preferred_detection_method", method)
    }

    /**
     * Set user property for accessibility service status
     */
    fun setAccessibilityServiceEnabled(enabled: Boolean) {
        firebaseAnalytics?.setUserProperty("accessibility_enabled", enabled.toString())
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    // Simplified common event methods
    /**
     * Log a simple button click event
     */
    fun logButtonClick(buttonType: String, success: Boolean = true) {
        val params = Bundle().apply {
            putString("button_type", buttonType)
            putBoolean("success", success)
        }
        logEvent("button_clicked", params)
    }

    /**
     * Log feature usage with optional method parameter
     */
    fun logFeatureUsed(feature: String, method: String? = null, success: Boolean = true) {
        val params = Bundle().apply {
            putString("feature", feature)
            putBoolean("success", success)
            method?.let { putString("method", it) }
        }
        logEvent("feature_used", params)
    }

    /**
     * Log comprehensive button detection attempt data
     */
    fun logButtonDetectionAttempt(
        packageName: String,
        detectionMethod: String,
        success: Boolean,
        buttonInfo: AccessibilityNodeInfo? = null,
        validationScore: Int? = null,
        detectionTimeMs: Long? = null,
        screenOrientation: Int = 0,
        deviceInfo: String? = null
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putString("detection_method", detectionMethod)
            putBoolean("success", success)
            putInt("screen_orientation", screenOrientation)
            deviceInfo?.let { putString("device_info", it) }
            detectionTimeMs?.let { putLong("detection_time_ms", it) }
            validationScore?.let { putInt("validation_score", it) }
            
            // Button details if available
            buttonInfo?.let { button ->
                putString("button_resource_id", button.viewIdResourceName ?: "null")
                putString("button_content_description", button.contentDescription?.toString() ?: "null")
                putString("button_class_name", button.className?.toString() ?: "null")
                putString("button_text", button.text?.toString() ?: "null")
                putBoolean("button_clickable", button.isClickable)
                putBoolean("button_enabled", button.isEnabled)
                putBoolean("button_focused", button.isFocused)
                putBoolean("button_selected", button.isSelected)
                putInt("button_child_count", button.childCount)
                
                // Button bounds
                val bounds = Rect()
                button.getBoundsInScreen(bounds)
                putInt("button_width", bounds.width())
                putInt("button_height", bounds.height())
                putInt("button_center_x", bounds.centerX())
                putInt("button_center_y", bounds.centerY())
                putInt("button_left", bounds.left)
                putInt("button_top", bounds.top)
                putInt("button_right", bounds.right)
                putInt("button_bottom", bounds.bottom)
                
                // Calculate additional metrics
                val area = bounds.width() * bounds.height()
                putInt("button_area", area)
                putFloat("button_aspect_ratio", bounds.width().toFloat() / bounds.height())
                putBoolean("button_is_square", bounds.width() == bounds.height())
            }
        }
        
        logEvent("button_detection_attempt", params)
    }

    /**
     * Log button validation results
     */
    fun logButtonValidation(
        packageName: String,
        buttonInfo: AccessibilityNodeInfo,
        validationResult: CameraDetectionUtils.ValidationResult,
        detectionMethod: String
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putString("detection_method", detectionMethod)
            putBoolean("validation_passed", validationResult.isValid)
            putInt("validation_score", validationResult.score)
            putString("validation_reasons", validationResult.reasons.joinToString("|"))
            
            // Button details
            putString("button_resource_id", buttonInfo.viewIdResourceName ?: "null")
            putString("button_content_description", buttonInfo.contentDescription?.toString() ?: "null")
            putString("button_class_name", buttonInfo.className?.toString() ?: "null")
            
            // Button bounds
            val bounds = Rect()
            buttonInfo.getBoundsInScreen(bounds)
            putInt("button_width", bounds.width())
            putInt("button_height", bounds.height())
            putInt("button_center_x", bounds.centerX())
            putInt("button_center_y", bounds.centerY())
        }
        
        logEvent("button_validation_result", params)
    }

    /**
     * Log shutter button click attempt
     */
    fun logShutterButtonClick(
        packageName: String,
        buttonInfo: AccessibilityNodeInfo,
        clickMethod: String,
        success: Boolean,
        responseTimeMs: Long? = null,
        deviceName: String? = null
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putString("click_method", clickMethod)
            putBoolean("success", success)
            deviceName?.let { putString("device_name", it) }
            responseTimeMs?.let { putLong("response_time_ms", it) }
            
            // Button details
            putString("button_resource_id", buttonInfo.viewIdResourceName ?: "null")
            putString("button_content_description", buttonInfo.contentDescription?.toString() ?: "null")
            putString("button_class_name", buttonInfo.className?.toString() ?: "null")
            
            // Button bounds
            val bounds = Rect()
            buttonInfo.getBoundsInScreen(bounds)
            putInt("button_width", bounds.width())
            putInt("button_height", bounds.height())
            putInt("button_center_x", bounds.centerX())
            putInt("button_center_y", bounds.centerY())
        }
        
        logEvent("shutter_button_click", params)
    }

    /**
     * Log detection strategy performance
     */
    fun logDetectionStrategyPerformance(
        packageName: String,
        strategyName: String,
        success: Boolean,
        detectionTimeMs: Long,
        candidatesFound: Int,
        bestCandidateScore: Int? = null
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putString("strategy_name", strategyName)
            putBoolean("success", success)
            putLong("detection_time_ms", detectionTimeMs)
            putInt("candidates_found", candidatesFound)
            bestCandidateScore?.let { putInt("best_candidate_score", it) }
        }
        
        logEvent("detection_strategy_performance", params)
    }

    /**
     * Log comprehensive detection session summary
     */
    fun logDetectionSessionSummary(
        packageName: String,
        successfulMethod: String?,
        totalAttempts: Int,
        totalTimeMs: Long,
        strategiesTried: List<String>,
        finalButtonInfo: ShutterButtonInfo? = null,
        deviceInfo: String? = null
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putString("successful_method", successfulMethod ?: "none")
            putInt("total_attempts", totalAttempts)
            putLong("total_time_ms", totalTimeMs)
            putString("strategies_tried", strategiesTried.joinToString("|"))
            deviceInfo?.let { putString("device_info", it) }
            
            // Final button details if available
            finalButtonInfo?.let { info ->
                putString("final_button_resource_id", info.resourceId ?: "null")
                putString("final_button_content_description", info.contentDescription ?: "null")
                putString("final_button_class_name", info.className ?: "null")
                putInt("final_button_confidence_score", info.confidenceScore)
                putString("final_button_detection_method", info.detectionMethod ?: "unknown")
                putString("final_button_size", info.buttonSize ?: "unknown")
                putString("final_button_position", info.positionOnScreen ?: "unknown")
                putBoolean("final_button_is_square", info.isSquare)
            }
        }
        
        logEvent("detection_session_summary", params)
    }

    /**
     * Log camera app characteristics
     */
    fun logCameraAppCharacteristics(
        packageName: String,
        isKnownCameraApp: Boolean,
        totalClickableNodes: Int,
        screenWidth: Int,
        screenHeight: Int,
        screenOrientation: Int,
        deviceInfo: String? = null
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putBoolean("is_known_camera_app", isKnownCameraApp)
            putInt("total_clickable_nodes", totalClickableNodes)
            putInt("screen_width", screenWidth)
            putInt("screen_height", screenHeight)
            putInt("screen_orientation", screenOrientation)
            deviceInfo?.let { putString("device_info", it) }
        }
        
        logEvent("camera_app_characteristics", params)
    }

    /**
     * Log detection failure analysis
     */
    fun logDetectionFailure(
        packageName: String,
        strategiesTried: List<String>,
        failureReason: String,
        screenCharacteristics: String? = null,
        deviceInfo: String? = null
    ) {
        val params = Bundle().apply {
            putString("camera_package", packageName)
            putString("strategies_tried", strategiesTried.joinToString("|"))
            putString("failure_reason", failureReason)
            screenCharacteristics?.let { putString("screen_characteristics", it) }
            deviceInfo?.let { putString("device_info", it) }
        }
        
        logEvent("detection_failure_analysis", params)
    }

    fun logAppOpen(context: Context, isColdStart: Boolean) {
        val params = Bundle().apply {
            putString("launch_type", if (isColdStart) "cold_start" else "warm_start")
        }
        logEvent(FirebaseAnalytics.Event.APP_OPEN, params)
    }

    fun logCameraCommand(deviceName: String, cameraPackage: String, serviceState: String) {
        val params = Bundle().apply {
            putString("device_name", deviceName)
            putString("camera_package", cameraPackage)
            putString("service_state", serviceState)
        }
        logEvent("camera_command_received", params)
    }

    fun logCameraLaunch(success: Boolean, packageName: String? = null) {
        val params = Bundle().apply {
            putBoolean("success", success)
            packageName?.let { putString("camera_package", it) }
        }
        logEvent("camera_launch_attempt", params)
    }

    fun logDeviceConnection(deviceName: String, status: String) {
        val params = Bundle().apply {
            putString("device_name", deviceName)
            putString("connection_status", status)
        }
        logEvent("device_connection_changed", params)
    }

    fun logWatchAppOpen(deviceName: String, deviceId: String, status: String) {
        val params = Bundle().apply {
            putString("device_name", deviceName)
            putString("device_id", deviceId)
            putString("status", status)
        }
        logEvent("watch_app_open_attempt", params)
    }

    fun logScreenView(screenName: String, screenClass: String, params: Bundle? = null) {
        val bundle = params ?: Bundle()
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun logUserEngagement(sessionDuration: Long, timeSinceLastSession: Long? = null) {
        val params = Bundle().apply {
            putLong("session_duration_ms", sessionDuration)
            timeSinceLastSession?.let { putLong("time_since_last_session_ms", it) }
        }
        logEvent("app_session_engagement", params)
    }

    fun logFeatureUsage(featureName: String, action: String, success: Boolean) {
        val params = Bundle().apply {
            putString("feature", featureName)
            putString("action", action)
            putBoolean("success", success)
        }
        logEvent("feature_used", params)
    }

    fun logError(errorType: String, errorMessage: String, context: String) {
        val params = Bundle().apply {
            putString("error_type", errorType)
            putString("error_message", errorMessage)
            putString("context", context)
        }
        logEvent("app_error", params)
    }

    fun logPermissionState(permission: String, granted: Boolean) {
        val params = Bundle().apply {
            putString("permission", permission)
            putBoolean("granted", granted)
        }
        logEvent("permission_state", params)
    }

    fun logServiceState(serviceName: String, state: String) {
        val params = Bundle().apply {
            putString("service_name", serviceName)
            putString("state", state)
        }
        logEvent("service_state", params)
    }

    /**
     * Log when a user sets or removes a default device
     */
    fun logDefaultDeviceSet(deviceName: String, deviceId: String, isSet: Boolean) {
        val params = Bundle().apply {
            putString("device_name", deviceName)
            putString("device_id", deviceId)
            putBoolean("is_set", isSet)
            putString("action", if (isSet) "set" else "remove")
        }
        logEvent("default_device_changed", params)
    }

    /**
     * Log auto-launch attempts with details about success/failure
     */
    fun logAutoLaunchAttempt(
        preferredDeviceName: String?,
        actualDeviceName: String?,
        usedFallback: Boolean,
        success: Boolean
    ) {
        val params = Bundle().apply {
            putBoolean("success", success)
            putBoolean("used_fallback", usedFallback)
            preferredDeviceName?.let { putString("preferred_device", it) }
            actualDeviceName?.let { putString("launched_device", it) }
        }
        logEvent("auto_launch_attempt", params)
    }
} 
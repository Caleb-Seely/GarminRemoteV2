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
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = Firebase.analytics.apply {
                setAnalyticsCollectionEnabled(true)
                
            }
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        firebaseAnalytics?.let { analytics ->
            try {
                analytics.logEvent(eventName, params)
                Log.d(TAG, "Successfully logged event: $eventName with params: $params")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log event: $eventName", e)
            }
        } ?: Log.e(TAG, "Firebase Analytics not initialized")
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
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("timestamp", System.currentTimeMillis().toString())
            
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
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("timestamp", System.currentTimeMillis().toString())
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
        logEvent("device_connection", params)
    }

    fun logWatchAppOpen(deviceName: String, deviceId: String, status: String) {
        val params = Bundle().apply {
            putString("device_name", deviceName)
            putString("device_id", deviceId)
            putString("status", status)
            putString("timestamp", System.currentTimeMillis().toString())
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
            putString("feature_name", featureName)
            putString("action", action)
            putBoolean("success", success)
            putString("timestamp", System.currentTimeMillis().toString())
        }
        logEvent("feature_usage", params)
    }

    fun logError(errorType: String, errorMessage: String, context: String) {
        val params = Bundle().apply {
            putString("error_type", errorType)
            putString("error_message", errorMessage)
            putString("context", context)
            putString("timestamp", System.currentTimeMillis().toString())
        }
        logEvent("app_error", params)
    }

    fun logPermissionState(permission: String, granted: Boolean) {
        val params = Bundle().apply {
            putString("permission", permission)
            putBoolean("granted", granted)
            putString("timestamp", System.currentTimeMillis().toString())
        }
        logEvent("permission_state", params)
    }

    fun logServiceState(serviceName: String, state: String) {
        val params = Bundle().apply {
            putString("service_name", serviceName)
            putString("state", state)
            putString("timestamp", System.currentTimeMillis().toString())
        }
        logEvent("service_state", params)
    }
} 
package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

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
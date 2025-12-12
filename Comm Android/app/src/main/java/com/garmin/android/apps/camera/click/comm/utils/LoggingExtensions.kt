package com.garmin.android.apps.camera.click.comm.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Extension functions to consolidate common logging patterns.
 *
 * These extensions combine FirebaseCrashlytics and Analytics logging
 * to reduce code duplication throughout the app.
 */

/**
 * Log a message to both Crashlytics and as a feature usage event.
 * This is the most common logging pattern in the app.
 *
 * @param message The message to log to Crashlytics
 * @param feature The feature name for analytics
 * @param action The action name for analytics
 * @param value The value for analytics (default: true)
 */
fun logFeatureUsageWithCrashlytics(
    message: String,
    feature: String,
    action: String,
    value: Boolean = true
) {
    FirebaseCrashlytics.getInstance().log(message)
    AnalyticsUtils.logFeatureUsage(feature, action, value)
}

/**
 * Log a permission state to both Crashlytics and Analytics.
 *
 * @param permission The permission name
 * @param granted Whether the permission was granted
 */
fun logPermissionStateWithCrashlytics(permission: String, granted: Boolean) {
    val message = if (granted) {
        "$permission permission granted"
    } else {
        "$permission permission not granted"
    }
    FirebaseCrashlytics.getInstance().log(message)
    AnalyticsUtils.logPermissionState(permission, granted)
}

/**
 * Log a service state to both Crashlytics and Analytics.
 *
 * @param service The service name
 * @param state The service state (e.g., "enabled", "disabled")
 */
fun logServiceStateWithCrashlytics(service: String, state: String) {
    FirebaseCrashlytics.getInstance().log("$service service $state")
    AnalyticsUtils.logServiceState(service, state)
}

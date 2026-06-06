package com.garmin.android.apps.camera.click.comm.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import android.content.pm.ApplicationInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.R

/**
 * Simple utility for handling Google Play In-App Reviews
 * Follows best practices for when and how to show review prompts
 */
object InAppReviewUtils {
    
    private const val PREFS_NAME = "in_app_review_prefs"
    private const val KEY_FIRST_INSTALL_DATE = "first_install_date"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LAST_REVIEW_REQUEST = "last_review_request"
    private const val KEY_REVIEW_COMPLETED = "review_completed"
    private const val KEY_REVIEW_SHOW_COUNT = "review_show_count"
    private const val KEY_REVIEW_DELAY_AFTER_DEVELOPER_NOTE = "review_delay_after_developer_note"

    // Production thresholds
    private const val INITIAL_DELAY_DAYS = 5
    private const val DAYS_BETWEEN_REVIEW_REQUESTS = 30
    private const val MAX_REVIEW_SHOW_COUNT = 6
    
    /**
     * Call this method when your app launches to track usage
     */
    fun trackAppLaunch(context: Context) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        prefs.edit().putInt(KEY_LAUNCH_COUNT, currentCount + 1).apply()
        
        // Initialize first install date if not set
        initializeFirstInstallDateIfNeeded(prefs, context)
    }
    
    /**
     * Check if we should show the review prompt based on best practices
     * Now coordinated with developer note popup
     */
    fun shouldShowReviewPrompt(context: Context): Boolean {
        val prefs = getPrefs(context)

        // Don't show if user already completed a review
        val isCompleted = prefs.getBoolean(KEY_REVIEW_COMPLETED, false)
        if (isCompleted) {
            android.util.Log.d("InAppReviewUtils", "Review already completed, not showing")
            return false
        }

        // Don't show if we've shown too many times
        val showCount = prefs.getInt(KEY_REVIEW_SHOW_COUNT, 0)
        if (showCount >= MAX_REVIEW_SHOW_COUNT) {
            android.util.Log.d("InAppReviewUtils", "Review shown $showCount times (>= $MAX_REVIEW_SHOW_COUNT), not showing")
            prefs.edit().putBoolean(KEY_REVIEW_COMPLETED, true).apply()
            return false
        }

        // NEW: Check if developer note has been shown first
        if (!DeveloperNoteManager.isPopupShown(context)) {
            android.util.Log.d("InAppReviewUtils", "Developer note not shown yet, review deferred")
            return false
        }

        // Initialize first install date if not set
        initializeFirstInstallDateIfNeeded(prefs, context)

        val currentTime = System.currentTimeMillis()
        val firstInstallDate = prefs.getLong(KEY_FIRST_INSTALL_DATE, currentTime)
        val lastRequest = prefs.getLong(KEY_LAST_REVIEW_REQUEST, 0)

        // Check if INITIAL_DELAY_DAYS have passed since install
        val daysSinceInstall = (currentTime - firstInstallDate) / (1000 * 60 * 60 * 24)
        android.util.Log.d("InAppReviewUtils", "Days since install: $daysSinceInstall (need $INITIAL_DELAY_DAYS)")

        if (daysSinceInstall < INITIAL_DELAY_DAYS) {
            android.util.Log.d("InAppReviewUtils", "Not enough days since install")
            return false
        }

        // NEW: Enforce 1-3 day delay after developer note
        val developerNoteDate = DeveloperNoteManager.getPopupShownDate(context)
        val daysSinceDeveloperNote = (currentTime - developerNoteDate) / (1000 * 60 * 60 * 24)

        // Use random delay between 1-3 days (save in prefs for consistency)
        val targetDelay = prefs.getInt(KEY_REVIEW_DELAY_AFTER_DEVELOPER_NOTE, -1)
        if (targetDelay == -1) {
            val randomDelay = (1..3).random()
            prefs.edit().putInt(KEY_REVIEW_DELAY_AFTER_DEVELOPER_NOTE, randomDelay).apply()
            android.util.Log.d("InAppReviewUtils", "Set random review delay to $randomDelay days after developer note")
        }

        val actualDelay = prefs.getInt(KEY_REVIEW_DELAY_AFTER_DEVELOPER_NOTE, 1)
        if (daysSinceDeveloperNote < actualDelay) {
            android.util.Log.d("InAppReviewUtils", "Only $daysSinceDeveloperNote days since developer note, need $actualDelay")
            return false
        }

        // NEW: Enforce 24-hour gap from ANY popup
        val lastDeveloperNoteDate = DeveloperNoteManager.getPopupShownDate(context)
        val lastReviewDate = prefs.getLong(KEY_LAST_REVIEW_REQUEST, 0)
        val lastPopupDate = maxOf(lastDeveloperNoteDate, lastReviewDate)

        val hoursSinceLastPopup = (currentTime - lastPopupDate) / (1000 * 60 * 60)
        if (hoursSinceLastPopup < 24) {
            android.util.Log.d("InAppReviewUtils", "Only $hoursSinceLastPopup hours since last popup, need 24")
            return false
        }

        // If we've never shown a review request, show it now (after all checks pass)
        if (lastRequest == 0L) {
            android.util.Log.d("InAppReviewUtils", "First time showing review prompt (after developer note)")
            return true
        }

        // Check time since last request (DAYS_BETWEEN_REVIEW_REQUESTS between requests)
        val daysSinceLastRequest = if (lastRequest > 0) (currentTime - lastRequest) / (1000 * 60 * 60 * 24) else Long.MAX_VALUE
        android.util.Log.d("InAppReviewUtils", "Days since last request: $daysSinceLastRequest (need $DAYS_BETWEEN_REVIEW_REQUESTS)")

        val shouldShow = daysSinceLastRequest >= DAYS_BETWEEN_REVIEW_REQUESTS
        android.util.Log.d("InAppReviewUtils", "Should show review: $shouldShow")
        return shouldShow
    }
    
    /**
     * Launch the in-app review flow
     * Call this at a natural break in your app flow (after completing an action)
     */
    fun launchInAppReview(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        android.util.Log.d("InAppReviewUtils", "Launching in-app review flow")

        try {
            val prefs = getPrefs(activity)
            // DON'T increment show count here - only increment when we actually show something to the user
            
            val reviewManager = ReviewManagerFactory.create(activity)
            val request = reviewManager.requestReviewFlow()

            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("InAppReviewUtils", "Review flow request successful, launching UI")

                    // We got the ReviewInfo object - NOW increment the count since we're about to show UI
                    val currentShowCount = prefs.getInt(KEY_REVIEW_SHOW_COUNT, 0) + 1
                    prefs.edit().putInt(KEY_REVIEW_SHOW_COUNT, currentShowCount)
                        .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
                        .apply()

                    if (currentShowCount >= MAX_REVIEW_SHOW_COUNT) {
                        prefs.edit().putBoolean(KEY_REVIEW_COMPLETED, true).apply()
                        android.util.Log.d("InAppReviewUtils", "Reached max show count, marking review completed")
                    }

                    // Log that we're about to show UI (this helps detect if Google suppresses it)
                    val uiLaunchParams = android.os.Bundle().apply {
                        putString("method", "play_review_api")
                        putString("stage", "ui_launch_requested")
                        putString("timestamp", System.currentTimeMillis().toString())
                        putInt("show_count", currentShowCount)
                    }
                    AnalyticsUtils.logEvent("in_app_review_ui_launch", uiLaunchParams)

                    // We got the ReviewInfo object
                    val reviewInfo = task.result
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)

                    flow.addOnCompleteListener { flowTask ->
                        val flowCompletionTime = System.currentTimeMillis()
                        val timeSinceRequest = flowCompletionTime - prefs.getLong(KEY_LAST_REVIEW_REQUEST, flowCompletionTime)
                        
                        android.util.Log.d("InAppReviewUtils", "Review flow completed in ${timeSinceRequest}ms")

                        // Log completion with timing to help detect suppressed UI
                        val completionParams = android.os.Bundle().apply {
                            putString("method", "play_review_api")
                            putString("stage", "ui_completed")
                            putLong("completion_time_ms", timeSinceRequest)
                            putString("timestamp", flowCompletionTime.toString())
                            putBoolean("likely_suppressed", timeSinceRequest < 100) // Flag suspiciously fast completions
                        }
                        AnalyticsUtils.logEvent("in_app_review_ui_completion", completionParams)

                        // Update preferences and mark as completed (best-effort)
                        prefs.edit()
                            .putLong(KEY_LAST_REVIEW_REQUEST, flowCompletionTime)
                            .putBoolean(KEY_REVIEW_COMPLETED, true)
                            .apply()

                        val successParams = android.os.Bundle().apply {
                            putString("method", "play_review_api")
                            putBoolean("success", true)
                            putString("timestamp", flowCompletionTime.toString())
                            putLong("duration_ms", timeSinceRequest)
                        }
                        AnalyticsUtils.logEvent("in_app_review", successParams)

                        onComplete?.invoke(true)
                    }
                } else {
                    // There was some problem, log it
                    val errorCode = (task.exception as? com.google.android.play.core.review.ReviewException)?.errorCode
                    android.util.Log.w("InAppReviewUtils", "Review flow failed with error: $errorCode, exception: ${task.exception}")

                    // Update last request time only (don't increment show count for failures)
                    prefs.edit().putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis()).apply()

                    // If Play Store not available or in debug build, show fallback dialog
                    val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (isDebuggable || errorCode == ReviewErrorCode.PLAY_STORE_NOT_FOUND) {
                        android.util.Log.w("InAppReviewUtils", "Using fallback review dialog (debug or Play Store missing)")

                        // Increment show count here since we're showing fallback UI
                        val currentShowCount = prefs.getInt(KEY_REVIEW_SHOW_COUNT, 0) + 1
                        prefs.edit().putInt(KEY_REVIEW_SHOW_COUNT, currentShowCount).apply()

                        if (currentShowCount >= MAX_REVIEW_SHOW_COUNT) {
                            prefs.edit().putBoolean(KEY_REVIEW_COMPLETED, true).apply()
                            android.util.Log.d("InAppReviewUtils", "Reached max show count via fallback, marking review completed")
                        }

                        // In debug builds, show the fallback dialog immediately so developers can see the UI
                        if (isDebuggable) {
                            showFallbackReviewDialog(activity, onComplete)
                        } else {
                            // Only show fallback when Play Store missing in non-debug
                            showFallbackReviewDialog(activity, onComplete)
                        }

                        val fallbackParams = android.os.Bundle().apply {
                            putString("reason", errorCode?.toString() ?: "unknown")
                            putString("timestamp", System.currentTimeMillis().toString())
                        }
                        AnalyticsUtils.logEvent("in_app_review_fallback", fallbackParams)
                    } else {
                        when (errorCode) {
                            ReviewErrorCode.NO_ERROR -> {
                                android.util.Log.d("InAppReviewUtils", "No error reported")
                            }
                            ReviewErrorCode.INTERNAL_ERROR -> {
                                android.util.Log.w("InAppReviewUtils", "Internal error in review API")
                            }
                            else -> {
                                android.util.Log.w("InAppReviewUtils", "Other error: $errorCode")
                            }
                        }

                        android.widget.Toast.makeText(activity, activity.getString(R.string.error_review_api, errorCode.toString()), android.widget.Toast.LENGTH_SHORT).show()
                        onComplete?.invoke(false)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InAppReviewUtils", "Exception launching review flow", e)
            // In case of any exception, offer fallback in debug builds
            val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                // Increment show count here since we're showing fallback UI
                val prefs = getPrefs(activity)
                val currentShowCount = prefs.getInt(KEY_REVIEW_SHOW_COUNT, 0) + 1
                prefs.edit().putInt(KEY_REVIEW_SHOW_COUNT, currentShowCount)
                    .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
                    .apply()

                if (currentShowCount >= MAX_REVIEW_SHOW_COUNT) {
                    prefs.edit().putBoolean(KEY_REVIEW_COMPLETED, true).apply()
                    android.util.Log.d("InAppReviewUtils", "Reached max show count via exception fallback, marking review completed")
                }

                showFallbackReviewDialog(activity, onComplete)
                val fallbackExceptionParams = android.os.Bundle().apply {
                    putString("reason", "exception")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("in_app_review_fallback", fallbackExceptionParams)
            } else {
                android.widget.Toast.makeText(activity, activity.getString(R.string.error_review_system, e.message), android.widget.Toast.LENGTH_SHORT).show()
                onComplete?.invoke(false)
            }
        }
    }

    private fun showFallbackReviewDialog(activity: Activity, onComplete: ((Boolean) -> Unit)? = null, markAsCompleted: Boolean = true) {
        try {
            android.util.Log.d("InAppReviewUtils", "Showing fallback review dialog (markAsCompleted: $markAsCompleted)")
            
            val builder = AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_rate_title)
                .setMessage(R.string.dialog_rate_message)
                .setPositiveButton(R.string.dialog_rate_positive) { _, _ ->
                    // Try to open Play Store
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
                        activity.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // Fallback to web URL or toast
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}"))
                            activity.startActivity(intent)
                        } catch (e2: Exception) {
                            android.widget.Toast.makeText(activity, R.string.error_play_store_not_available, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Only mark as completed if specified
                    if (markAsCompleted) {
                        markReviewCompleted(activity)
                    }
                    val actParams = android.os.Bundle().apply { 
                        putString("action", "rate_app")
                        putString("timestamp", System.currentTimeMillis().toString())
                        putBoolean("marked_completed", markAsCompleted)
                    }
                    AnalyticsUtils.logEvent("in_app_review_fallback_action", actParams)
                    onComplete?.invoke(true)
                }
                .setNegativeButton(R.string.dialog_rate_negative) { dialog, _ ->
                    // Only mark as completed if specified
                    if (markAsCompleted) {
                        markReviewCompleted(activity)
                    }
                    val notNowParams = android.os.Bundle().apply { 
                        putString("action", "not_now")
                        putString("timestamp", System.currentTimeMillis().toString())
                        putBoolean("marked_completed", markAsCompleted)
                    }
                    AnalyticsUtils.logEvent("in_app_review_fallback_action", notNowParams)
                    onComplete?.invoke(false)
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    // Only mark as completed if specified
                    if (markAsCompleted) {
                        markReviewCompleted(activity)
                    }
                    val dismissParams = android.os.Bundle().apply { 
                        putString("action", "dismiss")
                        putString("timestamp", System.currentTimeMillis().toString())
                        putBoolean("marked_completed", markAsCompleted)
                    }
                    AnalyticsUtils.logEvent("in_app_review_fallback_action", dismissParams)
                    onComplete?.invoke(false)
                }

            val dialog = builder.create()
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("InAppReviewUtils", "Error showing fallback dialog", e)
            onComplete?.invoke(false)
        }
    }
    
    /**
     * Reset review state (useful for testing)
     */
    fun resetReviewState(context: Context) {
        getPrefs(context).edit().clear().apply()
        android.util.Log.d("InAppReviewUtils", "Review state reset - all preferences cleared")
    }

    /**
     * Force-show the visible fallback review dialog (useful for manual QA/testing).
     * This bypasses Play Core and immediately shows the app-hosted dialog used in debug/fallback paths.
     * Unlike the regular flow, this does NOT mark the review as completed automatically.
     */
    fun forceShowVisibleReview(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        // Only allow force-show in debuggable builds to avoid exposing QA hooks in release
        val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            android.util.Log.w("InAppReviewUtils", "forceShowVisibleReview called in non-debug build - no-op")
            onComplete?.invoke(false)
            return
        }

        android.util.Log.d("InAppReviewUtils", "Force showing visible review dialog for debugging")
        showFallbackReviewDialog(activity, onComplete, markAsCompleted = false)
    }
    
    /**
     * Debug method to show review dialog that actually marks as completed (for testing the full flow)
     */
    fun debugShowReviewWithCompletion(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            android.util.Log.w("InAppReviewUtils", "debugShowReviewWithCompletion called in non-debug build - no-op")
            onComplete?.invoke(false)
            return
        }

        android.util.Log.d("InAppReviewUtils", "Debug showing review dialog with completion marking")
        showFallbackReviewDialog(activity, onComplete, markAsCompleted = true)
    }

    
    /**
     * Force set install date for testing (useful for debugging timing)
     */
    fun setTestInstallDate(context: Context, daysAgo: Int) {
        val testDate = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L)
        getPrefs(context).edit()
            .putLong(KEY_FIRST_INSTALL_DATE, testDate)
            .apply()
    }
    
    /**
     * Get current launch count (useful for debugging)
     */
    fun getLaunchCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAUNCH_COUNT, 0)
    }
    
    /**
     * Get days since first install (useful for debugging)
     */
    fun getDaysSinceFirstInstall(context: Context): Long {
        val prefs = getPrefs(context)
        initializeFirstInstallDateIfNeeded(prefs, context)
        val firstInstallDate = prefs.getLong(KEY_FIRST_INSTALL_DATE, System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        return (currentTime - firstInstallDate) / (1000 * 60 * 60 * 24)
    }
    
    /**
     * Check if the review system is likely working based on timing patterns.
     * This helps detect if Google is suppressing the UI.
     */
    fun getReviewSystemHealthInfo(context: Context): Map<String, Any> {
        val prefs = getPrefs(context)
        val showCount = prefs.getInt(KEY_REVIEW_SHOW_COUNT, 0)
        val lastRequest = prefs.getLong(KEY_LAST_REVIEW_REQUEST, 0)
        val isCompleted = prefs.getBoolean(KEY_REVIEW_COMPLETED, false)
        val daysSinceInstall = getDaysSinceFirstInstall(context)
        
        return mapOf(
            "show_count" to showCount,
            "is_completed" to isCompleted,
            "days_since_install" to daysSinceInstall,
            "last_request_timestamp" to lastRequest,
            "should_show_now" to shouldShowReviewPrompt(context),
            "system_health" to when {
                showCount > 0 && isCompleted -> "Working - User completed review"
                showCount > 2 && !isCompleted -> "Potentially suppressed - Multiple attempts without completion"
                daysSinceInstall > 10 && showCount == 0 -> "Not triggered - Check timing logic"
                else -> "Normal"
            }
        )
    }
    
    /**
     * Mark review as permanently completed (stops showing forever)
     */
    fun markReviewCompleted(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_REVIEW_COMPLETED, true)
            .apply()
    }
    
    /**
     * Check if review was marked as completed
     */
    fun isReviewCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REVIEW_COMPLETED, false)
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun initializeFirstInstallDateIfNeeded(prefs: SharedPreferences, context: Context) {
        if (!prefs.contains(KEY_FIRST_INSTALL_DATE)) {
            // Try to get the actual app install date from PackageManager
            val installDate = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.firstInstallTime
            } catch (e: Exception) {
                // Fallback: use current time
                System.currentTimeMillis()
            }
            
            prefs.edit()
                .putLong(KEY_FIRST_INSTALL_DATE, installDate)
                .apply()
        }
    }
}
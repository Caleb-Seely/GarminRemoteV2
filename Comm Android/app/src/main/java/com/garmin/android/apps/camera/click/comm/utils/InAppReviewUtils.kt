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
    
    // Production thresholds
    private const val INITIAL_DELAY_DAYS = 5
    private const val DAYS_BETWEEN_REVIEW_REQUESTS = 30
    private const val MAX_REVIEW_SHOW_COUNT = 6
    private const val KEY_REVIEW_SHOW_COUNT = "review_show_count"
    
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
        
        // If we've never shown a review request, show it now (after 5 days)
        if (lastRequest == 0L) {
            android.util.Log.d("InAppReviewUtils", "First time showing review prompt")
            return true
        }
        
    // Check time since last request (DAYS_BETWEEN_REVIEW_REQUESTS between requests)
    val daysSinceLastRequest = if (lastRequest > 0) (currentTime - lastRequest) / (1000 * 60 * 60 * 24) else Long.MAX_VALUE
    android.util.Log.d("InAppReviewUtils", "Days since last request: $daysSinceLastRequest (need $DAYS_BETWEEN_REVIEW_REQUESTS)")
        
    val shouldShow = daysSinceLastRequest >= DAYS_BETWEEN_REVIEW_REQUESTS || lastRequest == 0L
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
            // Increment show count immediately to avoid spamming
            val prefs = getPrefs(activity)
            val currentShowCount = prefs.getInt(KEY_REVIEW_SHOW_COUNT, 0) + 1
            prefs.edit().putInt(KEY_REVIEW_SHOW_COUNT, currentShowCount)
                .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
                .apply()

            if (currentShowCount >= MAX_REVIEW_SHOW_COUNT) {
                prefs.edit().putBoolean(KEY_REVIEW_COMPLETED, true).apply()
                android.util.Log.d("InAppReviewUtils", "Reached max show count, marking review completed")
            }

            val reviewManager = ReviewManagerFactory.create(activity)
            val request = reviewManager.requestReviewFlow()

            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("InAppReviewUtils", "Review flow request successful, launching UI")

                    // We got the ReviewInfo object
                    val reviewInfo = task.result
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)

                    flow.addOnCompleteListener { flowTask ->
                        android.util.Log.d("InAppReviewUtils", "Review flow completed")

                        // Update preferences and mark as completed (best-effort)
                        prefs.edit()
                            .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
                            .putBoolean(KEY_REVIEW_COMPLETED, true)
                            .apply()

                        val successParams = android.os.Bundle().apply {
                            putString("method", "play_review_api")
                            putBoolean("success", true)
                            putString("timestamp", System.currentTimeMillis().toString())
                        }
                        AnalyticsUtils.logEvent("in_app_review", successParams)

                        onComplete?.invoke(true)
                    }
                } else {
                    // There was some problem, log it
                    val errorCode = (task.exception as? com.google.android.play.core.review.ReviewException)?.errorCode
                    android.util.Log.w("InAppReviewUtils", "Review flow failed with error: $errorCode, exception: ${task.exception}")

                    // Update last request time already set above

                    // If Play Store not available or in debug build, show fallback dialog
                    val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (isDebuggable || errorCode == ReviewErrorCode.PLAY_STORE_NOT_FOUND) {
                        android.util.Log.w("InAppReviewUtils", "Using fallback review dialog (debug or Play Store missing)")

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

                        android.widget.Toast.makeText(activity, "Review API error: $errorCode", android.widget.Toast.LENGTH_SHORT).show()
                        onComplete?.invoke(false)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InAppReviewUtils", "Exception launching review flow", e)
            // In case of any exception, offer fallback in debug builds
            val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                showFallbackReviewDialog(activity, onComplete)
                val fallbackExceptionParams = android.os.Bundle().apply {
                    putString("reason", "exception")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("in_app_review_fallback", fallbackExceptionParams)
            } else {
                android.widget.Toast.makeText(activity, "Review system error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                onComplete?.invoke(false)
            }
        }
    }

    private fun showFallbackReviewDialog(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            val builder = AlertDialog.Builder(activity)
                .setTitle("Rate CameraClick")
                .setMessage("Thanks for using my app! Have a couple seconds to review it?")
                .setPositiveButton("Review App") { _, _ ->
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
                            android.widget.Toast.makeText(activity, "Play Store not available", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Treat as completed for testing purposes
                    markReviewCompleted(activity)
                    val actParams = android.os.Bundle().apply { putString("action", "rate_app"); putString("timestamp", System.currentTimeMillis().toString()) }
                    AnalyticsUtils.logEvent("in_app_review_fallback_action", actParams)
                    onComplete?.invoke(true)
                }
                .setNegativeButton("Deny") { dialog, _ ->
                    // Dismiss and mark as completed for debug fidelity
                    markReviewCompleted(activity)
                    val notNowParams = android.os.Bundle().apply { putString("action", "not_now"); putString("timestamp", System.currentTimeMillis().toString()) }
                    AnalyticsUtils.logEvent("in_app_review_fallback_action", notNowParams)
                    onComplete?.invoke(true)
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    // Treat cancel as completed for testing
                    markReviewCompleted(activity)
                    val dismissParams = android.os.Bundle().apply { putString("action", "dismiss"); putString("timestamp", System.currentTimeMillis().toString()) }
                    AnalyticsUtils.logEvent("in_app_review_fallback_action", dismissParams)
                    onComplete?.invoke(true)
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
    }

    /**
     * Force-show the visible fallback review dialog (useful for manual QA/testing).
     * This bypasses Play Core and immediately shows the app-hosted dialog used in debug/fallback paths.
     */
    fun forceShowVisibleReview(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        // Only allow force-show in debuggable builds to avoid exposing QA hooks in release
        val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            android.util.Log.w("InAppReviewUtils", "forceShowVisibleReview called in non-debug build - no-op")
            onComplete?.invoke(false)
            return
        }

        // Reset the completed flag so the dialog behaves like a normal prompt
        try {
            getPrefs(activity).edit().putBoolean(KEY_REVIEW_COMPLETED, false).apply()
        } catch (_: Exception) {
            // ignore
        }
        showFallbackReviewDialog(activity, onComplete)
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
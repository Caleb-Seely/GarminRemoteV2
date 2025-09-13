package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Manager for handling developer note popup timing, state persistence, and version progression.
 * Displays periodic popups introducing the app developer with 4 different message variations.
 */
object DeveloperNoteManager {
    
    private const val TAG = "DeveloperNoteManager"
    private const val PREFS_NAME = "developer_note_prefs"
    private const val KEY_FIRST_INSTALL_DATE = "developer_note_first_install_date"
    private const val KEY_LAST_POPUP_DATE = "developer_note_last_popup_date"
    private const val KEY_POPUP_VERSION_INDEX = "developer_note_popup_version_index"
    
    // Production timing constants
    private const val INITIAL_DELAY_MS = 5 * 24 * 60 * 60 * 1000L // 5 days
    private const val REPEAT_INTERVAL_MS = 60 * 24 * 60 * 60 * 1000L // 60 days
    private const val KEY_DEFER_TO_NEXT_LAUNCH = "developer_note_defer_next_launch"
    
    // Version constants
    private const val VERSION_COMPLETE = -1
    private const val MAX_VERSION_INDEX = 3 // 0, 1, 2, 3 = 4 versions total
    
    /**
     * Check if the popup should be displayed based on timing and version progression
     */
    fun shouldShowPopup(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            
            // Initialize first install date if not set
            initializeFirstInstallDateIfNeeded(prefs, context)
            
            // Check if popup cycle is complete
            val versionIndex = prefs.getInt(KEY_POPUP_VERSION_INDEX, 0)
            if (versionIndex == VERSION_COMPLETE) {
                Log.d(TAG, "Popup cycle complete, not showing popup")
                logTimingCheck(context, false, "cycle_complete", versionIndex)
                return false
            }
            
            val currentTime = System.currentTimeMillis()
            val firstInstallDate = prefs.getLong(KEY_FIRST_INSTALL_DATE, currentTime)
            val lastPopupDate = prefs.getLong(KEY_LAST_POPUP_DATE, 0)
            val deferred = prefs.getBoolean(KEY_DEFER_TO_NEXT_LAUNCH, false)
            
            val shouldShow = when (versionIndex) {
                0 -> {
                    // First popup: show after INITIAL_DELAY_MS from install
                    val daysSinceInstall = (currentTime - firstInstallDate) / (24 * 60 * 60 * 1000L)
                    val shouldShowFirst = (currentTime - firstInstallDate) >= INITIAL_DELAY_MS
                    Log.d(TAG, "First popup check: $daysSinceInstall days since install, should show: $shouldShowFirst")
                    shouldShowFirst || deferred
                }
                in 1..MAX_VERSION_INDEX -> {
                    // Subsequent popups: show 60 days after last popup
                    val daysSinceLastPopup = if (lastPopupDate > 0) (currentTime - lastPopupDate) / (24 * 60 * 60 * 1000L) else -1
                    val shouldShowNext = (lastPopupDate > 0 && (currentTime - lastPopupDate) >= REPEAT_INTERVAL_MS) || deferred
                    Log.d(TAG, "Subsequent popup check (version $versionIndex): $daysSinceLastPopup days since last popup, should show: $shouldShowNext (deferred=$deferred)")
                    shouldShowNext
                }
                else -> {
                    Log.w(TAG, "Invalid version index: $versionIndex")
                    false
                }
            }
            
            val reason = if (shouldShow) "timing_met" else "timing_not_met"
            logTimingCheck(context, shouldShow, reason, versionIndex)
            
            shouldShow
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if popup should show", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "timing_check_failed", e.message ?: "unknown")
            false
        }
    }
    
    /**
     * Get the current version index for popup content selection
     */
    fun getCurrentVersionIndex(context: Context): Int {
        val prefs = getPrefs(context)
        return prefs.getInt(KEY_POPUP_VERSION_INDEX, 0)
    }
    
    /**
     * Record that a popup was shown and advance to next version
     */
    fun recordPopupShown(context: Context) {
        try {
            val prefs = getPrefs(context)
            val currentVersion = prefs.getInt(KEY_POPUP_VERSION_INDEX, 0)
            val currentTime = System.currentTimeMillis()
            
            val nextVersion = if (currentVersion >= MAX_VERSION_INDEX) {
                VERSION_COMPLETE
            } else {
                currentVersion + 1
            }
            
            prefs.edit()
                .putLong(KEY_LAST_POPUP_DATE, currentTime)
                .putInt(KEY_POPUP_VERSION_INDEX, nextVersion)
                .putBoolean(KEY_DEFER_TO_NEXT_LAUNCH, false)
                .apply()
            
            Log.d(TAG, "Popup shown recorded: version $currentVersion -> $nextVersion")
            FirebaseCrashlytics.getInstance().log("Developer note popup shown - version $currentVersion, next: $nextVersion")
            
            // Log state change analytics
            logStateChange(context, "popup_shown", currentVersion, nextVersion)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording popup shown", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "record_shown_failed", e.message ?: "unknown")
        }
    }
    
    /**
     * Record that a popup was dismissed (for analytics/debugging)
     */
    fun recordPopupDismissed(context: Context) {
        try {
            val prefs = getPrefs(context)
            val currentVersion = prefs.getInt(KEY_POPUP_VERSION_INDEX, 0)
            val currentTime = System.currentTimeMillis()
            
            prefs.edit()
                .putLong(KEY_LAST_POPUP_DATE, currentTime)
                .putBoolean(KEY_DEFER_TO_NEXT_LAUNCH, false)
                .apply()
            
            Log.d(TAG, "Popup dismissed recorded for version $currentVersion")
            FirebaseCrashlytics.getInstance().log("Developer note popup dismissed - version $currentVersion")
            
            // Log dismissal analytics
            logStateChange(context, "popup_dismissed", currentVersion, currentVersion)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording popup dismissed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "record_dismissed_failed", e.message ?: "unknown")
        }
    }
    
    /**
     * Check if the popup cycle is complete (all 4 versions shown)
     */
    fun isPopupCycleComplete(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getInt(KEY_POPUP_VERSION_INDEX, 0) == VERSION_COMPLETE
    }

    /**
     * Defer the next developer note to the next app launch regardless of interval.
     */
    fun deferToNextLaunch(context: Context) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().putBoolean(KEY_DEFER_TO_NEXT_LAUNCH, true).apply()
            Log.d(TAG, "Developer note deferred to next launch")
            FirebaseCrashlytics.getInstance().log("Developer note deferred to next launch")
        } catch (e: Exception) {
            Log.e(TAG, "Error deferring developer note", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
    
    /**
     * Get the first install date timestamp
     */
    fun getFirstInstallDate(context: Context): Long {
        val prefs = getPrefs(context)
        initializeFirstInstallDateIfNeeded(prefs, context)
        return prefs.getLong(KEY_FIRST_INSTALL_DATE, System.currentTimeMillis())
    }
    
    /**
     * Get the last popup date timestamp
     */
    fun getLastPopupDate(context: Context): Long {
        val prefs = getPrefs(context)
        return prefs.getLong(KEY_LAST_POPUP_DATE, 0)
    }
    
    /**
     * Reset all popup state (useful for testing)
     */
    fun resetPopupState(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.d(TAG, "Developer note popup state reset")
    }
    
    /**
     * Force set install date for testing (useful for debugging timing)
     */
    fun setTestInstallDate(context: Context, daysAgo: Int) {
        val testDate = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L)
        getPrefs(context).edit()
            .putLong(KEY_FIRST_INSTALL_DATE, testDate)
            .apply()
        Log.d(TAG, "Test install date set to $daysAgo days ago")
    }

    /**
     * Force set last popup date for testing (simulate that the last popup was daysAgo days ago)
     */
    fun setTestLastPopupDate(context: Context, daysAgo: Int) {
        val testDate = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L)
        getPrefs(context).edit()
            .putLong(KEY_LAST_POPUP_DATE, testDate)
            .apply()
        Log.d(TAG, "Test last popup date set to $daysAgo days ago")
    }
    
    /**
     * Get days since first install (useful for debugging)
     */
    fun getDaysSinceFirstInstall(context: Context): Long {
        val firstInstallDate = getFirstInstallDate(context)
        val currentTime = System.currentTimeMillis()
        return (currentTime - firstInstallDate) / (24 * 60 * 60 * 1000L)
    }
    
    /**
     * Get days since last popup (useful for debugging)
     */
    fun getDaysSinceLastPopup(context: Context): Long {
        val lastPopupDate = getLastPopupDate(context)
        if (lastPopupDate == 0L) return -1
        
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastPopupDate) / (24 * 60 * 60 * 1000L)
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing SharedPreferences", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "prefs_access_failed", e.message ?: "unknown")
            throw e
        }
    }
    
    private fun initializeFirstInstallDateIfNeeded(prefs: SharedPreferences, context: Context) {
        try {
            if (!prefs.contains(KEY_FIRST_INSTALL_DATE)) {
                // Try to get the actual app install date from PackageManager
                val installDate = try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    packageInfo.firstInstallTime
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get actual install date, using current time", e)
                    // Fallback: For existing users, use a date 6 days ago so they see popup immediately
                    System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L)
                }
                
                prefs.edit()
                    .putLong(KEY_FIRST_INSTALL_DATE, installDate)
                    .apply()
                    
                val daysSinceActualInstall = (System.currentTimeMillis() - installDate) / (24 * 60 * 60 * 1000L)
                Log.d(TAG, "Initialized first install date: $installDate ($daysSinceActualInstall days ago)")
                FirebaseCrashlytics.getInstance().log("Developer note first install date initialized - $daysSinceActualInstall days since actual install")
                
                // Log analytics for install date initialization
                val params = android.os.Bundle().apply {
                    putLong("install_date", installDate)
                    putLong("days_since_actual_install", daysSinceActualInstall)
                    putBoolean("is_existing_user", daysSinceActualInstall > 5)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_install_date_init", params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing first install date", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "init_install_date_failed", e.message ?: "unknown")
        }
    }
    
    /**
     * Log timing check analytics for debugging popup timing logic
     */
    private fun logTimingCheck(context: Context, shouldShow: Boolean, reason: String, versionIndex: Int) {
        try {
            val params = android.os.Bundle().apply {
                putBoolean("should_show", shouldShow)
                putString("reason", reason)
                putInt("version_index", versionIndex)
                putLong("days_since_install", getDaysSinceFirstInstall(context))
                putLong("days_since_last_popup", getDaysSinceLastPopup(context))
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_timing_check", params)
            
            Log.d(TAG, "Timing check: shouldShow=$shouldShow, reason=$reason, version=$versionIndex")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging timing check", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
    
    /**
     * Log state change analytics for popup progression tracking
     */
    private fun logStateChange(context: Context, action: String, currentVersion: Int, nextVersion: Int) {
        try {
            val params = android.os.Bundle().apply {
                putString("action", action)
                putInt("current_version", currentVersion)
                putInt("next_version", nextVersion)
                putBoolean("cycle_complete", nextVersion == VERSION_COMPLETE)
                putLong("days_since_install", getDaysSinceFirstInstall(context))
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_state_change", params)
            
            Log.d(TAG, "State change: action=$action, $currentVersion -> $nextVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging state change", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
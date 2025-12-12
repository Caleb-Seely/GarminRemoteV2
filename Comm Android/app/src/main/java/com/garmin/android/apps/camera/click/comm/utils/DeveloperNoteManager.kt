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
    private const val KEY_DEFER_TO_NEXT_LAUNCH = "developer_note_defer_next_launch"

    // New simplified state tracking
    private const val KEY_POPUP_SHOWN = "developer_note_popup_shown"
    private const val KEY_POPUP_SHOWN_DATE = "developer_note_popup_shown_date"

    // Production timing constants
    private const val INITIAL_DELAY_MS = 5 * 24 * 60 * 60 * 1000L // 5 days
    private const val REPEAT_INTERVAL_MS = 60 * 24 * 60 * 60 * 1000L // 60 days
    
    // Version constants
    private const val VERSION_COMPLETE = -1
    private const val MAX_VERSION_INDEX = 3 // 0, 1, 2, 3 = 4 versions total
    
    /**
     * Check if the popup should be displayed based on timing (simplified single-show logic)
     */
    fun shouldShowPopup(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)

            // Initialize first install date if not set
            initializeFirstInstallDateIfNeeded(prefs, context)

            // Check if popup has already been shown (new simplified logic)
            val popupShown = prefs.getBoolean(KEY_POPUP_SHOWN, false)
            if (popupShown) {
                Log.d(TAG, "Popup already shown, not showing again")
                logTimingCheck(context, false, "already_shown", 0)
                return false
            }

            val currentTime = System.currentTimeMillis()
            val firstInstallDate = prefs.getLong(KEY_FIRST_INSTALL_DATE, currentTime)
            val deferred = prefs.getBoolean(KEY_DEFER_TO_NEXT_LAUNCH, false)

            // Show after 5 days from install OR if deferred from previous launch
            val daysSinceInstall = (currentTime - firstInstallDate) / (24 * 60 * 60 * 1000L)
            val shouldShow = (currentTime - firstInstallDate) >= INITIAL_DELAY_MS || deferred

            Log.d(TAG, "Popup check: $daysSinceInstall days since install, should show: $shouldShow (deferred=$deferred)")

            val reason = when {
                shouldShow && deferred -> "deferred"
                shouldShow -> "timing_met"
                else -> "timing_not_met"
            }
            logTimingCheck(context, shouldShow, reason, 0)

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
     * Always returns 0 for single-message system
     */
    fun getCurrentVersionIndex(context: Context): Int {
        // Simplified: always return 0 (single message)
        return 0
    }
    
    /**
     * Record that a popup was shown (simplified single-show logic)
     */
    fun recordPopupShown(context: Context) {
        try {
            val prefs = getPrefs(context)
            val currentTime = System.currentTimeMillis()

            prefs.edit()
                .putBoolean(KEY_POPUP_SHOWN, true)
                .putLong(KEY_POPUP_SHOWN_DATE, currentTime)
                .putBoolean(KEY_DEFER_TO_NEXT_LAUNCH, false)
                .apply()

            Log.d(TAG, "Popup shown recorded at $currentTime")
            FirebaseCrashlytics.getInstance().log("Developer note popup shown - single-show system")

            // Log state change analytics
            logStateChange(context, "popup_shown", 0, -1)

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

    /**
     * Check if popup has been shown (new simplified logic)
     */
    fun isPopupShown(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_POPUP_SHOWN, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if popup shown", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    /**
     * Get the timestamp when popup was shown
     */
    fun getPopupShownDate(context: Context): Long {
        return try {
            getPrefs(context).getLong(KEY_POPUP_SHOWN_DATE, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting popup shown date", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            0
        }
    }

    /**
     * Migrate existing users who have seen old multi-version popups
     * to the new single-popup system
     */
    fun migrateExistingUsers(context: Context) {
        try {
            val prefs = getPrefs(context)

            // Check if already migrated
            if (prefs.contains(KEY_POPUP_SHOWN)) {
                Log.d(TAG, "Already migrated to new popup system")
                return
            }

            val versionIndex = prefs.getInt(KEY_POPUP_VERSION_INDEX, 0)
            val lastPopupDate = prefs.getLong(KEY_LAST_POPUP_DATE, 0)

            // If user has seen ANY version (index > 0) or has a last popup date, mark as shown
            if (versionIndex > 0 || lastPopupDate > 0) {
                prefs.edit()
                    .putBoolean(KEY_POPUP_SHOWN, true)
                    .putLong(KEY_POPUP_SHOWN_DATE, lastPopupDate)
                    .apply()

                Log.d(TAG, "Migrated existing user: version $versionIndex, last shown at $lastPopupDate")
                FirebaseCrashlytics.getInstance().log("Developer note migrated - version $versionIndex to single-popup system")

                // Log migration analytics
                val params = android.os.Bundle().apply {
                    putInt("old_version_index", versionIndex)
                    putLong("old_last_popup_date", lastPopupDate)
                    putBoolean("had_seen_popup", true)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_migration", params)
            } else {
                // New user or user who hasn't seen popup yet - just mark as migrated
                prefs.edit()
                    .putBoolean(KEY_POPUP_SHOWN, false)
                    .putLong(KEY_POPUP_SHOWN_DATE, 0)
                    .apply()

                Log.d(TAG, "Migrated new user - no previous popups")

                val params = android.os.Bundle().apply {
                    putBoolean("had_seen_popup", false)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_migration", params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating existing users", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "migration_failed", e.message ?: "unknown")
        }
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
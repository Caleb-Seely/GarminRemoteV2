package com.garmin.android.apps.camera.click.comm.repository

import android.content.Context
import android.content.SharedPreferences
import com.garmin.android.apps.camera.click.comm.config.PreferencesKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing device and application preferences.
 * Provides type-safe access to SharedPreferences with centralized logic.
 *
 * This replaces direct SharedPreferences access throughout the codebase,
 * making the code more testable and maintainable.
 */
@Singleton
class DevicePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            PreferencesKeys.Files.CAMERA_CLICK_PREFS,
            Context.MODE_PRIVATE
        )
    }

    init {
        // Users upgrading from a version before onboarding was added won't have
        // FIRST_LAUNCH_COMPLETE. Use PackageManager to distinguish an update
        // (lastUpdateTime > firstInstallTime) from a fresh install, so existing
        // users skip onboarding without needing any specific prefs key to be present.
        if (!prefs.contains(PreferencesKeys.Compatibility.FIRST_LAUNCH_COMPLETE)) {
            val pkg = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
            if (pkg != null && pkg.lastUpdateTime > pkg.firstInstallTime) {
                prefs.edit().putBoolean(PreferencesKeys.Compatibility.FIRST_LAUNCH_COMPLETE, true).apply()
            }
        }
    }

    /**
     * Auto-launch camera feature settings
     */
    var isAutoLaunchEnabled: Boolean
        get() = prefs.getBoolean(PreferencesKeys.App.AUTO_LAUNCH_CAMERA, false)
        set(value) = prefs.edit().putBoolean(PreferencesKeys.App.AUTO_LAUNCH_CAMERA, value).apply()

    /**
     * Preferred device ID for auto-launch
     */
    var preferredDeviceId: String?
        get() = prefs.getString(PreferencesKeys.App.PREFERRED_DEVICE_ID, null)
        set(value) = prefs.edit().putString(PreferencesKeys.App.PREFERRED_DEVICE_ID, value).apply()

    /**
     * First app launch detection for compatibility check
     */
    var isFirstAppLaunch: Boolean
        get() = !prefs.contains(PreferencesKeys.Compatibility.FIRST_LAUNCH_COMPLETE)
        set(value) = prefs.edit().putBoolean(PreferencesKeys.Compatibility.FIRST_LAUNCH_COMPLETE, !value).apply()

    /**
     * Last compatibility check timestamp
     */
    var lastCompatibilityCheckTimestamp: Long
        get() = prefs.getLong(PreferencesKeys.Compatibility.LAST_CHECK_TIMESTAMP, 0)
        set(value) = prefs.edit().putLong(PreferencesKeys.Compatibility.LAST_CHECK_TIMESTAMP, value).apply()

    /**
     * Compatibility check session dismissed flag
     */
    var compatibilityCheckSessionDismissed: Boolean
        get() = prefs.getBoolean(PreferencesKeys.Compatibility.SESSION_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(PreferencesKeys.Compatibility.SESSION_DISMISSED, value).apply()

    /**
     * Set to true after the first confirmed successful photo capture.
     * Controls the setup-incomplete banner visibility in DeviceActivity.
     */
    var isSetupComplete: Boolean
        get() = prefs.getBoolean(PreferencesKeys.App.SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(PreferencesKeys.App.SETUP_COMPLETE, value).apply()

    /**
     * Clear all preferences
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Remove specific preference
     */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /**
     * Check if a preference exists
     */
    fun contains(key: String): Boolean {
        return prefs.contains(key)
    }
}

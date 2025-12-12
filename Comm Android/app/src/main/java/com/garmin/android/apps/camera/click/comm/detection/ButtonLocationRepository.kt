package com.garmin.android.apps.camera.click.comm.detection

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.garmin.android.apps.camera.click.comm.config.PreferencesKeys
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting and retrieving button location information.
 *
 * This replaces the direct SharedPreferences access scattered throughout
 * AccessibilityUtils with a clean, testable repository pattern.
 */
@Singleton
class ButtonLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private companion object {
        const val TAG = "ButtonLocationRepo"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            PreferencesKeys.Files.ACCESSIBILITY_PREFS,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Save button information for the last successful detection
     */
    fun saveButtonInfo(buttonInfo: ShutterButtonInfo) {
        try {
            val json = gson.toJson(buttonInfo)
            prefs.edit().putString(PreferencesKeys.Accessibility.BUTTON_INFO, json).apply()
            Log.d(TAG, "Saved button info for ${buttonInfo.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving button info", e)
        }
    }

    /**
     * Get the last known button info (across all apps)
     */
    fun getLastKnownButtonInfo(): ShutterButtonInfo? {
        val json = prefs.getString(PreferencesKeys.Accessibility.BUTTON_INFO, null) ?: return null

        return try {
            gson.fromJson(json, ShutterButtonInfo::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading button info", e)
            null
        }
    }

    /**
     * Save user's manually selected preferred button for a specific app
     */
    fun saveUserPreferredButton(packageName: String, buttonInfo: ShutterButtonInfo) {
        try {
            val preferences = loadUserPreferences()
            preferences[packageName] = buttonInfo

            val json = gson.toJson(preferences)
            prefs.edit().putString(PreferencesKeys.Accessibility.USER_PREFERRED_BUTTONS, json).apply()
            Log.d(TAG, "Saved user preferred button for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user preferred button", e)
        }
    }

    /**
     * Get user's preferred button for a specific app
     */
    fun getUserPreferredButton(packageName: String): ShutterButtonInfo? {
        return loadUserPreferences()[packageName]
    }

    /**
     * Clear user preferred button for a specific app
     */
    fun clearUserPreferredButton(packageName: String) {
        try {
            val preferences = loadUserPreferences()
            preferences.remove(packageName)

            val json = gson.toJson(preferences)
            prefs.edit().putString(PreferencesKeys.Accessibility.USER_PREFERRED_BUTTONS, json).apply()
            Log.d(TAG, "Cleared user preferred button for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user preferred button", e)
        }
    }

    /**
     * Get all user preferred buttons
     */
    fun getAllUserPreferredButtons(): Map<String, ShutterButtonInfo> {
        return loadUserPreferences()
    }

    /**
     * Save detection statistics
     */
    fun saveDetectionStats(stats: DetectionStats) {
        try {
            val allStats = loadAllDetectionStats().toMutableMap()
            allStats[stats.packageName] = stats

            val json = gson.toJson(allStats)
            prefs.edit().putString(PreferencesKeys.Accessibility.DETECTION_STATS, json).apply()
            Log.d(TAG, "Saved detection stats for ${stats.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving detection stats", e)
        }
    }

    /**
     * Get detection statistics for a specific package
     */
    fun getDetectionStats(packageName: String): DetectionStats? {
        return loadAllDetectionStats()[packageName]
    }

    /**
     * Clear all stored data (useful for debugging or user-requested reset)
     */
    fun clearAll() {
        prefs.edit()
            .remove(PreferencesKeys.Accessibility.BUTTON_INFO)
            .remove(PreferencesKeys.Accessibility.USER_PREFERRED_BUTTONS)
            .remove(PreferencesKeys.Accessibility.DETECTION_STATS)
            .remove(PreferencesKeys.Accessibility.CANDIDATE_LISTS)
            .apply()
        Log.d(TAG, "Cleared all button location data")
    }

    /**
     * Clear data for a specific package
     */
    fun clearPackageData(packageName: String) {
        try {
            // Clear user preferences
            val userPrefs = loadUserPreferences()
            userPrefs.remove(packageName)
            prefs.edit().putString(
                PreferencesKeys.Accessibility.USER_PREFERRED_BUTTONS,
                gson.toJson(userPrefs)
            ).apply()

            // Clear detection stats
            val stats = loadAllDetectionStats().toMutableMap()
            stats.remove(packageName)
            prefs.edit().putString(
                PreferencesKeys.Accessibility.DETECTION_STATS,
                gson.toJson(stats)
            ).apply()

            Log.d(TAG, "Cleared data for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing package data", e)
        }
    }

    /**
     * Save all candidate lists for all apps
     */
    fun saveAllCandidateLists(candidatesByApp: Map<String, List<ShutterButtonInfo>>) {
        try {
            val json = gson.toJson(candidatesByApp)
            prefs.edit().putString(PreferencesKeys.Accessibility.CANDIDATE_LISTS, json).apply()
            Log.d(TAG, "Saved candidate lists for ${candidatesByApp.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving candidate lists", e)
        }
    }

    /**
     * Load all candidate lists for all apps
     */
    fun loadAllCandidateLists(): Map<String, List<ShutterButtonInfo>> {
        val json = prefs.getString(PreferencesKeys.Accessibility.CANDIDATE_LISTS, null)
            ?: return emptyMap()

        return try {
            val type = object : TypeToken<Map<String, List<ShutterButtonInfo>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading candidate lists", e)
            emptyMap()
        }
    }

    private fun loadUserPreferences(): MutableMap<String, ShutterButtonInfo> {
        val json = prefs.getString(PreferencesKeys.Accessibility.USER_PREFERRED_BUTTONS, null)
            ?: return mutableMapOf()

        return try {
            val type = object : TypeToken<MutableMap<String, ShutterButtonInfo>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user preferences", e)
            mutableMapOf()
        }
    }

    private fun loadAllDetectionStats(): Map<String, DetectionStats> {
        val json = prefs.getString(PreferencesKeys.Accessibility.DETECTION_STATS, null)
            ?: return emptyMap()

        return try {
            val type = object : TypeToken<Map<String, DetectionStats>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading detection stats", e)
            emptyMap()
        }
    }
}

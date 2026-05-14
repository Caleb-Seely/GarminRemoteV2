package com.garmin.android.apps.camera.click.comm.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persisting and retrieving shutter button information.
 * This class handles all SharedPreferences operations related to button detection.
 */
@Singleton
class ButtonPersistenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "ButtonPersistenceRepo"
        const val PREFS_NAME = "AccessibilityPrefs"
        const val KEY_BUTTON_INFO = "last_known_button_info"
        const val KEY_USER_PREFERRED_BUTTONS = "user_preferred_buttons"
        const val KEY_CANDIDATE_LISTS = "candidate_lists_by_app"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    /**
     * Save the last known button info to SharedPreferences
     */
    fun saveLastKnownButton(buttonInfo: ShutterButtonInfo) {
        try {
            val json = gson.toJson(buttonInfo)
            prefs.edit().putString(KEY_BUTTON_INFO, json).apply()
            Log.d(TAG, "Successfully saved last known button info for ${buttonInfo.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last known button info", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Load the last known button info from SharedPreferences
     */
    fun loadLastKnownButton(): ShutterButtonInfo? {
        return try {
            val json = prefs.getString(KEY_BUTTON_INFO, null)
            if (json != null) {
                gson.fromJson(json, ShutterButtonInfo::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading last known button info", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    /**
     * Save user-preferred button for a specific camera app
     */
    fun saveUserPreferredButton(packageName: String, buttonInfo: ShutterButtonInfo) {
        try {
            val allPreferred = loadAllUserPreferredButtons().toMutableMap()

            // Add timestamp and maximum confidence
            val enhancedButtonInfo = buttonInfo.copy(
                timestamp = System.currentTimeMillis(),
                confidenceScore = 100 // User-selected buttons get max confidence
            )

            allPreferred[packageName] = enhancedButtonInfo
            val json = gson.toJson(allPreferred)

            val success = prefs.edit().putString(KEY_USER_PREFERRED_BUTTONS, json).commit()
            if (success) {
                Log.d(TAG, "Successfully saved user preferred button for $packageName")
                FirebaseCrashlytics.getInstance().log("User preferred button saved for $packageName")
            } else {
                Log.e(TAG, "Failed to save user preferred button for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user preferred button for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Load user-preferred button for a specific camera app
     */
    fun loadUserPreferredButton(packageName: String): ShutterButtonInfo? {
        return try {
            val buttonInfo = loadAllUserPreferredButtons()[packageName]

            // Validate that button info has required data
            if (buttonInfo != null && buttonInfo.bounds.isEmpty) {
                Log.w(TAG, "User preferred button for $packageName has invalid bounds, ignoring")
                return null
            }

            buttonInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user preferred button for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    /**
     * Load all user-preferred buttons
     */
    fun loadAllUserPreferredButtons(): Map<String, ShutterButtonInfo> {
        val json = prefs.getString(KEY_USER_PREFERRED_BUTTONS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<Map<String, ShutterButtonInfo>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing user preferred buttons", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    /**
     * Save candidate lists for all camera apps
     */
    fun saveCandidateLists(candidatesByApp: Map<String, List<ShutterButtonInfo>>) {
        try {
            val json = gson.toJson(candidatesByApp)
            prefs.edit().putString(KEY_CANDIDATE_LISTS, json).apply()
            Log.d(TAG, "Successfully saved candidate lists for ${candidatesByApp.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving candidate lists", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Load candidate lists for all camera apps
     */
    fun loadCandidateLists(): Map<String, List<ShutterButtonInfo>> {
        val json = prefs.getString(KEY_CANDIDATE_LISTS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<Map<String, List<ShutterButtonInfo>>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing candidate lists", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    /**
     * Clear all stored button information
     */
    fun clearAll() {
        try {
            prefs.edit()
                .remove(KEY_BUTTON_INFO)
                .remove(KEY_USER_PREFERRED_BUTTONS)
                .remove(KEY_CANDIDATE_LISTS)
                .apply()
            Log.d(TAG, "Successfully cleared all stored button information")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing button information", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Clear user-preferred button for a specific app
     */
    fun clearUserPreferredButton(packageName: String) {
        try {
            val allPreferred = loadAllUserPreferredButtons().toMutableMap()
            allPreferred.remove(packageName)
            val json = gson.toJson(allPreferred)
            prefs.edit().putString(KEY_USER_PREFERRED_BUTTONS, json).apply()
            Log.d(TAG, "Successfully cleared user preferred button for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user preferred button for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}

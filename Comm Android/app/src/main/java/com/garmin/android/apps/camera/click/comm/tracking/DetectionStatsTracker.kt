package com.garmin.android.apps.camera.click.comm.tracking

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks detection statistics for learning and improvement.
 * Records which detection methods work best for each camera app.
 */
@Singleton
class DetectionStatsTracker @Inject constructor(
    private val context: Context
) {
    private companion object {
        const val TAG = "DetectionStatsTracker"
        const val PREFS_NAME = "AccessibilityPrefs"
        const val KEY_DETECTION_STATS = "detection_stats"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    /**
     * Data class for detection statistics per camera app
     */
    data class DetectionStats(
        val packageName: String,
        val successfulMethods: MutableMap<String, Int> = mutableMapOf(),
        val failedAttempts: MutableMap<String, Int> = mutableMapOf(),
        val lastSuccessfulMethod: String? = null,
        val totalAttempts: Int = 0,
        val successRate: Float = 0f
    )

    /**
     * Update detection statistics when a detection attempt completes
     */
    fun recordDetectionAttempt(packageName: String, method: String, success: Boolean) {
        try {
            val stats = loadStats(packageName)

            if (success) {
                stats.successfulMethods[method] = (stats.successfulMethods[method] ?: 0) + 1
            } else {
                stats.failedAttempts[method] = (stats.failedAttempts[method] ?: 0) + 1
            }

            val updatedStats = stats.copy(
                lastSuccessfulMethod = if (success) method else stats.lastSuccessfulMethod,
                totalAttempts = stats.totalAttempts + 1,
                successRate = calculateSuccessRate(stats)
            )

            saveStats(packageName, updatedStats)

            Log.d(TAG, "Updated stats for $packageName: method=$method, success=$success, " +
                    "total=${updatedStats.totalAttempts}, successRate=${updatedStats.successRate}")

        } catch (e: Exception) {
            Log.e(TAG, "Error recording detection attempt for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Get statistics for a specific camera app
     */
    fun getStats(packageName: String): DetectionStats {
        return loadStats(packageName)
    }

    /**
     * Get the most successful detection method for a specific camera app
     */
    fun getMostSuccessfulMethod(packageName: String): String? {
        val stats = loadStats(packageName)
        return stats.successfulMethods.maxByOrNull { it.value }?.key
    }

    /**
     * Get success rate for a specific method and camera app
     */
    fun getMethodSuccessRate(packageName: String, method: String): Float {
        val stats = loadStats(packageName)
        val successes = stats.successfulMethods[method] ?: 0
        val failures = stats.failedAttempts[method] ?: 0
        val total = successes + failures

        return if (total > 0) {
            successes.toFloat() / total.toFloat()
        } else {
            0f
        }
    }

    /**
     * Clear statistics for a specific camera app
     */
    fun clearStats(packageName: String) {
        try {
            val allStats = loadAllStats().toMutableMap()
            allStats.remove(packageName)
            saveAllStats(allStats)
            Log.d(TAG, "Cleared stats for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing stats for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Clear all statistics
     */
    fun clearAllStats() {
        try {
            prefs.edit().remove(KEY_DETECTION_STATS).apply()
            Log.d(TAG, "Cleared all detection statistics")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all stats", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Get statistics for all camera apps
     */
    fun getAllStats(): Map<String, DetectionStats> {
        return loadAllStats()
    }

    // Private helper methods

    private fun loadStats(packageName: String): DetectionStats {
        val allStats = loadAllStats()
        return allStats[packageName] ?: DetectionStats(packageName = packageName)
    }

    private fun saveStats(packageName: String, stats: DetectionStats) {
        val allStats = loadAllStats().toMutableMap()
        allStats[packageName] = stats
        saveAllStats(allStats)
    }

    private fun loadAllStats(): Map<String, DetectionStats> {
        val json = prefs.getString(KEY_DETECTION_STATS, null)
        return if (json != null) {
            try {
                // Parse as Map<String, Any> first to handle type conversion
                val rawMap = gson.fromJson(json, Map::class.java) as? Map<String, Any> ?: emptyMap()

                rawMap.mapNotNull { (packageName, statsData) ->
                    try {
                        val statsMap = statsData as? Map<*, *> ?: return@mapNotNull null

                        val successfulMethods = parseMethodMap(statsMap["successfulMethods"])
                        val failedAttempts = parseMethodMap(statsMap["failedAttempts"])
                        val lastSuccessfulMethod = statsMap["lastSuccessfulMethod"] as? String
                        val totalAttempts = (statsMap["totalAttempts"] as? Number)?.toInt() ?: 0
                        val successRate = (statsMap["successRate"] as? Number)?.toFloat() ?: 0f

                        packageName to DetectionStats(
                            packageName = packageName,
                            successfulMethods = successfulMethods,
                            failedAttempts = failedAttempts,
                            lastSuccessfulMethod = lastSuccessfulMethod,
                            totalAttempts = totalAttempts,
                            successRate = successRate
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing stats for $packageName", e)
                        null
                    }
                }.toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading all stats", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    private fun saveAllStats(stats: Map<String, DetectionStats>) {
        try {
            val json = gson.toJson(stats)
            prefs.edit().putString(KEY_DETECTION_STATS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving all stats", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun parseMethodMap(data: Any?): MutableMap<String, Int> {
        val result = mutableMapOf<String, Int>()
        val map = data as? Map<*, *> ?: return result

        map.forEach { (key, value) ->
            if (key is String) {
                val count = when (value) {
                    is Int -> value
                    is Double -> value.toInt()
                    is Number -> value.toInt()
                    else -> 0
                }
                result[key] = count
            }
        }

        return result
    }

    private fun calculateSuccessRate(stats: DetectionStats): Float {
        val totalSuccesses = stats.successfulMethods.values.sum()
        val totalFailures = stats.failedAttempts.values.sum()
        val total = totalSuccesses + totalFailures

        return if (total > 0) {
            totalSuccesses.toFloat() / total.toFloat()
        } else {
            0f
        }
    }
}

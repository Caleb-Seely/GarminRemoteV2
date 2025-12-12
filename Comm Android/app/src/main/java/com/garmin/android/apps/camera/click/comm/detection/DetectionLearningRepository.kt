package com.garmin.android.apps.camera.click.comm.detection

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.garmin.android.apps.camera.click.comm.config.PreferencesKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that learns from detection successes and failures.
 *
 * This implements a simple machine learning-like system that:
 * - Tracks which strategies work for each camera app
 * - Calculates success rates over time
 * - Provides adaptive strategy ordering
 * - Decays old data to adapt to app updates
 *
 * Think of this as a "memory" that makes the app smarter over time.
 */
@Singleton
class DetectionLearningRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private companion object {
        const val TAG = "DetectionLearning"
        const val KEY_STRATEGY_PERFORMANCE = "strategy_performance_v2"
        const val DECAY_FACTOR = 0.95f // Older data has less weight
        const val MIN_SAMPLES_FOR_CONFIDENCE = 3
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            PreferencesKeys.Files.ACCESSIBILITY_PREFS,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Record a successful detection
     */
    fun recordSuccess(packageName: String, strategyName: String, confidence: Float) {
        Log.d(TAG, "Recording success: $packageName -> $strategyName (confidence: $confidence)")

        val performance = loadPerformanceData()
        val key = "$packageName:$strategyName"

        val current = performance[key] ?: StrategyPerformanceData(
            packageName = packageName,
            strategyName = strategyName
        )

        val updated = current.copy(
            successCount = current.successCount + 1,
            totalAttempts = current.totalAttempts + 1,
            lastSuccessTimestamp = System.currentTimeMillis(),
            averageConfidence = calculateMovingAverage(
                current.averageConfidence,
                confidence,
                current.successCount
            )
        )

        performance[key] = updated
        savePerformanceData(performance)

        Log.d(TAG, "Updated performance: success rate = ${updated.successRate}, avg confidence = ${updated.averageConfidence}")
    }

    /**
     * Record a failed detection
     */
    fun recordFailure(packageName: String, strategyName: String) {
        Log.d(TAG, "Recording failure: $packageName -> $strategyName")

        val performance = loadPerformanceData()
        val key = "$packageName:$strategyName"

        val current = performance[key] ?: StrategyPerformanceData(
            packageName = packageName,
            strategyName = strategyName
        )

        val updated = current.copy(
            totalAttempts = current.totalAttempts + 1,
            lastFailureTimestamp = System.currentTimeMillis()
        )

        performance[key] = updated
        savePerformanceData(performance)

        Log.d(TAG, "Updated performance: success rate = ${updated.successRate}")
    }

    /**
     * Get strategy performance for a specific package
     */
    fun getStrategyPerformance(packageName: String): Map<String, StrategyPerformanceData> {
        val allPerformance = loadPerformanceData()

        return allPerformance
            .filterKeys { it.startsWith("$packageName:") }
            .mapKeys { (key, _) -> key.substringAfter(":") }
    }

    /**
     * Get detection stats for a package
     */
    fun getStats(packageName: String): DetectionStats {
        val performance = getStrategyPerformance(packageName)

        val successfulMethods = performance
            .filter { it.value.successCount > 0 }
            .mapValues { it.value.successCount }

        val failedAttempts = performance
            .filter { it.value.failureCount > 0 }
            .mapValues { it.value.failureCount }

        val lastSuccessful = performance
            .filter { it.value.lastSuccessTimestamp > 0 }
            .maxByOrNull { it.value.lastSuccessTimestamp }
            ?.key

        return DetectionStats(
            packageName = packageName,
            successfulMethods = successfulMethods,
            failedAttempts = failedAttempts,
            lastSuccessfulMethod = lastSuccessful
        )
    }

    /**
     * Clear learning data for a specific package (useful when app updates change UI)
     */
    fun clearPackageData(packageName: String) {
        Log.d(TAG, "Clearing learning data for $packageName")

        val performance = loadPerformanceData()
        val keysToRemove = performance.keys.filter { it.startsWith("$packageName:") }

        keysToRemove.forEach { performance.remove(it) }
        savePerformanceData(performance)
    }

    /**
     * Get top performing strategies for a package
     */
    fun getTopStrategies(packageName: String, limit: Int = 3): List<String> {
        return getStrategyPerformance(packageName)
            .filter { it.value.totalAttempts >= MIN_SAMPLES_FOR_CONFIDENCE }
            .toList()
            .sortedWith(
                compareByDescending<Pair<String, StrategyPerformanceData>> { it.second.successRate }
                    .thenByDescending { it.second.averageConfidence }
            )
            .take(limit)
            .map { it.first }
    }

    /**
     * Apply decay to old data (called periodically to reduce weight of old information)
     */
    fun applyDecay() {
        Log.d(TAG, "Applying decay to old performance data")

        val performance = loadPerformanceData()
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000L)

        performance.entries.forEach { (key, data) ->
            // Apply decay to data older than one week
            if (data.lastSuccessTimestamp < oneWeekAgo && data.lastFailureTimestamp < oneWeekAgo) {
                val decayed = data.copy(
                    successCount = (data.successCount * DECAY_FACTOR).toInt(),
                    totalAttempts = (data.totalAttempts * DECAY_FACTOR).toInt()
                )

                // Remove if decayed to zero
                if (decayed.totalAttempts == 0) {
                    performance.remove(key)
                } else {
                    performance[key] = decayed
                }
            }
        }

        savePerformanceData(performance)
    }

    private fun loadPerformanceData(): MutableMap<String, StrategyPerformanceData> {
        val json = prefs.getString(KEY_STRATEGY_PERFORMANCE, null) ?: return mutableMapOf()

        return try {
            val type = object : TypeToken<MutableMap<String, StrategyPerformanceData>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading performance data", e)
            mutableMapOf()
        }
    }

    private fun savePerformanceData(data: Map<String, StrategyPerformanceData>) {
        try {
            val json = gson.toJson(data)
            prefs.edit().putString(KEY_STRATEGY_PERFORMANCE, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving performance data", e)
        }
    }

    private fun calculateMovingAverage(currentAvg: Float, newValue: Float, count: Int): Float {
        if (count == 0) return newValue
        return (currentAvg * count + newValue) / (count + 1)
    }
}

/**
 * Performance data for a strategy on a specific package
 */
data class StrategyPerformanceData(
    val packageName: String,
    val strategyName: String,
    val successCount: Int = 0,
    val totalAttempts: Int = 0,
    val averageConfidence: Float = 0f,
    val lastSuccessTimestamp: Long = 0,
    val lastFailureTimestamp: Long = 0
) {
    val successRate: Float
        get() = if (totalAttempts > 0) successCount.toFloat() / totalAttempts else 0f

    val failureCount: Int
        get() = totalAttempts - successCount

    val hasEnoughSamples: Boolean
        get() = totalAttempts >= 3
}

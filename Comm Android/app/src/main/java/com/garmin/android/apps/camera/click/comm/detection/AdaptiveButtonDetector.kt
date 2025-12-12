package com.garmin.android.apps.camera.click.comm.detection

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptive Button Detector with Machine Learning-like capabilities.
 *
 * Key improvements over the original system:
 * 1. **Learns from Success**: Tracks which strategies work for each app and prioritizes them
 * 2. **Confidence Scoring**: Returns multiple candidates with confidence scores
 * 3. **Adaptive Strategy Ordering**: Dynamically reorders strategies based on historical performance
 * 4. **Smart Fallbacks**: Provides guided user selection when auto-detection fails
 * 5. **Visual Feedback**: Highlights detected buttons for user confirmation
 *
 * This replaces the monolithic findShutterButtonEnhanced() with a cleaner,
 * more maintainable, and more intelligent system.
 */
@Singleton
class AdaptiveButtonDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val strategies: List<@JvmSuppressWildcards ButtonDetectionStrategy>,
    private val learningRepository: DetectionLearningRepository,
    private val buttonRepository: ButtonLocationRepository,
    private val buttonCandidateCollector: ButtonCandidateCollector
) {

    private companion object {
        const val TAG = "AdaptiveButtonDetector"
        const val MIN_CONFIDENCE_THRESHOLD = 0.6f
        const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
    }

    /**
     * Detect shutter button with adaptive strategy ordering
     *
     * @param root Root accessibility node
     * @param packageName Camera app package name
     * @param options Detection options (confidence threshold, max candidates, etc.)
     * @return DetectionSession containing all candidates and the best result
     */
    fun detect(
        root: AccessibilityNodeInfo,
        packageName: String,
        options: DetectionOptions = DetectionOptions()
    ): DetectionSession {
        val startTime = System.currentTimeMillis()
        val detectionContext = createDetectionContext(packageName)

        Log.d(TAG, "=== ADAPTIVE DETECTION SESSION START ===")
        Log.d(TAG, "Package: $packageName")
        Log.d(TAG, "Confidence threshold: ${options.minConfidenceThreshold}")
        Log.d(TAG, "User preferred button: ${detectionContext.userPreferredButton?.let { "YES (${it.resourceId})" } ?: "NO"}")

        // Get historically successful strategies for this app
        val orderedStrategies = getAdaptiveStrategyOrder(packageName)
        Log.d(TAG, "Strategy order: ${orderedStrategies.map { it.name }}")

        val candidates = mutableListOf<DetectionCandidate>()
        val attemptedStrategies = mutableListOf<StrategyAttempt>()

        // Collect ALL clickable nodes for manual selection UI
        // This ensures users can see every button on screen, not just high-confidence ones
        try {
            val allCandidates = buttonCandidateCollector.collectCandidates(root, packageName)
            if (allCandidates.isNotEmpty()) {
                CameraAppCandidateStore.updateCandidatesForApp(context, packageName, allCandidates)
                Log.d(TAG, "Collected ${allCandidates.size} total clickable nodes for manual selection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect all candidates", e)
        }

        // Try each strategy in priority order
        for (strategy in orderedStrategies) {
            val strategyStartTime = System.currentTimeMillis()

            try {
                Log.d(TAG, "Trying strategy: ${strategy.name} (priority: ${strategy.priority})")

                val result = strategy.detect(root, packageName, detectionContext)
                val strategyDuration = System.currentTimeMillis() - strategyStartTime

                when (result) {
                    is DetectionResult.Success -> {
                        Log.d(TAG, "✓ ${strategy.name} succeeded (confidence: ${result.confidence})")

                        val candidate = DetectionCandidate(
                            node = result.node,
                            buttonInfo = result.buttonInfo,
                            confidence = result.confidence,
                            strategyName = strategy.name,
                            detectionTimeMs = strategyDuration
                        )
                        candidates.add(candidate)

                        attemptedStrategies.add(
                            StrategyAttempt(
                                strategyName = strategy.name,
                                success = true,
                                durationMs = strategyDuration,
                                confidence = result.confidence
                            )
                        )

                        // Always run SmartHeuristicStrategy to collect all buttons for manual selection
                        // Don't stop early - we want the full list even if we're confident
                        // This gives users a fallback to manually select if auto-detection fails
                    }
                    is DetectionResult.Failure -> {
                        Log.d(TAG, "✗ ${strategy.name} failed: ${result.reason}")
                        attemptedStrategies.add(
                            StrategyAttempt(
                                strategyName = strategy.name,
                                success = false,
                                durationMs = strategyDuration,
                                failureReason = result.reason
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in strategy ${strategy.name}", e)
                attemptedStrategies.add(
                    StrategyAttempt(
                        strategyName = strategy.name,
                        success = false,
                        durationMs = System.currentTimeMillis() - strategyStartTime,
                        failureReason = "Exception: ${e.message}"
                    )
                )
            }
        }

        // Sort candidates by confidence
        candidates.sortByDescending { it.confidence }

        val totalDuration = System.currentTimeMillis() - startTime

        // ALWAYS prefer user_preferred strategy when it succeeds
        // User's manual selection should override all automatic detection
        val bestCandidate = candidates.firstOrNull { it.strategyName == "user_preferred" && it.confidence >= options.minConfidenceThreshold }
            ?: candidates.firstOrNull { it.confidence >= options.minConfidenceThreshold }

        Log.d(TAG, "Detection complete: ${candidates.size} candidates found in ${totalDuration}ms")
        if (bestCandidate != null) {
            Log.d(TAG, "Best candidate: ${bestCandidate.strategyName} (confidence: ${bestCandidate.confidence})")
            Log.d(TAG, "Selected button resourceId: ${bestCandidate.buttonInfo.resourceId}")
            Log.d(TAG, "Selected button bounds: ${bestCandidate.buttonInfo.bounds}")

            // Learn from success
            learningRepository.recordSuccess(
                packageName = packageName,
                strategyName = bestCandidate.strategyName,
                confidence = bestCandidate.confidence
            )

            // Save for future use
            buttonRepository.saveButtonInfo(bestCandidate.buttonInfo)

            // Note: All candidates already saved earlier by ButtonCandidateCollector
            // This ensures the manual selection UI has ALL clickable nodes, not just strategy results
        } else {
            Log.d(TAG, "No candidate met confidence threshold")

            // Learn from failure
            attemptedStrategies.forEach { attempt ->
                if (!attempt.success) {
                    learningRepository.recordFailure(packageName, attempt.strategyName)
                }
            }
        }

        Log.d(TAG, "=== DETECTION SESSION END ===")

        return DetectionSession(
            packageName = packageName,
            candidates = candidates,
            bestCandidate = bestCandidate,
            attemptedStrategies = attemptedStrategies,
            totalDurationMs = totalDuration,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get strategies ordered by historical success for this app
     * Always prioritizes user preferred button strategy when a user preference exists
     */
    private fun getAdaptiveStrategyOrder(packageName: String): List<ButtonDetectionStrategy> {
        val performance = learningRepository.getStrategyPerformance(packageName)
        val hasUserPreference = buttonRepository.getUserPreferredButton(packageName) != null

        // Sort strategies by performance and priority
        val sortedStrategies = strategies.sortedWith(
            compareByDescending<ButtonDetectionStrategy> { strategy ->
                // Prioritize strategies with high success rate for this app
                performance[strategy.name]?.successRate ?: 0f
            }.thenByDescending { strategy ->
                // Then by base priority
                strategy.priority
            }
        )

        // If user preference exists, ensure UserPreferredButtonStrategy is always first
        if (hasUserPreference) {
            val userPrefStrategy = sortedStrategies.find { it.name == "user_preferred" }
            if (userPrefStrategy != null) {
                val otherStrategies = sortedStrategies.filterNot { it.name == "user_preferred" }
                Log.d(TAG, "User preference detected for $packageName - prioritizing UserPreferredButtonStrategy")
                return listOf(userPrefStrategy) + otherStrategies
            }
        }

        return sortedStrategies
    }

    /**
     * Create detection context with current state
     */
    private fun createDetectionContext(packageName: String): DetectionContext {
        return DetectionContext(
            userPreferredButton = buttonRepository.getUserPreferredButton(packageName),
            lastKnownButton = buttonRepository.getLastKnownButtonInfo(),
            screenDimensions = getScreenDimensions(),
            detectionStats = learningRepository.getStats(packageName)
        )
    }

    /**
     * Get actual screen dimensions from the device
     */
    private fun getScreenDimensions(): ScreenDimensions {
        val displayMetrics = context.resources.displayMetrics
        return ScreenDimensions(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
}

/**
 * Options for detection behavior
 */
data class DetectionOptions(
    /**
     * Minimum confidence threshold for accepting a candidate
     */
    val minConfidenceThreshold: Float = 0.6f,

    /**
     * Maximum number of candidates to collect before stopping
     */
    val maxCandidates: Int = 3,

    /**
     * Whether to show visual feedback for detected buttons
     */
    val showVisualFeedback: Boolean = false,

    /**
     * Whether to request user confirmation for low-confidence detections
     */
    val requestUserConfirmation: Boolean = false
)

/**
 * A single detection candidate
 */
data class DetectionCandidate(
    val node: AccessibilityNodeInfo,
    val buttonInfo: ShutterButtonInfo,
    val confidence: Float,
    val strategyName: String,
    val detectionTimeMs: Long
)

/**
 * Complete detection session results
 */
data class DetectionSession(
    val packageName: String,
    val candidates: List<DetectionCandidate>,
    val bestCandidate: DetectionCandidate?,
    val attemptedStrategies: List<StrategyAttempt>,
    val totalDurationMs: Long,
    val timestamp: Long
) {
    val isSuccessful: Boolean
        get() = bestCandidate != null

    val allCandidatesLowConfidence: Boolean
        get() = candidates.isNotEmpty() && candidates.all { it.confidence < 0.7f }
}

/**
 * Record of a strategy attempt
 */
data class StrategyAttempt(
    val strategyName: String,
    val success: Boolean,
    val durationMs: Long,
    val confidence: Float? = null,
    val failureReason: String? = null
)

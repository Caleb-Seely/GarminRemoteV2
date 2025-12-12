package com.garmin.android.apps.camera.click.comm.detection

import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

/**
 * Strategy interface for button detection algorithms.
 * Each implementation represents a different approach to finding the camera shutter button.
 *
 * This follows the Strategy pattern, allowing multiple detection methods to be
 * tried in order of priority without complex conditional logic.
 */
interface ButtonDetectionStrategy {
    /**
     * The priority of this strategy (higher = tried first)
     * Recommended values:
     * - 100: User preferences (highest priority)
     * - 80: Package-specific patterns
     * - 60: Last known successful detection
     * - 40: Content description matching
     * - 20: Largest clickable node heuristic
     * - 10: Comprehensive search (fallback)
     */
    val priority: Int

    /**
     * Name of the strategy for logging and analytics
     */
    val name: String

    /**
     * Attempt to detect the shutter button
     *
     * @param root The root accessibility node to search from
     * @param packageName The package name of the camera app
     * @param context Additional context needed for detection
     * @return DetectionResult indicating success or failure
     */
    fun detect(
        root: AccessibilityNodeInfo,
        packageName: String,
        context: DetectionContext
    ): DetectionResult
}

/**
 * Context provided to detection strategies
 */
data class DetectionContext(
    /**
     * User's manually selected preferred button for this package
     */
    val userPreferredButton: ShutterButtonInfo?,

    /**
     * Last button that was successfully detected and used
     */
    val lastKnownButton: ShutterButtonInfo?,

    /**
     * Screen dimensions for position calculations
     */
    val screenDimensions: ScreenDimensions,

    /**
     * Historical detection statistics
     */
    val detectionStats: DetectionStats? = null
)

/**
 * Screen dimensions for position-based detection
 */
data class ScreenDimensions(
    val width: Int,
    val height: Int
)

/**
 * Detection statistics for learning patterns
 */
data class DetectionStats(
    val packageName: String,
    val successfulMethods: Map<String, Int> = emptyMap(),
    val failedAttempts: Map<String, Int> = emptyMap(),
    val lastSuccessfulMethod: String? = null
)

/**
 * Result of a button detection attempt
 */
sealed class DetectionResult {
    /**
     * Button was successfully detected
     *
     * @param node The detected accessibility node
     * @param confidence Confidence score (0.0 to 1.0)
     * @param method Name of the detection method used
     * @param buttonInfo Complete button information for persistence
     */
    data class Success(
        val node: AccessibilityNodeInfo,
        val confidence: Float,
        val method: String,
        val buttonInfo: ShutterButtonInfo
    ) : DetectionResult()

    /**
     * Button detection failed
     *
     * @param reason Human-readable reason for failure
     */
    data class Failure(val reason: String) : DetectionResult()
}

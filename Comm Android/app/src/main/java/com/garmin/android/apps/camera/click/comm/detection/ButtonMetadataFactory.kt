package com.garmin.android.apps.camera.click.comm.detection

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ButtonMetadataFactory"

/**
 * ButtonMetadataFactory - Creates ShutterButtonInfo with complete metadata
 *
 * This class is responsible for creating ShutterButtonInfo instances from AccessibilityNodeInfo
 * nodes, enriching them with all available metadata including position, size, confidence scores,
 * and detection method information.
 *
 * Centralizes all button metadata creation logic that was previously scattered across
 * AccessibilityUtils.
 *
 * @param screenMetrics Helper for calculating screen-relative positions
 */
@Singleton
class ButtonMetadataFactory @Inject constructor(
    private val screenMetrics: ScreenMetricsHelper
) {

    /**
     * Create a complete ShutterButtonInfo from an AccessibilityNodeInfo
     *
     * Extracts all available metadata from the node and enriches it with calculated fields
     * like position on screen, confidence score, and button size information.
     *
     * @param node The accessibility node representing the button
     * @param packageName The package name of the app containing this button
     * @param detectionMethod The method used to detect this button (e.g., "package_specific", "content_description")
     * @return Complete ShutterButtonInfo with all metadata
     */
    fun createButtonInfo(
        node: AccessibilityNodeInfo,
        packageName: String,
        detectionMethod: String
    ): ShutterButtonInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Calculate derived metadata
        val buttonSize = "${bounds.width()}x${bounds.height()}"
        val isSquare = isSquareNode(node)
        val positionOnScreen = screenMetrics.calculatePositionOnScreen(bounds)
        val confidenceScore = calculateConfidenceScore(node, detectionMethod)
        val screenOrientation = screenMetrics.getScreenOrientation()

        // Extract node properties
        val contentDescription = node.contentDescription?.toString()
        val resourceId = node.viewIdResourceName
        val className = node.className?.toString()
        val text = node.text?.toString()

        Log.d(TAG, "Created button info: method=$detectionMethod, confidence=$confidenceScore, " +
                "pos=$positionOnScreen, size=$buttonSize, square=$isSquare, pkg=$packageName")

        return ShutterButtonInfo(
            bounds = bounds,
            packageName = packageName,
            contentDescription = contentDescription,
            resourceId = resourceId,
            className = className,
            text = text,
            detectionMethod = detectionMethod,
            confidenceScore = confidenceScore,
            timestamp = System.currentTimeMillis(),
            screenOrientation = screenOrientation,
            buttonSize = buttonSize,
            isSquare = isSquare,
            positionOnScreen = positionOnScreen
        )
    }

    /**
     * Calculate a confidence score (0-100) based on node properties and detection method
     *
     * Higher scores indicate more confidence that this is the correct shutter button.
     * Scoring factors:
     * - Detection method (package-specific = highest, fallback = lowest)
     * - Square shape (typical for camera buttons)
     * - Has content description
     * - Has resource ID
     * - Button size (larger buttons score higher)
     *
     * @param node The accessibility node to score
     * @param detectionMethod The method used to detect this button
     * @return Confidence score from 0-100
     */
    private fun calculateConfidenceScore(
        node: AccessibilityNodeInfo,
        detectionMethod: String
    ): Int {
        var score = 0

        // Base score from detection method (0-40 points)
        score += when (detectionMethod) {
            "saved_location" -> 40  // Highest confidence - user previously selected
            "package_specific" -> 35  // High confidence - matches known pattern
            "content_description" -> 30  // Medium-high - has descriptive text
            "position_based" -> 25  // Medium - common camera button position
            "comprehensive" -> 20  // Medium-low - multi-criteria match
            "largest_node" -> 15  // Lower - fallback method
            else -> 10  // Lowest - unknown method
        }

        // Square shape bonus (0-20 points)
        if (isSquareNode(node)) {
            score += 20
        }

        // Has content description (0-15 points)
        if (!node.contentDescription.isNullOrEmpty()) {
            score += 15
        }

        // Has resource ID (0-10 points)
        if (!node.viewIdResourceName.isNullOrEmpty()) {
            score += 10
        }

        // Button size scoring (0-15 points)
        // Larger buttons are more likely to be shutter buttons
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val area = bounds.width() * bounds.height()
        val screenArea = screenMetrics.getScreenWidth() * screenMetrics.getScreenHeight()
        val areaRatio = area.toFloat() / screenArea

        score += when {
            areaRatio > 0.05 -> 15  // Very large button (>5% of screen)
            areaRatio > 0.03 -> 12  // Large button (>3% of screen)
            areaRatio > 0.01 -> 8   // Medium button (>1% of screen)
            areaRatio > 0.005 -> 5  // Small button (>0.5% of screen)
            else -> 0               // Very small button
        }

        // Ensure score is within 0-100 range
        return score.coerceIn(0, 100)
    }

    /**
     * Check if a node is square-shaped
     *
     * Camera shutter buttons are typically square or nearly square.
     * Allows for 10% tolerance to account for rounding and different screen densities.
     *
     * @param node The accessibility node to check
     * @return true if the node is square-shaped within tolerance
     */
    private fun isSquareNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val width = bounds.width()
        val height = bounds.height()

        if (width == 0 || height == 0) return false

        val ratio = width.toFloat() / height.toFloat()

        // Allow 10% tolerance (ratio between 0.9 and 1.1)
        return ratio in 0.9f..1.1f
    }

    /**
     * Create a ShutterButtonInfo for a saved/preferred button
     *
     * This is a convenience method for creating button info from user-selected buttons
     * with maximum confidence score.
     *
     * @param node The accessibility node representing the button
     * @param packageName The package name of the app
     * @return ShutterButtonInfo with saved_location detection method and high confidence
     */
    fun createSavedButtonInfo(
        node: AccessibilityNodeInfo,
        packageName: String
    ): ShutterButtonInfo {
        return createButtonInfo(node, packageName, "saved_location")
    }
}

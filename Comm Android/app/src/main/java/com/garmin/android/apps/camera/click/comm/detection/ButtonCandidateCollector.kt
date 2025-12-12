package com.garmin.android.apps.camera.click.comm.detection

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.CameraDetectionUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ButtonCandidateCollector"

/**
 * ButtonCandidateCollector - Collect all potential button candidates for manual selection
 *
 * This class collects all clickable nodes that could potentially be shutter buttons
 * and enriches them with metadata for display in the manual selection UI.
 *
 * Each candidate is:
 * - Validated using CameraDetectionUtils to get a confidence score
 * - Enriched with position, size, and identification metadata
 * - Sorted by confidence score (highest first)
 *
 * This is used for the manual shutter button selection activity where users can
 * choose the correct button if automatic detection fails.
 *
 * @param traverser Utility for traversing accessibility node tree
 * @param screenMetrics Helper for calculating screen positions
 */
@Singleton
class ButtonCandidateCollector @Inject constructor(
    private val traverser: NodeTraverser,
    private val screenMetrics: ScreenMetricsHelper
) {

    /**
     * Collect all clickable node candidates that could be shutter buttons
     *
     * Traverses the entire accessibility tree, collects all clickable nodes,
     * validates each one, and creates ShutterButtonInfo for each candidate.
     *
     * The returned list is sorted by confidence score (highest first) to help
     * users identify the most likely correct button.
     *
     * @param root The root accessibility node to search from
     * @param packageName The package name of the camera app
     * @return List of all button candidates, sorted by confidence score
     */
    fun collectCandidates(
        root: AccessibilityNodeInfo,
        packageName: String
    ): List<ShutterButtonInfo> {
        Log.d(TAG, "Collecting button candidates for $packageName")

        val candidates = mutableListOf<ShutterButtonInfo>()
        val screenWidth = screenMetrics.getScreenWidth()
        val screenHeight = screenMetrics.getScreenHeight()

        traverser.traverse(root) { node ->
            if (!node.isClickable) return@traverse

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Validate the node and get confidence score
            val validation = CameraDetectionUtils.validateShutterButton(node, packageName)

            // Calculate position on screen
            val positionOnScreen = screenMetrics.calculatePositionOnScreen(bounds)

            // Create button info for this candidate
            val buttonInfo = ShutterButtonInfo(
                bounds = bounds,
                packageName = packageName,
                contentDescription = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                className = node.className?.toString(),
                text = node.text?.toString(),
                confidenceScore = validation.score,
                detectionMethod = null, // Not detected by a specific method
                positionOnScreen = positionOnScreen,
                screenOrientation = screenMetrics.getScreenOrientation(),
                buttonSize = "${bounds.width()}x${bounds.height()}",
                isSquare = isSquare(bounds)
            )

            candidates.add(buttonInfo)
        }

        // Sort by confidence score (highest first)
        val sortedCandidates = candidates.sortedByDescending { it.confidenceScore }

        Log.d(TAG, "Collected ${sortedCandidates.size} candidates")
        if (sortedCandidates.isNotEmpty()) {
            Log.d(TAG, "Top candidate score: ${sortedCandidates.first().confidenceScore}")
        }

        return sortedCandidates
    }

    /**
     * Check if bounds represent a square shape
     *
     * @param bounds The bounds to check
     * @return true if width equals height
     */
    private fun isSquare(bounds: Rect): Boolean {
        return bounds.width() == bounds.height()
    }
}

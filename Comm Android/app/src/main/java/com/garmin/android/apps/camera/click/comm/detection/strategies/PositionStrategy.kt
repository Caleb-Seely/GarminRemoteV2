package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.NodeTraverser
import com.garmin.android.apps.camera.click.comm.detection.ScreenMetricsHelper
import com.garmin.android.apps.camera.click.comm.detection.ButtonMetadataFactory
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PositionStrategy"

/**
 * PositionStrategy - Find button by screen position
 *
 * This strategy searches for shutter buttons based on their typical screen position.
 * Most camera apps place the shutter button in the bottom center area of the screen,
 * approximately 80% down from the top.
 *
 * Searches for clickable nodes within a vertical tolerance range of the expected
 * position. When multiple candidates are found, selects the largest button.
 *
 * @param traverser Utility for traversing accessibility node tree
 * @param screenMetrics Helper for calculating screen dimensions
 * @param metadataFactory Factory for creating button metadata
 */
@Singleton
class PositionStrategy @Inject constructor(
    private val traverser: NodeTraverser,
    private val screenMetrics: ScreenMetricsHelper,
    private val metadataFactory: ButtonMetadataFactory
) {

    /**
     * Find button by position (bottom center of screen)
     *
     * Searches for clickable nodes positioned around 80% down the screen with
     * a 10% tolerance. This matches the typical placement of shutter buttons
     * in most camera applications.
     *
     * @param root The root accessibility node to search from
     * @param packageName The package name of the camera app
     * @return The best matching button node, or null if none found
     */
    fun findButton(
        root: AccessibilityNodeInfo,
        packageName: String
    ): AccessibilityNodeInfo? {
        Log.d(TAG, "Searching for button by position in $packageName")

        val screenHeight = screenMetrics.getScreenHeight()
        val bottomCenterY = screenHeight * 0.8f  // 80% down the screen
        val tolerance = screenHeight * 0.1f       // 10% tolerance

        val candidates = findCandidatesAtPosition(root, bottomCenterY, tolerance)

        if (candidates.isEmpty()) {
            Log.d(TAG, "No candidates found at expected position")
            return null
        }

        Log.d(TAG, "Found ${candidates.size} candidates by position")

        // Return the largest candidate (shutter buttons are typically large)
        return selectBestCandidate(candidates)
    }

    /**
     * Find all clickable nodes within the vertical position tolerance range
     *
     * @param root The root node to search from
     * @param targetY The target Y position (center of expected range)
     * @param tolerance The vertical tolerance (plus/minus range)
     * @return List of all matching nodes
     */
    private fun findCandidatesAtPosition(
        root: AccessibilityNodeInfo,
        targetY: Float,
        tolerance: Float
    ): List<AccessibilityNodeInfo> {
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        traverser.traverse(root) { node ->
            if (!node.isClickable) return@traverse

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerY = bounds.centerY()

            // Check if node is within the vertical tolerance range
            if (centerY >= targetY - tolerance && centerY <= targetY + tolerance) {
                candidates.add(node)
                Log.d(TAG, "Found candidate by position: centerY=$centerY")
            }
        }

        return candidates
    }

    /**
     * Select the best candidate from a list of matching nodes
     *
     * Uses button size as the primary selection criteria - larger buttons are
     * more likely to be the main shutter button.
     *
     * @param candidates List of candidate nodes
     * @return The best matching node
     */
    private fun selectBestCandidate(
        candidates: List<AccessibilityNodeInfo>
    ): AccessibilityNodeInfo? {
        return candidates.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val area = bounds.width() * bounds.height()

            Log.d(TAG, "Candidate area: $area, centerY: ${bounds.centerY()}")
            area
        }
    }
}

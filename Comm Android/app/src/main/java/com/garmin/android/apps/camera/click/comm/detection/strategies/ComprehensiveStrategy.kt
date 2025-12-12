package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.NodeTraverser
import com.garmin.android.apps.camera.click.comm.detection.ButtonMetadataFactory
import com.garmin.android.apps.camera.click.comm.utils.CameraDetectionUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ComprehensiveStrategy"

/**
 * ComprehensiveStrategy - Multi-criteria button detection
 *
 * This strategy performs a comprehensive search by collecting all clickable nodes
 * and validating each against multiple criteria including:
 * - Size and shape characteristics
 * - Position on screen
 * - Content description and resource ID patterns
 * - Camera app-specific patterns
 *
 * Uses CameraDetectionUtils.validateShutterButton() to score each candidate
 * and selects the highest-scoring button.
 *
 * This is a more thorough but slower detection method, useful when simpler
 * strategies have failed.
 *
 * @param traverser Utility for traversing accessibility node tree
 * @param metadataFactory Factory for creating button metadata
 */
@Singleton
class ComprehensiveStrategy @Inject constructor(
    private val traverser: NodeTraverser,
    private val metadataFactory: ButtonMetadataFactory
) {

    /**
     * Comprehensive button search that collects all candidates and picks the best one
     *
     * Traverses the entire accessibility tree, collects all clickable nodes,
     * validates each candidate using CameraDetectionUtils, and returns the
     * highest-scoring valid button.
     *
     * @param root The root accessibility node to search from
     * @param packageName The package name of the camera app
     * @return The best validated button node, or null if none found
     */
    fun findButton(
        root: AccessibilityNodeInfo,
        packageName: String
    ): AccessibilityNodeInfo? {
        Log.d(TAG, "Starting comprehensive search in $packageName")

        // Collect all clickable nodes
        val candidates = traverser.findAll(root) { node ->
            node.isClickable
        }

        Log.d(TAG, "Found ${candidates.size} clickable nodes")

        // Filter and rank candidates using validation
        val validCandidates = candidates.mapNotNull { node ->
            val validation = CameraDetectionUtils.validateShutterButton(node, packageName)
            if (validation.isValid) {
                node to validation.score
            } else null
        }

        if (validCandidates.isEmpty()) {
            Log.d(TAG, "No valid candidates found after validation")
            return null
        }

        // Select the highest-scoring candidate
        val bestCandidate = validCandidates.maxByOrNull { it.second }

        Log.d(TAG, "Comprehensive search found ${validCandidates.size} valid candidates, " +
                "best score: ${bestCandidate?.second}")

        return bestCandidate?.first
    }
}

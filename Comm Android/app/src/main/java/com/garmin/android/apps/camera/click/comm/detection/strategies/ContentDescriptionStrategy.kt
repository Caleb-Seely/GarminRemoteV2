package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.NodeTraverser
import com.garmin.android.apps.camera.click.comm.detection.ButtonMetadataFactory
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContentDescStrategy"

/**
 * ContentDescriptionStrategy - Find button by content description patterns
 *
 * This strategy searches for shutter buttons by matching content description text
 * against known shutter button keywords like "shutter", "take photo", "capture", etc.
 *
 * Content descriptions are accessibility labels that app developers provide for
 * screen readers. Many camera apps properly label their shutter buttons with
 * descriptive text.
 *
 * When multiple candidates are found, selects the largest button as shutter buttons
 * are typically prominent UI elements.
 *
 * @param traverser Utility for traversing accessibility node tree
 * @param metadataFactory Factory for creating button metadata
 */
@Singleton
class ContentDescriptionStrategy @Inject constructor(
    private val traverser: NodeTraverser,
    private val metadataFactory: ButtonMetadataFactory
) {

    /**
     * Keywords that typically appear in shutter button content descriptions
     */
    private val shutterKeywords = listOf(
        "shutter",
        "take photo",
        "camera shutter",
        "capture",
        "shoot"
    )

    /**
     * Find button by matching content description against known shutter keywords
     *
     * Searches all clickable nodes for content descriptions containing shutter-related
     * keywords. If multiple matches are found, returns the largest button.
     *
     * @param root The root accessibility node to search from
     * @param packageName The package name of the camera app
     * @return The best matching button node, or null if none found
     */
    fun findButton(
        root: AccessibilityNodeInfo,
        packageName: String
    ): AccessibilityNodeInfo? {
        Log.d(TAG, "Searching for button by content description in $packageName")

        val candidates = findAllCandidates(root)

        if (candidates.isEmpty()) {
            Log.d(TAG, "No candidates found by content description")
            return null
        }

        Log.d(TAG, "Found ${candidates.size} candidates by content description")

        // Return the largest candidate (shutter buttons are typically large)
        return selectBestCandidate(candidates)
    }

    /**
     * Find all clickable nodes with content descriptions matching shutter keywords
     *
     * @param root The root node to search from
     * @return List of all matching nodes
     */
    private fun findAllCandidates(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        traverser.traverse(root) { node ->
            if (!node.isClickable) return@traverse

            val contentDesc = node.contentDescription?.toString()?.lowercase()

            if (contentDesc != null && matchesShutterKeyword(contentDesc)) {
                candidates.add(node)
                Log.d(TAG, "Found candidate by content description: $contentDesc")
            }
        }

        return candidates
    }

    /**
     * Check if a content description contains any shutter-related keywords
     *
     * @param contentDesc The content description to check (should be lowercase)
     * @return true if the description matches a shutter keyword
     */
    private fun matchesShutterKeyword(contentDesc: String): Boolean {
        return shutterKeywords.any { keyword ->
            contentDesc.contains(keyword)
        }
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

            Log.d(TAG, "Candidate area: $area, desc: ${node.contentDescription}")
            area
        }
    }
}

package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.NodeTraverser
import com.garmin.android.apps.camera.click.comm.detection.ScreenMetricsHelper
import com.garmin.android.apps.camera.click.comm.detection.ButtonMetadataFactory
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LargestButtonFallback"

/**
 * LargestButtonFallbackStrategy - Fallback to largest clickable square node
 *
 * This is a last-resort detection strategy that finds the largest square-shaped
 * clickable button on the screen. Camera shutter buttons are typically:
 * - Large (prominent UI elements)
 * - Square or nearly square in shape
 * - Clickable
 *
 * This strategy is less precise than other methods but provides a reasonable
 * fallback when app-specific patterns and heuristics fail.
 *
 * Square shape detection allows for 10% tolerance to account for different
 * screen densities and rounding.
 *
 * @param traverser Utility for traversing accessibility node tree
 * @param screenMetrics Helper for screen dimension calculations
 * @param metadataFactory Factory for creating button metadata
 */
@Singleton
class LargestButtonFallbackStrategy @Inject constructor(
    private val traverser: NodeTraverser,
    private val screenMetrics: ScreenMetricsHelper,
    private val metadataFactory: ButtonMetadataFactory
) {

    /**
     * Find the largest square clickable node on the screen
     *
     * Traverses all nodes looking for clickable, square-shaped buttons
     * and returns the one with the largest area.
     *
     * @param root The root accessibility node to search from
     * @param packageName The package name of the camera app
     * @return The largest square clickable node, or null if none found
     */
    fun findButton(
        root: AccessibilityNodeInfo,
        packageName: String
    ): AccessibilityNodeInfo? {
        Log.d(TAG, "Starting largest button fallback search in $packageName")

        var largestNode: AccessibilityNodeInfo? = null
        var largestArea = 0

        traverser.traverse(root) { node ->
            if (!node.isClickable) return@traverse
            if (!isSquareNode(node)) return@traverse

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val area = bounds.width() * bounds.height()

            if (area > largestArea) {
                largestArea = area
                largestNode = node
            }
        }

        if (largestNode != null) {
            val bounds = Rect()
            largestNode?.getBoundsInScreen(bounds)
            Log.d(TAG, "Largest square button found: area=$largestArea, bounds=$bounds")
        } else {
            Log.d(TAG, "No square clickable nodes found")
        }

        return largestNode
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
}

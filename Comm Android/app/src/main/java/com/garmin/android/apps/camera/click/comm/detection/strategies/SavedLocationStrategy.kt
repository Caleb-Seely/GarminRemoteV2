package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.NodeTraverser
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SavedLocationStrategy"

/**
 * SavedLocationStrategy - Find button using previously saved location
 *
 * This strategy attempts to find a shutter button at a location that was previously
 * detected or selected by the user. This is typically the fastest and most reliable
 * method when the button location hasn't changed.
 *
 * Uses an in-memory cache of the last known button location to quickly find the
 * button without traversing the entire accessibility tree.
 *
 * @param traverser Utility for traversing accessibility node tree
 */
@Singleton
class SavedLocationStrategy @Inject constructor(
    private val traverser: NodeTraverser
) {

    /**
     * In-memory cache of the last known button location
     * This is a runtime cache only, not persisted to disk
     */
    private var lastKnownButtonInfo: ShutterButtonInfo? = null

    /**
     * Find button using saved location from cache
     *
     * Searches for a clickable node at the exact bounds that were previously saved.
     * This is a precise match - the bounds must be identical.
     *
     * @param root The root accessibility node to search from
     * @return The button node if found at saved location, null otherwise
     */
    fun findButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val savedInfo = lastKnownButtonInfo

        if (savedInfo == null) {
            Log.d(TAG, "No saved location available")
            return null
        }

        Log.d(TAG, "Trying saved location: ${savedInfo.bounds}")

        return findClickableNodeAtLocation(root, savedInfo.bounds)
    }

    /**
     * Find a clickable node at the specified location in the accessibility tree
     *
     * Traverses the tree looking for a clickable node with bounds that exactly
     * match the provided bounds.
     *
     * @param root The root AccessibilityNodeInfo to start traversal from
     * @param bounds The exact bounds to search for
     * @return The found AccessibilityNodeInfo or null if not found
     */
    private fun findClickableNodeAtLocation(
        root: AccessibilityNodeInfo,
        bounds: Rect
    ): AccessibilityNodeInfo? {
        return traverser.findFirst(root) { node ->
            if (!node.isClickable) return@findFirst false

            val nodeBounds = Rect()
            node.getBoundsInScreen(nodeBounds)
            nodeBounds == bounds
        }
    }

    /**
     * Save button location to in-memory cache
     *
     * This saves the button info for quick retrieval in future detection attempts.
     * Note: This is an in-memory cache only - for persistent storage, use
     * ButtonLocationRepository.
     *
     * @param buttonInfo The button information to cache
     */
    fun saveLocation(buttonInfo: ShutterButtonInfo) {
        lastKnownButtonInfo = buttonInfo
        Log.d(TAG, "Saved button location: ${buttonInfo.bounds}, pkg=${buttonInfo.packageName}")
    }

    /**
     * Get the currently saved button info from cache
     *
     * @return The cached button info, or null if none is cached
     */
    fun getSavedLocation(): ShutterButtonInfo? {
        return lastKnownButtonInfo
    }

    /**
     * Clear the saved location from cache
     *
     * Useful when switching camera apps or when the saved location is no longer valid
     */
    fun clearSavedLocation() {
        Log.d(TAG, "Clearing saved location")
        lastKnownButtonInfo = null
    }
}

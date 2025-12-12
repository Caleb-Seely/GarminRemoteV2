package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.ButtonDetectionStrategy
import com.garmin.android.apps.camera.click.comm.detection.DetectionContext
import com.garmin.android.apps.camera.click.comm.detection.DetectionResult
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.ButtonReliabilityUtils
import javax.inject.Inject

/**
 * Strategy that uses the user's manually selected preferred button.
 *
 * This has the highest priority because the user explicitly chose this button.
 * When users manually select a button (via the manual selection activity),
 * it gets saved and this strategy will always try it first.
 */
class UserPreferredButtonStrategy @Inject constructor() : ButtonDetectionStrategy {

    override val priority: Int = 100
    override val name: String = "user_preferred"

    private companion object {
        const val TAG = "UserPreferredStrategy"
        const val POSITION_TOLERANCE = 50 // pixels
    }

    override fun detect(
        root: AccessibilityNodeInfo,
        packageName: String,
        context: DetectionContext
    ): DetectionResult {
        val userButton = context.userPreferredButton

        if (userButton == null) {
            Log.d(TAG, "No user preferred button set for $packageName")
            return DetectionResult.Failure("No user preferred button configured")
        }

        if (userButton.packageName != packageName) {
            Log.d(TAG, "User preferred button is for different package: ${userButton.packageName}")
            return DetectionResult.Failure("User preferred button is for different package")
        }

        Log.d(TAG, "Searching for user preferred button (resourceId: ${userButton.resourceId}, position: (${userButton.bounds.centerX()}, ${userButton.bounds.centerY()}))")

        // Try multiple methods to find the button, in order of reliability:
        // 1. By resource ID (most reliable)
        // 2. By content description
        // 3. By position (fallback)

        var node: AccessibilityNodeInfo? = null
        var matchMethod = ""

        // Method 1: Find by resource ID
        if (userButton.resourceId != null && !userButton.resourceId.isEmpty()) {
            node = findNodeByResourceId(root, userButton.resourceId)
            if (node != null) {
                matchMethod = "resource_id"
                Log.d(TAG, "Found button by resource ID: ${userButton.resourceId}")
            }
        }

        // Method 2: Find by content description
        if (node == null && userButton.contentDescription != null && !userButton.contentDescription.isEmpty()) {
            node = findNodeByContentDescription(root, userButton.contentDescription)
            if (node != null) {
                matchMethod = "content_description"
                Log.d(TAG, "Found button by content description: ${userButton.contentDescription}")
            }
        }

        // Method 3: Find by position (fallback)
        if (node == null) {
            node = findNodeAtPosition(root, userButton.bounds.centerX(), userButton.bounds.centerY())
            if (node != null) {
                matchMethod = "position"
                Log.d(TAG, "Found button by position")
            }
        }

        if (node == null) {
            Log.d(TAG, "No node found using any method")
            return DetectionResult.Failure("Button not found at saved position")
        }

        // Validate the button is still valid
        val validation = ButtonReliabilityUtils.validateButtonReliability(node, packageName)
        if (!validation.isValid) {
            Log.d(TAG, "User preferred button failed validation: ${validation.issues}")
            // Node recycling is handled automatically by the system
            return DetectionResult.Failure("Button at saved position failed validation")
        }

        Log.d(TAG, "✓ Found user preferred button via $matchMethod (score: ${validation.score})")

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Update button info with current state
        val updatedButtonInfo = userButton.copy(
            bounds = bounds,
            timestamp = System.currentTimeMillis(),
            resourceId = node.viewIdResourceName,
            contentDescription = node.contentDescription?.toString()
        )

        // Maximum possible confidence because user explicitly selected this
        // Using 1.0f ensures user preference ALWAYS wins over automatic detection
        // (other strategies max out at 1.0f, and user_preferred runs first in strategy order)
        val confidence = 1.0f

        return DetectionResult.Success(
            node = node,
            confidence = confidence,
            method = name,
            buttonInfo = updatedButtonInfo
        )
    }

    /**
     * Find node by resource ID
     */
    private fun findNodeByResourceId(
        root: AccessibilityNodeInfo,
        resourceId: String
    ): AccessibilityNodeInfo? {
        fun searchRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.viewIdResourceName == resourceId && node.isClickable && node.isEnabled) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val result = searchRecursive(child)
                    if (result != null) {
                        return result
                    }
                }
            }
            return null
        }

        return searchRecursive(root)
    }

    /**
     * Find node by content description
     */
    private fun findNodeByContentDescription(
        root: AccessibilityNodeInfo,
        contentDescription: String
    ): AccessibilityNodeInfo? {
        fun searchRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.contentDescription?.toString() == contentDescription && node.isClickable && node.isEnabled) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val result = searchRecursive(child)
                    if (result != null) {
                        return result
                    }
                }
            }
            return null
        }

        return searchRecursive(root)
    }

    /**
     * Find an accessibility node at or near the specified position
     */
    private fun findNodeAtPosition(
        root: AccessibilityNodeInfo,
        targetX: Int,
        targetY: Int
    ): AccessibilityNodeInfo? {
        val bounds = Rect()

        // First try exact position
        var bestMatch: AccessibilityNodeInfo? = null
        var bestDistance = Int.MAX_VALUE

        fun searchRecursive(node: AccessibilityNodeInfo) {
            if (!node.isClickable || !node.isEnabled) {
                // Still search children
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        searchRecursive(child)
                        // Node recycling is handled automatically by the system
                    }
                }
                return
            }

            node.getBoundsInScreen(bounds)
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            // Check if position is within bounds
            if (bounds.contains(targetX, targetY)) {
                bestMatch = node
                bestDistance = 0
                return // Found exact match
            }

            // Calculate distance from target
            val distance = Math.sqrt(
                Math.pow((centerX - targetX).toDouble(), 2.0) +
                Math.pow((centerY - targetY).toDouble(), 2.0)
            ).toInt()

            if (distance < bestDistance && distance < POSITION_TOLERANCE) {
                // Node recycling is handled automatically by the system
                bestMatch = node
                bestDistance = distance
            }

            // Search children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    searchRecursive(child)
                    // Node recycling is handled automatically by the system
                }
            }
        }

        searchRecursive(root)
        return bestMatch
    }
}

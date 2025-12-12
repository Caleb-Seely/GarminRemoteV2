package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.ButtonDetectionStrategy
import com.garmin.android.apps.camera.click.comm.detection.CameraAppPatternRegistry
import com.garmin.android.apps.camera.click.comm.detection.DetectionContext
import com.garmin.android.apps.camera.click.comm.detection.DetectionResult
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.ButtonReliabilityUtils
import javax.inject.Inject

/**
 * Detection strategy that uses package-specific patterns.
 *
 * This strategy leverages known patterns for specific camera apps
 * (resource IDs, content descriptions, etc.) to find the shutter button.
 */
class PackageSpecificStrategy @Inject constructor(
    private val patternRegistry: CameraAppPatternRegistry
) : ButtonDetectionStrategy {

    override val priority: Int = 80
    override val name: String = "package_specific"

    private companion object {
        const val TAG = "PackageSpecificStrategy"
    }

    override fun detect(
        root: AccessibilityNodeInfo,
        packageName: String,
        context: DetectionContext
    ): DetectionResult {
        Log.d(TAG, "Attempting package-specific detection for $packageName")

        val pattern = patternRegistry.getPattern(packageName)
        if (pattern == null) {
            Log.d(TAG, "No pattern registered for $packageName")
            return DetectionResult.Failure("No pattern registered for package")
        }

        // Try to find by resource ID first
        pattern.resourceIds.forEach { resourceId ->
            val node = findByResourceId(root, resourceId)
            if (node != null) {
                val validation = ButtonReliabilityUtils.validateButtonReliability(node, packageName)
                if (validation.isValid) {
                    Log.d(TAG, "Found button by resource ID: $resourceId (score: ${validation.score})")
                    return createSuccessResult(node, packageName, "resource_id:$resourceId", validation.score)
                } else {
                    Log.d(TAG, "Button found by resource ID but failed validation: ${validation.issues}")
                    // Node recycling is handled automatically by the system
                }
            }
        }

        // Try to find by content description
        pattern.contentDescriptions.forEach { desc ->
            val node = findByContentDescription(root, desc)
            if (node != null) {
                val validation = ButtonReliabilityUtils.validateButtonReliability(node, packageName)
                if (validation.isValid) {
                    Log.d(TAG, "Found button by content description: $desc (score: ${validation.score})")
                    return createSuccessResult(node, packageName, "content_desc:$desc", validation.score)
                } else {
                    Log.d(TAG, "Button found by content desc but failed validation: ${validation.issues}")
                    // Node recycling is handled automatically by the system
                }
            }
        }

        // Try to find by position if specified
        pattern.positions.forEach { position ->
            val node = findByPosition(root, position, context.screenDimensions.width, context.screenDimensions.height)
            if (node != null) {
                val validation = ButtonReliabilityUtils.validateButtonReliability(node, packageName)
                if (validation.isValid && ButtonReliabilityUtils.isLikelyShutterButton(node)) {
                    Log.d(TAG, "Found button by position: $position (score: ${validation.score})")
                    return createSuccessResult(node, packageName, "position:$position", validation.score)
                } else {
                    // Node recycling is handled automatically by the system
                }
            }
        }

        Log.d(TAG, "Package-specific detection failed for $packageName")
        return DetectionResult.Failure("No button found matching package patterns")
    }

    private fun findByResourceId(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        return findNodeRecursive(root) { node ->
            node.viewIdResourceName?.contains(resourceId) == true
        }
    }

    private fun findByContentDescription(root: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        return findNodeRecursive(root) { node ->
            node.contentDescription?.toString()?.contains(description, ignoreCase = true) == true
        }
    }

    private fun findByPosition(
        root: AccessibilityNodeInfo,
        position: String,
        screenWidth: Int,
        screenHeight: Int
    ): AccessibilityNodeInfo? {
        val bounds = Rect()

        return findNodeRecursive(root) { node ->
            if (!node.isClickable || !node.isEnabled) return@findNodeRecursive false

            node.getBoundsInScreen(bounds)
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            when (position) {
                "bottom_center", "center_bottom" -> {
                    val isInBottomHalf = centerY > screenHeight * 0.5f
                    val isInCenterThird = centerX > screenWidth * 0.33f && centerX < screenWidth * 0.67f
                    isInBottomHalf && isInCenterThird
                }
                else -> false
            }
        }
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, predicate)
            if (result != null) {
                // Node recycling is handled automatically by the system
                return result
            }
            // Node recycling is handled automatically by the system
        }

        return null
    }

    private fun createSuccessResult(
        node: AccessibilityNodeInfo,
        packageName: String,
        method: String,
        score: Int
    ): DetectionResult.Success {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val buttonInfo = ShutterButtonInfo(
            bounds = bounds,
            packageName = packageName,
            detectionMethod = method,
            timestamp = System.currentTimeMillis(),
            resourceId = node.viewIdResourceName,
            contentDescription = node.contentDescription?.toString()
        )

        val confidence = (score / 100f).coerceIn(0f, 1f)

        return DetectionResult.Success(
            node = node,
            confidence = confidence,
            method = method,
            buttonInfo = buttonInfo
        )
    }
}

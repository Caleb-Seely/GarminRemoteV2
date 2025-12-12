package com.garmin.android.apps.camera.click.comm.detection.strategies

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.ButtonDetectionStrategy
import com.garmin.android.apps.camera.click.comm.detection.DetectionContext
import com.garmin.android.apps.camera.click.comm.detection.DetectionResult
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.ButtonReliabilityUtils
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Smart heuristic strategy that combines multiple detection methods.
 *
 * This strategy uses a scoring system to evaluate ALL clickable nodes and
 * selects the one most likely to be the shutter button. It combines:
 * - Content description matching
 * - Resource ID matching
 * - Position heuristics (bottom-center)
 * - Size heuristics (larger buttons)
 * - Last known button location
 * - Shape analysis (square buttons)
 *
 * This replaces the need for multiple separate strategies by intelligently
 * combining all heuristics into one comprehensive scoring system.
 */
class SmartHeuristicStrategy @Inject constructor(
    @ApplicationContext private val context: Context
) : ButtonDetectionStrategy {

    override val priority: Int = 50
    override val name: String = "smart_heuristic"

    private companion object {
        const val TAG = "SmartHeuristicStrategy"

        // Scoring weights
        const val WEIGHT_CONTENT_DESC = 40
        const val WEIGHT_RESOURCE_ID = 35
        const val WEIGHT_POSITION = 15
        const val WEIGHT_SIZE = 5
        const val WEIGHT_LAST_KNOWN = 20
        const val WEIGHT_SHAPE = 5

        // Shutter button keywords
        val SHUTTER_KEYWORDS = listOf(
            "shutter", "capture", "take", "photo", "record", "shoot",
            "camera", "snap", "click"
        )

        val RESOURCE_ID_KEYWORDS = listOf(
            "shutter", "capture", "camera", "btn", "button"
        )

        // Keywords to avoid (camera switch, settings, etc.)
        val AVOID_KEYWORDS = listOf(
            "switch", "flip", "front", "back", "selfie", "toggle",
            "settings", "menu", "mode", "flash", "timer", "filter",
            "gallery", "preview"
        )
    }

    override fun detect(
        root: AccessibilityNodeInfo,
        packageName: String,
        detectionContext: DetectionContext
    ): DetectionResult {
        Log.d(TAG, "Running smart heuristic detection for $packageName")

        val candidates = mutableListOf<ScoredCandidate>()

        // Scan all clickable nodes and score them
        scanAndScoreNodes(root, packageName, detectionContext, candidates)

        if (candidates.isEmpty()) {
            Log.d(TAG, "No clickable nodes found")
            return DetectionResult.Failure("No clickable nodes found")
        }

        // Sort by score (highest first)
        candidates.sortByDescending { it.score }

        Log.d(TAG, "Found ${candidates.size} candidates")
        candidates.take(5).forEach { candidate ->
            Log.d(TAG, "  - Score: ${candidate.score}, Method: ${candidate.scoringDetails}")
        }

        // Save all candidates to CameraAppCandidateStore for manual selection UI
        val buttonInfoList = candidates.take(10).map { candidate ->
            val bounds = Rect()
            candidate.node.getBoundsInScreen(bounds)
            ShutterButtonInfo(
                bounds = bounds,
                packageName = packageName,
                contentDescription = candidate.node.contentDescription?.toString(),
                resourceId = candidate.node.viewIdResourceName,
                className = candidate.node.className?.toString(),
                text = candidate.node.text?.toString(),
                detectionMethod = "smart_heuristic",
                confidenceScore = candidate.score,
                timestamp = System.currentTimeMillis()
            )
        }
        CameraAppCandidateStore.updateCandidatesForApp(context, packageName, buttonInfoList)
        Log.d(TAG, "Saved ${buttonInfoList.size} candidates for manual selection")

        // Validate top candidate
        val topCandidate = candidates.first()
        val validation = ButtonReliabilityUtils.validateButtonReliability(
            topCandidate.node,
            packageName
        )

        if (!validation.isValid) {
            Log.d(TAG, "Top candidate failed validation: ${validation.issues}")
            // Node recycling is handled automatically by the system
            return DetectionResult.Failure("Top candidate failed validation")
        }

        // Check if it's likely a camera switch button (should avoid)
        if (ButtonReliabilityUtils.isLikelyCameraSwitchButton(topCandidate.node)) {
            Log.d(TAG, "Top candidate appears to be camera switch button, trying next")

            // Try second candidate
            if (candidates.size > 1) {
                val secondCandidate = candidates[1]
                val secondValidation = ButtonReliabilityUtils.validateButtonReliability(
                    secondCandidate.node,
                    packageName
                )

                if (secondValidation.isValid &&
                    !ButtonReliabilityUtils.isLikelyCameraSwitchButton(secondCandidate.node)) {
                    // Node recycling is handled automatically by the system
                    return createSuccessResult(secondCandidate, packageName, validation.score)
                }
            }
        }

        // Node recycling is handled automatically by the system

        Log.d(TAG, "✓ Selected button with score: ${topCandidate.score}")

        return createSuccessResult(topCandidate, packageName, validation.score)
    }

    private fun scanAndScoreNodes(
        node: AccessibilityNodeInfo,
        packageName: String,
        context: DetectionContext,
        candidates: MutableList<ScoredCandidate>
    ) {
        // Only consider clickable and enabled nodes
        if (node.isClickable && node.isEnabled) {
            val score = scoreNode(node, packageName, context)
            if (score > 0) {
                candidates.add(ScoredCandidate(node, score, "combined_heuristics"))
            }
        }

        // Recursively scan children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanAndScoreNodes(child, packageName, context, candidates)
            // Node recycling is handled automatically by the system
        }
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        packageName: String,
        context: DetectionContext
    ): Int {
        var score = 0
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 1. Content description matching (40 points)
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        if (contentDesc != null) {
            if (SHUTTER_KEYWORDS.any { contentDesc.contains(it) }) {
                score += WEIGHT_CONTENT_DESC
            }
            // Penalize avoid keywords
            if (AVOID_KEYWORDS.any { contentDesc.contains(it) }) {
                score -= 30
            }
        }

        // 2. Resource ID matching (35 points)
        val resourceId = node.viewIdResourceName?.lowercase()
        if (resourceId != null) {
            if (RESOURCE_ID_KEYWORDS.any { resourceId.contains(it) }) {
                score += WEIGHT_RESOURCE_ID
            }
            // Penalize avoid keywords
            if (AVOID_KEYWORDS.any { resourceId.contains(it) }) {
                score -= 25
            }
        }

        // 3. Position heuristics (15 points) - bottom center area
        val screenWidth = context.screenDimensions.width
        val screenHeight = context.screenDimensions.height
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        val isInBottomHalf = centerY > screenHeight * 0.5f
        val isInCenterThird = centerX > screenWidth * 0.33f && centerX < screenWidth * 0.67f

        if (isInBottomHalf && isInCenterThird) {
            score += WEIGHT_POSITION
        }

        // Extra points for bottom-center position (typical shutter button location)
        if (centerY > screenHeight * 0.7f &&
            centerX > screenWidth * 0.4f && centerX < screenWidth * 0.6f) {
            score += 5
        }

        // 4. Size heuristics (5 points) - larger buttons are more likely
        val area = bounds.width() * bounds.height()
        if (area > 10000) { // Reasonable button size
            score += WEIGHT_SIZE
        }

        // 5. Last known button proximity (20 points)
        val lastKnown = context.lastKnownButton
        if (lastKnown != null && lastKnown.packageName == packageName) {
            val distance = Math.sqrt(
                Math.pow((centerX - lastKnown.bounds.centerX()).toDouble(), 2.0) +
                Math.pow((centerY - lastKnown.bounds.centerY()).toDouble(), 2.0)
            ).toInt()

            // Close to last known position
            if (distance < 100) {
                score += WEIGHT_LAST_KNOWN
            } else if (distance < 200) {
                score += WEIGHT_LAST_KNOWN / 2
            }
        }

        // 6. Shape analysis (5 points) - square buttons are common
        if (bounds.width() == bounds.height()) {
            score += WEIGHT_SHAPE
        }

        return score
    }

    private fun createSuccessResult(
        candidate: ScoredCandidate,
        packageName: String,
        validationScore: Int
    ): DetectionResult.Success {
        val bounds = Rect()
        candidate.node.getBoundsInScreen(bounds)

        val buttonInfo = ShutterButtonInfo(
            bounds = bounds,
            packageName = packageName,
            detectionMethod = name,
            timestamp = System.currentTimeMillis(),
            resourceId = candidate.node.viewIdResourceName,
            contentDescription = candidate.node.contentDescription?.toString()
        )

        // Confidence based on score (normalize to 0-1)
        val maxPossibleScore = WEIGHT_CONTENT_DESC + WEIGHT_RESOURCE_ID +
                              WEIGHT_POSITION + WEIGHT_SIZE +
                              WEIGHT_LAST_KNOWN + WEIGHT_SHAPE
        val normalizedScore = (candidate.score.toFloat() / maxPossibleScore).coerceIn(0f, 1f)

        // Boost confidence if validation score is high
        val confidenceBoost = if (validationScore >= 80) 0.1f else 0f
        val finalConfidence = (normalizedScore + confidenceBoost).coerceIn(0f, 1f)

        return DetectionResult.Success(
            node = candidate.node,
            confidence = finalConfidence,
            method = name,
            buttonInfo = buttonInfo
        )
    }

    private data class ScoredCandidate(
        val node: AccessibilityNodeInfo,
        val score: Int,
        val scoringDetails: String
    )
}

package com.garmin.android.apps.camera.click.comm.service.handlers

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.garmin.android.apps.camera.click.comm.detection.AdaptiveButtonDetector
import com.garmin.android.apps.camera.click.comm.detection.DetectionOptions
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.ButtonReliabilityUtils
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ShutterActionHandler"

/**
 * Handles all shutter button detection and triggering operations.
 * This class is responsible for:
 * - Finding shutter buttons using adaptive detection
 * - Triggering shutter button clicks
 * - Logging analytics for button detection and clicks
 */
@Singleton
class ShutterActionHandler @Inject constructor(
    private val buttonDetector: AdaptiveButtonDetector
) {
    /**
     * Result of a shutter action attempt.
     */
    data class ShutterActionResult(
        val success: Boolean,
        val method: String? = null,
        val error: String? = null,
        val responseTimeMs: Long = 0
    )

    /**
     * Attempts to find the shutter button with retry logic.
     *
     * @param root The root accessibility node
     * @param packageName The camera app package name
     * @param context The application context for showing toasts
     * @param maxRetries Maximum number of retry attempts (default: 2)
     * @return The AccessibilityNodeInfo of the shutter button if found, null otherwise
     */
    fun findShutterButton(
        root: AccessibilityNodeInfo,
        packageName: String,
        context: Context,
        maxRetries: Int = 2
    ): AccessibilityNodeInfo? {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt <= maxRetries) {
            try {
                val result = findShutterButtonInternal(root, packageName, context)
                if (result != null) {
                    if (attempt > 0) {
                        Log.d(TAG, "Button detection succeeded on retry attempt $attempt")
                        FirebaseCrashlytics.getInstance().log("Detection succeeded on retry $attempt for $packageName")
                    }
                    return result
                }

                // If no button found and we have retries left, wait before retrying
                if (attempt < maxRetries) {
                    val delayMs = (50L * (1 shl attempt)) // Exponential backoff: 50ms, 100ms
                    Log.d(TAG, "No button found, retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)")
                    Thread.sleep(delayMs)
                }
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "Error during button detection attempt $attempt", e)
                if (attempt < maxRetries) {
                    val delayMs = (50L * (1 shl attempt))
                    Thread.sleep(delayMs)
                }
            }
            attempt++
        }

        // All retries exhausted
        if (lastError != null) {
            Log.e(TAG, "All $maxRetries retry attempts failed for button detection")
            FirebaseCrashlytics.getInstance().recordException(lastError)
        }
        return null
    }

    /**
     * Internal method for finding the shutter button using the adaptive detection system.
     * This is separated to allow retry logic in the public method.
     */
    private fun findShutterButtonInternal(
        root: AccessibilityNodeInfo,
        packageName: String,
        context: Context
    ): AccessibilityNodeInfo? {
        return try {
            Log.d(TAG, "Attempting adaptive shutter button detection for $packageName")
            FirebaseCrashlytics.getInstance().log("Finding shutter button for $packageName")

            // Use the adaptive detection system
            val detectionOptions = DetectionOptions(
                minConfidenceThreshold = 0.6f,
                maxCandidates = 3,
                showVisualFeedback = false,
                requestUserConfirmation = false
            )

            val session = buttonDetector.detect(root, packageName, detectionOptions)

            if (session.isSuccessful && session.bestCandidate != null) {
                val candidate = session.bestCandidate
                Log.d(TAG, """
                    ✓ Adaptive detection successful!
                    Strategy: ${candidate.strategyName}
                    Confidence: ${candidate.confidence}
                    Detection time: ${candidate.detectionTimeMs}ms
                    Total candidates: ${session.candidates.size}
                """.trimIndent())

                showToast(context, "Found button (${(candidate.confidence * 100).toInt()}% confident)")

                // Log button details
                val bounds = Rect()
                candidate.node.getBoundsInScreen(bounds)
                Log.d(TAG, """
                    Shutter button details:
                    Package: $packageName
                    Resource ID: ${candidate.buttonInfo.resourceId}
                    Content Description: ${candidate.buttonInfo.contentDescription}
                    Bounds: $bounds
                    Clickable: ${candidate.node.isClickable}
                """.trimIndent())

                FirebaseCrashlytics.getInstance().log(
                    "Adaptive detection succeeded: $packageName via ${candidate.strategyName}"
                )

                // Log analytics
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = candidate.strategyName,
                    success = true,
                    buttonInfo = candidate.node,
                    validationScore = (candidate.confidence * 100).toInt(),
                    detectionTimeMs = session.totalDurationMs
                )

                candidate.node
            } else {
                Log.d(TAG, "Adaptive detection failed to find suitable button")
                if (session.candidates.isNotEmpty()) {
                    Log.d(TAG, "Found ${session.candidates.size} candidates but all below confidence threshold")
                    showToast(context, "Found buttons but unsure which is correct")
                } else {
                    showToast(context, "No shutter button found")
                }

                FirebaseCrashlytics.getInstance().log("Adaptive detection failed for $packageName")

                // Log analytics
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = "adaptive_failed",
                    success = false,
                    buttonInfo = null,
                    validationScore = 0,
                    detectionTimeMs = session.totalDurationMs
                )

                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during button detection - accessibility permissions may be missing", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            showToast(context, "Permission error - check accessibility settings")
            null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state during button detection - window may have closed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            showToast(context, "Window state error - please try again")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during button detection for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            showToast(context, "Detection error - please try again")
            null
        }
    }

    /**
     * Attempts to trigger the camera shutter by clicking the provided button.
     *
     * @param button The AccessibilityNodeInfo of the shutter button
     * @param packageName The camera app package name
     * @param deviceName The Garmin device name (for analytics)
     * @param context The application context
     * @param messageReceivedTime Timestamp when message was received (for response time calculation)
     * @return ShutterActionResult indicating success/failure
     */
    fun triggerShutter(
        button: AccessibilityNodeInfo,
        packageName: String,
        deviceName: String?,
        context: Context,
        messageReceivedTime: Long
    ): ShutterActionResult {
        return try {
            Log.d(TAG, "Attempting to trigger shutter button")
            FirebaseCrashlytics.getInstance().log("Attempting to trigger shutter button")

            // Validate button is still valid
            if (!button.isClickable || !button.isEnabled) {
                Log.e(TAG, "Button is no longer clickable or enabled")
                return ShutterActionResult(
                    success = false,
                    error = "Button is no longer clickable or enabled"
                )
            }

            // Get the button's bounds
            val buttonBounds = Rect()
            button.getBoundsInScreen(buttonBounds)
            Log.d(TAG, "Button bounds: $buttonBounds")

            // Try primary click methods
            val clickResult = ButtonReliabilityUtils.performReliableClick(button, packageName)

        if (clickResult.success) {
            Log.d(TAG, "Button click successful with method: ${clickResult.method}")
            FirebaseCrashlytics.getInstance().log("Shutter button clicked successfully with ${clickResult.method}")

            // Log successful click analytics
            AnalyticsUtils.logShutterButtonClick(
                packageName = packageName,
                buttonInfo = button,
                clickMethod = clickResult.method,
                success = true,
                responseTimeMs = clickResult.duration,
                deviceName = deviceName
            )

            // Calculate and log response time
            val responseTime = System.currentTimeMillis() - messageReceivedTime
            logResponseTime(context, deviceName ?: "unknown_device", responseTime, clickResult.method)

            return ShutterActionResult(
                success = true,
                method = clickResult.method,
                responseTimeMs = responseTime
            )
        }

        Log.e(TAG, "Primary click methods failed: ${clickResult.error}")

        // Try child button clicks as fallback
        val childResult = tryChildButtonClicks(button, packageName, deviceName, context, messageReceivedTime)
        if (childResult.success) {
            return childResult
        }

        // All methods failed
        Log.e(TAG, "All click methods failed")
        FirebaseCrashlytics.getInstance().log("All click methods failed for $packageName")

            AnalyticsUtils.logShutterButtonClick(
                packageName = packageName,
                buttonInfo = button,
                clickMethod = "all_methods_failed",
                success = false,
                responseTimeMs = clickResult.duration,
                deviceName = deviceName
            )

            ShutterActionResult(
                success = false,
                error = "All click methods failed: ${clickResult.error}"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during button trigger", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            ShutterActionResult(
                success = false,
                error = "Permission error during click"
            )
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state during button trigger - button may have become invalid", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            ShutterActionResult(
                success = false,
                error = "Button state error - window may have changed"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during button trigger for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            ShutterActionResult(
                success = false,
                error = "Unexpected error during click: ${e.message}"
            )
        }
    }

    /**
     * Attempts to click child buttons as a fallback strategy.
     */
    private fun tryChildButtonClicks(
        button: AccessibilityNodeInfo,
        packageName: String,
        deviceName: String?,
        context: Context,
        messageReceivedTime: Long
    ): ShutterActionResult {
        try {
            for (i in 0 until button.childCount) {
                val child = button.getChild(i) ?: continue

                Log.d(TAG, "Attempting click on child button $i")
                val childClickStartTime = System.currentTimeMillis()
                val childClickResult = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val childClickTime = System.currentTimeMillis() - childClickStartTime

                if (childClickResult) {
                    Log.d(TAG, "Child button click successful")

                    // Log successful child click analytics
                    AnalyticsUtils.logShutterButtonClick(
                        packageName = packageName,
                        buttonInfo = child,
                        clickMethod = "child_click_$i",
                        success = true,
                        responseTimeMs = childClickTime,
                        deviceName = deviceName
                    )

                    // Calculate and log response time
                    val responseTime = System.currentTimeMillis() - messageReceivedTime
                    logResponseTime(context, deviceName ?: "unknown_device", responseTime, "child_click")

                    return ShutterActionResult(
                        success = true,
                        method = "child_click_$i",
                        responseTimeMs = responseTime
                    )
                } else {
                    // Log failed child click
                    AnalyticsUtils.logShutterButtonClick(
                        packageName = packageName,
                        buttonInfo = child,
                        clickMethod = "child_click_$i",
                        success = false,
                        responseTimeMs = childClickTime,
                        deviceName = deviceName
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during child button click", e)
            AnalyticsUtils.logShutterButtonClick(
                packageName = packageName,
                buttonInfo = button,
                clickMethod = "child_click_exception",
                success = false,
                deviceName = deviceName
            )
        }

        return ShutterActionResult(success = false, error = "Child button clicks failed")
    }

    /**
     * Logs the response time for shutter button clicks.
     */
    private fun logResponseTime(context: Context, deviceName: String, timeMs: Long, method: String) {
        val bundle = Bundle().apply {
            putString("device_name", deviceName)
            putLong("time_spent_ms", timeMs)
            putString("method", method)
        }
        FirebaseAnalytics.getInstance(context).logEvent("shutter_response_time", bundle)
    }

    /**
     * Shows a toast message to the user.
     */
    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Finds an alternative shutter button when the primary detection finds a camera switch button.
     */
    fun findAlternativeShutterButton(
        root: AccessibilityNodeInfo,
        packageName: String,
        excludeButton: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val excludeBounds = Rect()
        excludeButton.getBoundsInScreen(excludeBounds)

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)

                // Skip the button we want to exclude
                if (nodeBounds != excludeBounds) {
                    // Prefer buttons that look like shutter buttons
                    if (ButtonReliabilityUtils.isLikelyShutterButton(node)) {
                        candidates.add(node)
                        Log.d(TAG, "Found likely shutter button candidate: ${node.contentDescription}")
                    } else if (!ButtonReliabilityUtils.isLikelyCameraSwitchButton(node)) {
                        // Add other clickable buttons as fallback, but not camera switch buttons
                        candidates.add(node)
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                // Node recycling is handled automatically by the system
            }
        }

        traverse(root)

        // Return the best candidate based on validation score
        return candidates.maxByOrNull { candidate ->
            val validation = ButtonReliabilityUtils.validateButtonReliability(candidate, packageName)
            validation.score
        }
    }
}

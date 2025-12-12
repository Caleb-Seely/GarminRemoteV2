package com.garmin.android.apps.camera.click.comm.utils

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Utility class focused on improving button detection and click reliability
 */
object ButtonReliabilityUtils {
    private const val TAG = "ButtonReliabilityUtils"
    
    /**
     * Enhanced button validation that checks multiple criteria
     */
    fun validateButtonReliability(node: AccessibilityNodeInfo, packageName: String): ButtonValidationResult {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        var score = 0
        val issues = mutableListOf<String>()
        
        // Check if button is visible and has reasonable size
        if (bounds.width() < 50 || bounds.height() < 50) {
            issues.add("Button too small (${bounds.width()}x${bounds.height()})")
        } else {
            score += 20
        }
        
        // Check if button is clickable
        if (!node.isClickable) {
            issues.add("Node is not clickable")
        } else {
            score += 30
        }
        
        // Check if button is enabled
        if (!node.isEnabled) {
            issues.add("Node is not enabled")
        } else {
            score += 20
        }
        
        // Check for meaningful content description or resource ID
        val hasContentDesc = !node.contentDescription.isNullOrBlank()
        val hasResourceId = !node.viewIdResourceName.isNullOrBlank()
        
        if (hasContentDesc || hasResourceId) {
            score += 15
        } else {
            issues.add("No content description or resource ID")
        }
        
        // Check button position (prefer buttons in bottom half of screen for cameras)
        val screenHeight = 1920 // Default screen height
        if (bounds.centerY() > screenHeight * 0.5) {
            score += 10
        }
        
        // Check if button is square (common for camera buttons)
        if (bounds.width() == bounds.height()) {
            score += 5
        }
        
        return ButtonValidationResult(
            isValid = score >= 70,
            score = score,
            issues = issues
        )
    }
    
    /**
     * Attempts multiple click strategies with delays and retries
     * Note: This is now synchronous and uses a simple approach.
     * For complex async strategies, consider using a callback-based approach.
     */
    fun performReliableClick(node: AccessibilityNodeInfo, packageName: String): ClickResult {
        // Direct click strategy
        try {
            Log.d(TAG, "Attempting direct_click for $packageName")
            val startTime = System.currentTimeMillis()
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val duration = System.currentTimeMillis() - startTime

            if (success) {
                Log.d(TAG, "direct_click succeeded for $packageName in ${duration}ms")
                FirebaseCrashlytics.getInstance().log("Successful click with direct_click for $packageName")

                return ClickResult(
                    success = true,
                    method = "direct_click",
                    duration = duration,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during direct_click for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        // Long click strategy as fallback
        try {
            Log.d(TAG, "Attempting long_click for $packageName")
            val startTime = System.currentTimeMillis()
            val success = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            val duration = System.currentTimeMillis() - startTime

            if (success) {
                Log.d(TAG, "long_click succeeded for $packageName in ${duration}ms")
                FirebaseCrashlytics.getInstance().log("Successful click with long_click for $packageName")

                return ClickResult(
                    success = true,
                    method = "long_click",
                    duration = duration,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during long_click for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        return ClickResult(
            success = false,
            method = "all_failed",
            duration = 0,
            error = "All click strategies failed"
        )
    }

    /**
     * Performs a click with async retry strategies using Handler.
     * Use this when you need more complex retry logic with delays.
     */
    fun performReliableClickAsync(
        node: AccessibilityNodeInfo,
        packageName: String,
        callback: (ClickResult) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())

        // Try direct click first
        try {
            Log.d(TAG, "Attempting direct_click for $packageName")
            val startTime = System.currentTimeMillis()
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val duration = System.currentTimeMillis() - startTime

            if (success) {
                Log.d(TAG, "direct_click succeeded for $packageName in ${duration}ms")
                FirebaseCrashlytics.getInstance().log("Successful click with direct_click for $packageName")
                callback(ClickResult(true, "direct_click", duration, null))
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during direct_click for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        // Try focus then click with delay
        handler.postDelayed({
            try {
                Log.d(TAG, "Attempting focus_then_click for $packageName")
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                handler.postDelayed({
                    try {
                        val startTime = System.currentTimeMillis()
                        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        val duration = System.currentTimeMillis() - startTime

                        if (success) {
                            Log.d(TAG, "focus_then_click succeeded for $packageName")
                            FirebaseCrashlytics.getInstance().log("Successful click with focus_then_click for $packageName")
                            callback(ClickResult(true, "focus_then_click", duration, null))
                        } else {
                            // Final fallback - long click
                            tryLongClickFallback(node, packageName, callback)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during focus_then_click for $packageName", e)
                        FirebaseCrashlytics.getInstance().recordException(e)
                        tryLongClickFallback(node, packageName, callback)
                    }
                }, 100)

            } catch (e: Exception) {
                Log.e(TAG, "Exception during focus for $packageName", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                tryLongClickFallback(node, packageName, callback)
            }
        }, 50)
    }

    private fun tryLongClickFallback(
        node: AccessibilityNodeInfo,
        packageName: String,
        callback: (ClickResult) -> Unit
    ) {
        try {
            Log.d(TAG, "Attempting long_click fallback for $packageName")
            val startTime = System.currentTimeMillis()
            val success = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            val duration = System.currentTimeMillis() - startTime

            if (success) {
                Log.d(TAG, "long_click succeeded for $packageName")
                FirebaseCrashlytics.getInstance().log("Successful click with long_click for $packageName")
                callback(ClickResult(true, "long_click", duration, null))
            } else {
                callback(ClickResult(false, "all_failed", 0, "All click strategies failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during long_click for $packageName", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            callback(ClickResult(false, "all_failed", 0, "All click strategies failed: ${e.message}"))
        }
    }
    
    /**
     * Checks if a button is likely to be a camera switch button (which we want to avoid)
     */
    fun isLikelyCameraSwitchButton(node: AccessibilityNodeInfo): Boolean {
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        val resourceId = node.viewIdResourceName?.lowercase()
        
        val switchKeywords = listOf(
            "switch", "flip", "front", "back", "selfie", "toggle", "camera_switch", 
            "switch_camera", "flip_camera", "reverse"
        )
        
        return switchKeywords.any { keyword ->
            contentDesc?.contains(keyword) == true || resourceId?.contains(keyword) == true
        }
    }
    
    /**
     * Checks if a button is likely to be a capture/shutter button
     */
    fun isLikelyShutterButton(node: AccessibilityNodeInfo): Boolean {
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        val resourceId = node.viewIdResourceName?.lowercase()

        val shutterKeywords = listOf(
            "shutter", "capture", "take", "photo", "record", "shoot", "camera_capture",
            "btn_capture", "shutter_button", "take_photo", "start_capture"
        )

        return shutterKeywords.any { keyword ->
            contentDesc?.contains(keyword) == true || resourceId?.contains(keyword) == true
        }
    }

    data class ClickResult(
        val success: Boolean,
        val method: String,
        val duration: Long,
        val error: String?
    )

    data class ButtonValidationResult(
        val isValid: Boolean,
        val score: Int,
        val issues: List<String>
    )
}
package com.garmin.android.apps.camera.click.comm.utils

import android.graphics.Rect
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
     */
    fun performReliableClick(node: AccessibilityNodeInfo, packageName: String): ClickResult {
        val strategies = listOf(
            ClickStrategy("direct_click", 0) { 
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK) 
            },
            ClickStrategy("focus_then_click", 100) { 
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                Thread.sleep(100)
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK) 
            },
            ClickStrategy("double_click", 50) { 
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(50)
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK) 
            },
            ClickStrategy("long_click", 0) { 
                node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) 
            }
        )
        
        for (strategy in strategies) {
            try {
                Log.d(TAG, "Attempting ${strategy.name} for $packageName")
                
                if (strategy.preDelay > 0) {
                    Thread.sleep(strategy.preDelay.toLong())
                }
                
                val startTime = System.currentTimeMillis()
                val success = strategy.action()
                val duration = System.currentTimeMillis() - startTime
                
                if (success) {
                    Log.d(TAG, "${strategy.name} succeeded for $packageName in ${duration}ms")
                    FirebaseCrashlytics.getInstance().log("Successful click with ${strategy.name} for $packageName")
                    
                    return ClickResult(
                        success = true,
                        method = strategy.name,
                        duration = duration,
                        error = null
                    )
                } else {
                    Log.d(TAG, "${strategy.name} returned false for $packageName")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during ${strategy.name} for $packageName", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
        
        return ClickResult(
            success = false,
            method = "all_failed",
            duration = 0,
            error = "All click strategies failed"
        )
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
    
    data class ClickStrategy(
        val name: String,
        val preDelay: Int,
        val action: () -> Boolean
    )
    
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
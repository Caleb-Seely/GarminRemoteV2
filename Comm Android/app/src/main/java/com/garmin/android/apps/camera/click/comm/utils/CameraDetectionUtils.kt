package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

/**
 * CameraDetectionUtils.kt
 * Utility class for camera app detection and validation.
 * Provides methods to identify camera apps and validate shutter button detection.
 */
object CameraDetectionUtils {
    private const val TAG = "CameraDetectionUtils"

    // Known camera app package names
    private val knownCameraApps = setOf(
        "com.google.android.GoogleCamera",      // Google Camera
        "com.android.camera",                   // AOSP Camera
        "com.google.android.apps.camera",       // Google Camera (alternative)
        "com.sec.android.app.camera",           // Samsung Camera
        "com.oplus.camera",                     // OnePlus Camera
        "com.motorola.camera3",                 // Motorola Camera
        "com.sony.playmemories.mobile",         // Sony Camera
        "com.huawei.camera",                    // Huawei Camera
        "com.xiaomi.camera",                    // Xiaomi Camera
        "com.oneplus.camera",                   // OnePlus Camera (alternative)
        "com.lge.camera",                       // LG Camera
        "com.asus.camera",                      // ASUS Camera
        "com.lenovo.camera",                    // Lenovo Camera
        "com.zte.camera",                       // ZTE Camera
        "com.meizu.camera",                     // Meizu Camera
        "com.vivo.camera",                      // Vivo Camera
        "com.oppo.camera",                      // OPPO Camera
        "com.realme.camera",                    // Realme Camera
        "com.nothing.camera",                   // Nothing Camera
        "com.samsung.android.camera",           // Samsung Camera (alternative)
        "com.samsung.camera"                    // Samsung Camera (legacy)
    )

    // Camera-related keywords for content description matching
    private val cameraKeywords = setOf(
        "shutter", "take photo", "camera shutter", "capture", "shoot",
        "photo", "picture", "snap", "click", "record", "video",
        "camera", "capture photo", "take picture", "snap photo"
    )

    // Shutter button resource ID patterns
    private val shutterButtonPatterns = setOf(
        "shutter_button", "btn_camera_capture", "capture_button", "photo_button",
        "camera_button", "shoot_button", "snap_button", "record_button",
        "btn_shutter", "btn_capture", "btn_photo", "btn_picture"
    )

    /**
     * Check if a package name is a known camera app
     */
    fun isKnownCameraApp(packageName: String): Boolean {
        return knownCameraApps.contains(packageName) || 
               packageName.contains("camera", ignoreCase = true)
    }

    /**
     * Check if a package name is likely a camera app based on various heuristics
     */
    fun isLikelyCameraApp(context: Context, packageName: String): Boolean {
        // Check if it's a known camera app
        if (isKnownCameraApp(packageName)) {
            return true
        }

        // Check if the app has camera permissions
        try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val permissions = packageInfo.requestedPermissions ?: return false
            
            val cameraPermissions = setOf(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO", // Often used with camera
                "android.permission.WRITE_EXTERNAL_STORAGE" // For saving photos
            )
            
            return permissions.any { permission ->
                cameraPermissions.any { cameraPermission ->
                    permission.contains(cameraPermission, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking permissions for $packageName: ${e.message}")
        }

        return false
    }

    /**
     * Validate if a detected button is likely a shutter button
     */
    fun validateShutterButton(node: AccessibilityNodeInfo, packageName: String): ValidationResult {
        val score = calculateValidationScore(node, packageName)
        val isValid = score >= 70 // Threshold for considering it valid
        
        return ValidationResult(
            isValid = isValid,
            score = score,
            reasons = getValidationReasons(node, packageName)
        )
    }

    /**
     * Calculate a validation score for a potential shutter button
     */
    private fun calculateValidationScore(node: AccessibilityNodeInfo, packageName: String): Int {
        var score = 0
        
        // Check if it's clickable (essential)
        if (node.isClickable) score += 20 else return 0
        
        // Check content description
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        if (contentDesc != null) {
            if (cameraKeywords.any { contentDesc.contains(it) }) {
                score += 30
            }
        }
        
        // Check resource ID
        val resourceId = node.viewIdResourceName?.lowercase()
        if (resourceId != null) {
            if (shutterButtonPatterns.any { resourceId.contains(it) }) {
                score += 25
            }
        }
        
        // Check position (shutter buttons are typically in bottom center)
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val centerY = bounds.centerY()
        val screenHeight = 1920 // Approximate screen height
        if (centerY > screenHeight * 0.6) { // Bottom 40% of screen
            score += 15
        }
        
        // Check size (shutter buttons are typically medium to large)
        val area = bounds.width() * bounds.height()
        if (area > 10000 && area < 100000) { // Reasonable size range
            score += 10
        }
        
        // Check if it's square-ish (many shutter buttons are circular/square)
        val aspectRatio = bounds.width().toFloat() / bounds.height()
        if (aspectRatio in 0.8f..1.2f) {
            score += 10
        }
        
        // Bonus for known camera apps
        if (isKnownCameraApp(packageName)) {
            score += 5
        }
        
        return score.coerceAtMost(100)
    }

    /**
     * Get validation reasons for debugging
     */
    private fun getValidationReasons(node: AccessibilityNodeInfo, packageName: String): List<String> {
        val reasons = mutableListOf<String>()
        
        if (!node.isClickable) {
            reasons.add("Not clickable")
        }
        
        val contentDesc = node.contentDescription?.toString()
        if (contentDesc != null) {
            if (cameraKeywords.any { contentDesc.lowercase().contains(it) }) {
                reasons.add("Has camera-related content description: $contentDesc")
            }
        }
        
        val resourceId = node.viewIdResourceName
        if (resourceId != null) {
            if (shutterButtonPatterns.any { resourceId.lowercase().contains(it) }) {
                reasons.add("Has shutter-related resource ID: $resourceId")
            }
        }
        
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val centerY = bounds.centerY()
        val screenHeight = 1920
        if (centerY > screenHeight * 0.6) {
            reasons.add("Located in bottom portion of screen")
        }
        
        val area = bounds.width() * bounds.height()
        if (area > 10000 && area < 100000) {
            reasons.add("Has reasonable size: ${bounds.width()}x${bounds.height()}")
        }
        
        if (isKnownCameraApp(packageName)) {
            reasons.add("Known camera app: $packageName")
        }
        
        return reasons
    }

    /**
     * Get the best shutter button from a list of candidates
     */
    fun getBestShutterButton(candidates: List<AccessibilityNodeInfo>, packageName: String): AccessibilityNodeInfo? {
        if (candidates.isEmpty()) return null
        
        return candidates.maxByOrNull { node ->
            val validation = validateShutterButton(node, packageName)
            validation.score
        }
    }

    /**
     * Result of shutter button validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val score: Int,
        val reasons: List<String>
    )
} 
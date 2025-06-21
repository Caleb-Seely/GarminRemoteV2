package com.garmin.android.apps.camera.click.comm.model

import android.graphics.Rect

/**
 * Data class to store button location and identification information.
 * This class holds the bounds and identifying information of a button.
 *
 * @property bounds The screen coordinates of the button
 * @property packageName The package name of the app containing the button
 * @property contentDescription The content description of the button
 * @property resourceId The resource ID of the button
 * @property className The class name of the button
 * @property text The text content of the button
 * @property detectionMethod The method used to detect this button
 * @property confidenceScore A confidence score (0-100) for this detection
 * @property timestamp When this button info was captured
 * @property screenOrientation The screen orientation when detected
 * @property buttonSize The size (width x height) of the button
 * @property isSquare Whether the button is square-shaped
 * @property positionOnScreen The relative position on screen (e.g., "bottom_center")
 */
data class ShutterButtonInfo(
    val bounds: Rect,
    val packageName: String,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val detectionMethod: String? = null,
    val confidenceScore: Int = 50,
    val timestamp: Long = System.currentTimeMillis(),
    val screenOrientation: Int = 0, // 0=portrait, 1=landscape, 2=reverse_portrait, 3=reverse_landscape
    val buttonSize: String? = null,
    val isSquare: Boolean = false,
    val positionOnScreen: String? = null
) {
    /**
     * Calculate the relative position of the button on screen
     */
    fun calculatePositionOnScreen(screenWidth: Int, screenHeight: Int): String {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        
        val horizontalPosition = when {
            centerX < screenWidth * 0.33 -> "left"
            centerX > screenWidth * 0.67 -> "right"
            else -> "center"
        }
        
        val verticalPosition = when {
            centerY < screenHeight * 0.33 -> "top"
            centerY > screenHeight * 0.67 -> "bottom"
            else -> "middle"
        }
        
        return "${verticalPosition}_${horizontalPosition}"
    }
    
    /**
     * Check if this button info is still valid (not too old)
     */
    fun isValid(maxAgeMs: Long = 24 * 60 * 60 * 1000): Boolean {
        return System.currentTimeMillis() - timestamp < maxAgeMs
    }
    
    /**
     * Check if this button info matches the current package
     */
    fun matchesPackage(currentPackage: String): Boolean {
        return packageName == currentPackage
    }
    
    /**
     * Get a human-readable description of the detection method
     */
    fun getDetectionMethodDescription(): String {
        return when (detectionMethod) {
            "package_specific" -> "Package-specific pattern matching"
            "content_description" -> "Content description matching"
            "largest_node" -> "Largest clickable node"
            "position_based" -> "Position-based detection"
            "saved_location" -> "Previously saved location"
            else -> "Unknown method"
        }
    }
    
    /**
     * Calculate button size string
     */
    fun calculateButtonSize(): String {
        return "${bounds.width()}x${bounds.height()}"
    }
    
    /**
     * Check if button is square-shaped
     */
    fun calculateIsSquare(): Boolean {
        return bounds.width() == bounds.height()
    }
} 
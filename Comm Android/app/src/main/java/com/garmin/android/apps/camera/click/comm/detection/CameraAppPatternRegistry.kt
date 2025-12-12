package com.garmin.android.apps.camera.click.comm.detection

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of camera app-specific detection patterns.
 * Centralizes knowledge about different camera apps' UI structures.
 *
 * This replaces the hard-coded patterns in AccessibilityUtils
 * with a configurable, testable registry.
 */
@Singleton
class CameraAppPatternRegistry @Inject constructor() {

    private val patterns = mapOf(
        "com.google.android.GoogleCamera" to CameraAppPattern(
            packageName = "com.google.android.GoogleCamera",
            resourceIds = listOf("com.google.android.GoogleCamera:id/shutter_button", "shutter_button"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        ),
        "com.android.camera" to CameraAppPattern(
            packageName = "com.android.camera",
            resourceIds = listOf("com.android.camera:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        ),
        "com.sec.android.app.camera" to CameraAppPattern( // Samsung
            packageName = "com.sec.android.app.camera",
            resourceIds = listOf("com.sec.android.app.camera:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter", "Record", "Stop"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom"),
            vendor = "Samsung"
        ),
        "com.oplus.camera" to CameraAppPattern( // OnePlus
            packageName = "com.oplus.camera",
            resourceIds = listOf("com.oplus.camera:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom"),
            vendor = "OnePlus"
        ),
        "com.motorola.camera3" to CameraAppPattern( // Motorola
            packageName = "com.motorola.camera3",
            resourceIds = listOf("com.motorola.camera3:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageButton", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom"),
            vendor = "Motorola"
        )
    )

    /**
     * Get the detection pattern for a specific camera app
     *
     * @param packageName The package name of the camera app
     * @return CameraAppPattern if known, null otherwise
     */
    fun getPattern(packageName: String): CameraAppPattern? {
        return patterns[packageName]
    }

    /**
     * Get all registered patterns
     *
     * @return List of all camera app patterns
     */
    fun getAllPatterns(): List<CameraAppPattern> {
        return patterns.values.toList()
    }

    /**
     * Check if a package has a registered pattern
     *
     * @param packageName The package name to check
     * @return true if pattern exists, false otherwise
     */
    fun hasPattern(packageName: String): Boolean {
        return patterns.containsKey(packageName)
    }
}

/**
 * Data class representing detection patterns for specific camera apps
 */
data class CameraAppPattern(
    val packageName: String,
    val resourceIds: List<String>,
    val contentDescriptions: List<String>,
    val classNames: List<String>,
    val positions: List<String>,
    val vendor: String? = null
)

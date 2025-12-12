package com.garmin.android.apps.camera.click.comm.helpers

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for Samsung Camera-specific detection logic.
 *
 * Samsung Camera has special handling for video recording mode where
 * we need to detect the "stop recording" button instead of the "capture photo" button.
 */
@Singleton
class SamsungCameraHelper @Inject constructor() {

    private companion object {
        const val TAG = "SamsungCameraHelper"
        const val SAMSUNG_CAMERA_PACKAGE = "com.sec.android.app.camera"
        const val STOP_BUTTON_RESOURCE_ID = "com.sec.android.app.camera:id/stop_button"
    }

    /**
     * Detects if Samsung Camera is currently in video recording mode
     */
    fun isRecording(root: AccessibilityNodeInfo): Boolean {
        if (root.packageName != SAMSUNG_CAMERA_PACKAGE) {
            return false
        }

        var hasRecordingIndicators = false

        fun traverse(node: AccessibilityNodeInfo) {
            val resourceId = node.viewIdResourceName
            val contentDesc = node.contentDescription?.toString()?.lowercase()
            val text = node.text?.toString()?.lowercase()

            // Check for stop recording button - strong indicator we're recording
            if (resourceId == STOP_BUTTON_RESOURCE_ID && node.isClickable) {
                Log.d(TAG, "Samsung Camera: Found stop recording button - recording detected")
                hasRecordingIndicators = true
                return
            }

            // Check for recording indicators in content description
            contentDesc?.let { desc ->
                if (desc.contains("stop recording") || desc.contains("stop video") ||
                    desc.contains("recording") || desc.contains("rec")
                ) {
                    Log.d(TAG, "Samsung Camera: Found recording indicator in content desc: $desc")
                    hasRecordingIndicators = true
                }
            }

            // Look for recording time indicators (00:01, 00:02, etc.)
            text?.let { textContent ->
                if (textContent.matches(Regex("\\d{2}:\\d{2}"))) {
                    Log.d(TAG, "Samsung Camera: Found recording timer: $textContent")
                    hasRecordingIndicators = true
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
            }
        }

        traverse(root)

        if (hasRecordingIndicators) {
            Log.d(TAG, "Samsung Camera: Video recording mode detected")
            AnalyticsUtils.logEvent("samsung_video_recording_detected", Bundle().apply {
                putString("package_name", SAMSUNG_CAMERA_PACKAGE)
                putLong("detection_timestamp", System.currentTimeMillis())
            })
        }

        return hasRecordingIndicators
    }

    /**
     * Finds the Samsung Camera stop recording button specifically
     */
    fun findStopRecordingButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.packageName != SAMSUNG_CAMERA_PACKAGE) {
            return null
        }

        var stopButton: AccessibilityNodeInfo? = null

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val resourceId = node.viewIdResourceName
                val contentDesc = node.contentDescription?.toString()?.lowercase()

                // Priority 1: Look for the exact stop button resource ID
                if (resourceId == STOP_BUTTON_RESOURCE_ID) {
                    Log.d(TAG, "Found Samsung stop recording button by resource ID: $resourceId")
                    stopButton = node
                    return
                }

                // Priority 2: Look for stop recording in content description
                if (contentDesc != null && (contentDesc.contains("stop recording") ||
                            contentDesc.contains("stop video"))
                ) {
                    Log.d(TAG, "Found Samsung stop recording button by content desc: $contentDesc")
                    if (stopButton == null) { // Only use if we haven't found the exact resource ID
                        stopButton = node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                // Stop if we found exact match
                if (stopButton != null && stopButton!!.viewIdResourceName == STOP_BUTTON_RESOURCE_ID) {
                    break
                }
            }
        }

        traverse(root)
        return stopButton
    }

    /**
     * Check if this is Samsung Camera package
     */
    fun isSamsungCamera(packageName: String): Boolean {
        return packageName == SAMSUNG_CAMERA_PACKAGE
    }

    /**
     * Handle Samsung Camera button detection with special video recording logic
     *
     * @return The appropriate button (stop recording if recording, null if should use normal detection)
     */
    fun handleSamsungCameraDetection(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        if (!isSamsungCamera(packageName)) {
            return null
        }

        if (isRecording(root)) {
            Log.d(TAG, "Samsung Camera recording detected - looking for stop recording button")

            val stopButton = findStopRecordingButton(root)
            if (stopButton != null) {
                Log.d(TAG, "Samsung Camera: Using stop recording button instead of capture button")
                AnalyticsUtils.logEvent("samsung_video_stop_button_selected", Bundle().apply {
                    putString("package_name", packageName)
                    putString("button_resource_id", stopButton.viewIdResourceName ?: "unknown")
                })
                return stopButton
            } else {
                Log.w(TAG, "Samsung Camera: Recording detected but stop button not found")
                AnalyticsUtils.logEvent("samsung_video_stop_button_not_found", Bundle().apply {
                    putString("package_name", packageName)
                    putString("fallback_reason", "recording_detected_but_no_stop_button")
                })
            }
        }

        return null // Use normal detection
    }
}

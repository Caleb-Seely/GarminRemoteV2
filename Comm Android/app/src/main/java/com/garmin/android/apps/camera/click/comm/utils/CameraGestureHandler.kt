package com.garmin.android.apps.camera.click.comm.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.garmin.android.apps.camera.click.comm.WatchMessagingService
import com.google.firebase.analytics.FirebaseAnalytics

private const val TAG = "CameraGestureHandler"
/**
 * Handles all gesture-related operations for the camera accessibility service.
 * Manages different types of gestures for triggering the camera shutter.
 */
class CameraGestureHandler(private val service: AccessibilityService) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchMessagingService = WatchMessagingService()
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(service)
    private val gestureTimeoutHandler = Handler(Looper.getMainLooper())
    private var gestureCompleted = false

    /**
     * Attempts to trigger the camera shutter using a tap gesture.
     * @param centerX The x-coordinate of the center of the button
     * @param centerY The y-coordinate of the center of the button
     * @param startTime The time when the message was received from the watch
     */
    fun attemptCameraGesture(centerX: Float, centerY: Float, startTime: Long) {
        Log.d(TAG, "Attempting camera gesture at ($centerX, $centerY)")
        
        // Reset gesture state
        gestureCompleted = false
        
        val clickPath = Path()
        clickPath.moveTo(centerX, centerY)

        val clickGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 100))
            .build()

        // Set up a timeout for the gesture callback
        val timeoutRunnable = Runnable {
            if (!gestureCompleted) {
                Log.d(TAG, "Gesture timed out, trying second approach")
                attemptSecondGesture(centerX, centerY, startTime)
            }
        }

        try {
            Log.d(TAG, "Attempting to dispatch primary gesture...")
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Primary gesture completed successfully")
                    gestureCompleted = true
                    gestureTimeoutHandler.removeCallbacks(timeoutRunnable)

                    // Calculate and log time spent
                    val timeSpent = System.currentTimeMillis() - startTime
                    val bundle = Bundle().apply {
                        putLong("time_spent_ms", timeSpent)
                        putString("method", "primary_gesture")
                    }
                    firebaseAnalytics.logEvent("shutter_response_time", bundle)

                    // Wait a bit before confirming success to ensure camera actually captures
                    mainHandler.postDelayed({
                        watchMessagingService.sendMessage(
                            WatchMessagingService.MESSAGE_TYPE_SHUTTER_SUCCESS,
                            "Success!"
                        )
                    }, 500)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.e(TAG, "Primary gesture was cancelled")
                    gestureCompleted = true
                    gestureTimeoutHandler.removeCallbacks(timeoutRunnable)
                    attemptSecondGesture(centerX, centerY, startTime)
                }
            }

            val success = service.dispatchGesture(clickGesture, callback, null)

            if (!success) {
                Log.e(TAG, "Failed to dispatch primary gesture")
                attemptSecondGesture(centerX, centerY, startTime)
            } else {
                gestureTimeoutHandler.postDelayed(timeoutRunnable, 800)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while dispatching primary gesture", e)
            attemptSecondGesture(centerX, centerY, startTime)
        }
    }

    private fun attemptSecondGesture(centerX: Float, centerY: Float, startTime: Long) {
        val clickPath = Path()
        clickPath.moveTo(centerX, centerY)

        val clickGestureBuilder = GestureDescription.Builder()
        val firstStroke = GestureDescription.StrokeDescription(
            clickPath,
            0,
            100
        )
        clickGestureBuilder.addStroke(firstStroke)

        val secondClickPath = Path()
        secondClickPath.moveTo(centerX, centerY)
        val secondStroke = GestureDescription.StrokeDescription(
            secondClickPath,
            150,
            100
        )
        clickGestureBuilder.addStroke(secondStroke)

        val clickGesture = clickGestureBuilder.build()

        try {
            Log.d(TAG, "Attempting double-tap gesture...")
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Double-tap gesture completed successfully")
                    
                    // Calculate and log time spent
                    val timeSpent = System.currentTimeMillis() - startTime
                    val bundle = Bundle().apply {
                        putLong("time_spent_ms", timeSpent)
                        putString("method", "double_tap")
                    }
                    firebaseAnalytics.logEvent("shutter_response_time", bundle)
                    
                    mainHandler.postDelayed({
                        watchMessagingService.sendMessage(
                            WatchMessagingService.MESSAGE_TYPE_SHUTTER_SUCCESS,
                            "Success with double-tap!"
                        )
                    }, 500)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.e(TAG, "Double-tap gesture was cancelled")
                    tryPressAndHoldGesture(centerX, centerY, startTime)
                }
            }

            val success = service.dispatchGesture(clickGesture, callback, null)

            if (!success) {
                Log.e(TAG, "Failed to dispatch double-tap gesture")
                tryPressAndHoldGesture(centerX, centerY, startTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while dispatching double-tap gesture", e)
            tryPressAndHoldGesture(centerX, centerY, startTime)
        }
    }

    private fun tryPressAndHoldGesture(centerX: Float, centerY: Float, startTime: Long) {
        Log.d(TAG, "Attempting press-and-hold gesture")

        val pressPath = Path()
        pressPath.moveTo(centerX, centerY)

        val pressGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(pressPath, 0, 800))
            .build()

        try {
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Press-and-hold gesture completed")
                    
                    // Calculate and log time spent
                    val timeSpent = System.currentTimeMillis() - startTime
                    val bundle = Bundle().apply {
                        putLong("time_spent_ms", timeSpent)
                        putString("method", "press_and_hold")
                    }
                    firebaseAnalytics.logEvent("shutter_response_time", bundle)
                    
                    mainHandler.postDelayed({
                        watchMessagingService.sendMessage(
                            WatchMessagingService.MESSAGE_TYPE_SHUTTER_SUCCESS,
                            "Success with press-and-hold"
                        )
                    }, 500)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.e(TAG, "Press-and-hold gesture cancelled")
                    tryMultipleRapidTaps(centerX, centerY, startTime)
                }
            }

            service.dispatchGesture(pressGesture, callback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in press-and-hold gesture", e)
            tryMultipleRapidTaps(centerX, centerY, startTime)
        }
    }

    private fun tryMultipleRapidTaps(centerX: Float, centerY: Float, startTime: Long) {
        Log.d(TAG, "Attempting multiple rapid taps")
        // Implementation for multiple rapid taps
        // This is a fallback method that can be implemented if needed
    }
}
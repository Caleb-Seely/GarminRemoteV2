package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils

/**
 * CameraUtils.kt
 * Utility class for handling camera-related operations.
 * Provides methods to launch the device's camera app using various approaches.
 */
class CameraUtils {
    companion object {
        private const val TAG = "CameraUtils"

        /**
         * Attempts to launch the device's camera app using multiple methods.
         * Tries different approaches in order of preference:
         * 1. Direct launch of known camera apps
         * 2. Standard camera intents
         * 3. System app chooser
         * 4. Camera app settings (as last resort)
         *
         * @param context The context to use for launching the camera
         * @return true if a camera app was found and launched, false otherwise
         */
        fun launchCamera(context: Context): Boolean {
            try {
                FirebaseCrashlytics.getInstance().log("Attempting to launch camera app")
                // Try launching specific camera apps first
                val cameraPackages = listOf(
                    "com.google.android.GoogleCamera",
                    "com.android.camera",
                    "com.google.android.apps.camera"
                )

                for (packageName in cameraPackages) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            Log.d(TAG, "Found launch intent for $packageName")
                            FirebaseCrashlytics.getInstance().log("Launching camera app: $packageName")
                            context.startActivity(launchIntent)
                            AnalyticsUtils.logCameraLaunch(true, packageName)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not launch $packageName: ${e.message}")
                        FirebaseCrashlytics.getInstance().log("Failed to launch $packageName: ${e.message}")
                    }
                }

                // Try standard camera intents
                val intents = listOf(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    Intent("android.media.action.STILL_IMAGE_CAMERA"),
                    Intent("android.media.action.IMAGE_CAPTURE")
                )

                for (intent in intents) {
                    Log.d(TAG, "Trying camera intent: ${intent.action}")
                    if (intent.resolveActivity(context.packageManager) != null) {
                        Log.d(TAG, "Found camera app with intent: ${intent.action}")
                        FirebaseCrashlytics.getInstance().log("Launching camera with intent: ${intent.action}")
                        context.startActivity(intent)
                        AnalyticsUtils.logCameraLaunch(true)
                        return true
                    }
                }

                // Try launching through system chooser as a fallback
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                val chooser = Intent.createChooser(intent, "Select Camera App")
                if (chooser.resolveActivity(context.packageManager) != null) {
                    Log.d(TAG, "Launching camera through system chooser")
                    FirebaseCrashlytics.getInstance().log("Launching camera through system chooser")
                    context.startActivity(chooser)
                    AnalyticsUtils.logCameraLaunch(true)
                    return true
                }

                // Only open settings if nothing else works
                Log.e(TAG, "No camera app found with any method")
                FirebaseCrashlytics.getInstance().log("No camera app found with any method")
                Toast.makeText(context, "No camera app found. Please check your device settings.", Toast.LENGTH_SHORT).show()
                AnalyticsUtils.logCameraLaunch(false)
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Error launching camera", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                AnalyticsUtils.logCameraLaunch(false)
                Toast.makeText(context, "Error launching camera app: ${e.message}", Toast.LENGTH_SHORT).show()
                return false
            }
        }

    }
} 
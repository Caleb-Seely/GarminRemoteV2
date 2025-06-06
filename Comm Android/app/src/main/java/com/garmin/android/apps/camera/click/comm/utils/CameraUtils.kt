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
                    "com.google.android.apps.camera",
                    "com.oplus.camera",                 //OnePlus
                    "com.sec.android.app.camera",       //Samsung
                    "com.sony.playmemories.mobile"      //Sony
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

                // Try launching through system chooser as a fallback
                Log.d(TAG, "Launching through system chooser")
                val intent = Intent("android.media.action.STILL_IMAGE_CAMERA")
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent) // This may show system picker with always/once options
                    return true
                }

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
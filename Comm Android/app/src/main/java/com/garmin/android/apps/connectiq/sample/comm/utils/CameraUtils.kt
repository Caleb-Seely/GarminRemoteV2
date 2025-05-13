package com.garmin.android.apps.connectiq.sample.comm.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

/**
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
                            context.startActivity(launchIntent)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not launch $packageName: ${e.message}")
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
                        context.startActivity(intent)
                        return true
                    }
                }

                // Try launching through system chooser as a fallback
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                val chooser = Intent.createChooser(intent, "Select Camera App")
                if (chooser.resolveActivity(context.packageManager) != null) {
                    Log.d(TAG, "Launching camera through system chooser")
                    context.startActivity(chooser)
                    return true
                }

                // Only open settings if nothing else works
                Log.e(TAG, "No camera app found with any method")
                Toast.makeText(context, "No camera app found. Please check your device settings.", Toast.LENGTH_SHORT).show()
                
                // Open camera app settings as last resort
                val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                settingsIntent.data = android.net.Uri.parse("package:com.google.android.GoogleCamera")
                if (settingsIntent.resolveActivity(context.packageManager) != null) {
                    Log.d(TAG, "Opening camera app settings")
                    context.startActivity(settingsIntent)
                }
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Error launching camera app", e)
                Toast.makeText(context, "Error launching camera app: ${e.message}", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        /**
         * Checks if the app has camera permission.
         *
         * @param context The context to check permissions for
         * @return true if camera permission is granted, false otherwise
         */
        fun hasCameraPermission(context: Context): Boolean {
            return context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }
} 
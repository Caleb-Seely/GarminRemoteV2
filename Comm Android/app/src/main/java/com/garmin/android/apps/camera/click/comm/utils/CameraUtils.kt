package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils

/**
 * CameraUtils.kt
 * Utility class for handling camera-related operations.
 * Provides methods to launch the device's camera app using Android best practices
 * to ensure reliable "Always" selection behavior.
 */
class CameraUtils {
    companion object {
        private const val TAG = "CameraUtils"

        /**
         * Launches the full native camera app using Android best practices for reliable "Always" selection.
         * 
         * This method opens the actual camera app (not single photo capture) using:
         * 1. STILL_IMAGE_CAMERA intent (opens full camera app)
         * 2. Check for default app first (respects "Always" selection)
         * 3. Fallback to direct app launch if needed
         *
         * @param context The context to use for launching the camera
         * @return true if camera was launched successfully, false otherwise
         */
        fun launchCamera(context: Context): Boolean {
            try {
                FirebaseCrashlytics.getInstance().log("Attempting to launch full native camera app")
                
                // Method 1: Use STILL_IMAGE_CAMERA intent (opens full camera app, not single photo)
                val cameraIntent = Intent("android.media.action.STILL_IMAGE_CAMERA")
                
                // Check if there's a default app set (user selected "Always")
                val defaultResolveInfo = context.packageManager.resolveActivity(
                    cameraIntent, 
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                
                if (defaultResolveInfo != null) {
                    val defaultPackage = defaultResolveInfo.activityInfo.packageName
                    Log.d(TAG, "Found default camera app: $defaultPackage")
                    FirebaseCrashlytics.getInstance().log("Launching default full camera app: $defaultPackage")
                    
                    try {
                        context.startActivity(cameraIntent)
                        AnalyticsUtils.logCameraLaunch(true, defaultPackage)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch default camera app", e)
                        FirebaseCrashlytics.getInstance().recordException(e)
                        // Continue to fallback methods
                    }
                }
                
                // Method 2: Check if any camera apps are available for STILL_IMAGE_CAMERA
                val availableApps = context.packageManager.queryIntentActivities(
                    cameraIntent, 
                    PackageManager.MATCH_ALL
                )
                
                if (availableApps.isNotEmpty()) {
                    // Method 3: If no default is set, show chooser (this will show "Always"/"Just once")
                    Log.d(TAG, "No default camera app set, showing chooser with ${availableApps.size} options")
                    FirebaseCrashlytics.getInstance().log("Showing full camera app chooser - no default set")
                    
                    try {
                        context.startActivity(cameraIntent)
                        AnalyticsUtils.logCameraLaunch(true, "chooser_shown")
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch camera intent", e)
                        FirebaseCrashlytics.getInstance().recordException(e)
                        // Continue to fallback
                    }
                }
                
                // Method 4: Fallback - try to launch known camera apps directly by package name
                val knownCameraApps = listOf(
                    "com.google.android.GoogleCamera",
                    "com.android.camera",
                    "com.sec.android.app.camera",       // Samsung
                    "com.google.android.apps.camera",
                    "com.oplus.camera",                 // OnePlus
                    "com.oneplus.camera",               // OnePlus alternative
                    "com.motorola.camera3",             // Motorola
                    "com.sony.playmemories.mobile",     // Sony
                    "com.huawei.camera",                // Huawei
                    "com.xiaomi.camera",                // Xiaomi
                    "com.lge.camera",                   // LG
                    "com.asus.camera",                  // ASUS
                    "com.vivo.camera",                  // Vivo
                    "com.oppo.camera",                  // OPPO
                    "com.realme.camera",                // Realme
                    "com.nothing.camera"                // Nothing
                )
                
                Log.d(TAG, "Fallback: trying to launch known camera apps directly")
                for (packageName in knownCameraApps) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            Log.d(TAG, "Fallback: launching $packageName directly")
                            FirebaseCrashlytics.getInstance().log("Fallback launch full camera app: $packageName")
                            
                            context.startActivity(launchIntent)
                            AnalyticsUtils.logCameraLaunch(true, packageName)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not launch $packageName: ${e.message}")
                    }
                }
                
                // All methods failed
                Log.e(TAG, "Unable to launch any camera app")
                FirebaseCrashlytics.getInstance().log("Unable to launch any camera app")
                Toast.makeText(context, "No camera app found on device", Toast.LENGTH_SHORT).show()
                AnalyticsUtils.logCameraLaunch(false)
                return false
                
            } catch (e: Exception) {
                Log.e(TAG, "Error launching camera", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                AnalyticsUtils.logCameraLaunch(false)
                Toast.makeText(context, "Error launching camera: ${e.message}", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }
} 
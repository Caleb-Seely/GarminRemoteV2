package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.garmin.android.apps.camera.click.comm.model.CameraAppInfo
import com.garmin.android.apps.camera.click.comm.model.SwitchAccessStatus

/**
 * CompatibilityCheckUtils
 *
 * Low-level utility functions for detecting system compatibility,
 * accessibility services, camera apps, and device information.
 */
object CompatibilityCheckUtils {
    private const val TAG = "CompatibilityCheckUtils"
    private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

    /**
     * Detect if Switch Access accessibility service is enabled
     *
     * Switch Access is an Android accessibility feature that may be required
     * for video recording on some devices (e.g., Google Pixel).
     */
    fun isSwitchAccessEnabled(context: Context): Boolean {
        try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false

            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            // Common Switch Access service package/name patterns across Android versions
            val switchAccessPatterns = listOf(
                "com.google.android.marvin.talkback",
                "com.android.switchaccess",
                "com.google.android.accessibility.switchaccess"
            )

            return enabledServices.any { service ->
                val packageName = service.resolveInfo.serviceInfo.packageName
                val serviceName = service.resolveInfo.serviceInfo.name

                switchAccessPatterns.any { pattern ->
                    packageName.contains(pattern, ignoreCase = true) &&
                    serviceName.contains("switchaccess", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking Switch Access status", e)
            return false
        }
    }

    /**
     * Get detailed Switch Access status including availability
     */
    fun getSwitchAccessStatus(context: Context): SwitchAccessStatus {
        val isEnabled = isSwitchAccessEnabled(context)

        // Check if Switch Access service is available on this device
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val availableServices = accessibilityManager?.getInstalledAccessibilityServiceList() ?: emptyList()

        val switchAccessService = availableServices.find { service ->
            val packageName = service.resolveInfo.serviceInfo.packageName
            val serviceName = service.resolveInfo.serviceInfo.name
            packageName.contains("accessibility", ignoreCase = true) &&
            serviceName.contains("switchaccess", ignoreCase = true)
        }

        return SwitchAccessStatus(
            isEnabled = isEnabled,
            isAvailable = switchAccessService != null,
            serviceName = switchAccessService?.resolveInfo?.serviceInfo?.name
        )
    }

    /**
     * Get all installed camera apps on the device
     */
    fun getInstalledCameraApps(context: Context): List<CameraAppInfo> {
        val cameraApps = mutableListOf<CameraAppInfo>()
        val packageManager = context.packageManager

        try {
            // Query all apps that can handle camera intent
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    cameraIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }

                cameraApps.add(
                    CameraAppInfo(
                        packageName = packageName,
                        appName = appName,
                        versionName = versionName,
                        isKnownCompatible = CameraDetectionUtils.isKnownCameraApp(packageName),
                        isDefault = false
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting installed camera apps", e)
        }

        return cameraApps
    }

    /**
     * Get the default camera app
     */
    fun getDefaultCameraApp(context: Context): CameraAppInfo? {
        val packageManager = context.packageManager

        try {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    cameraIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            return resolveInfo?.activityInfo?.let { activityInfo ->
                val packageName = activityInfo.packageName
                val appName = activityInfo.loadLabel(packageManager).toString()
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }

                CameraAppInfo(
                    packageName = packageName,
                    appName = appName,
                    versionName = versionName,
                    isKnownCompatible = CameraDetectionUtils.isKnownCameraApp(packageName),
                    isDefault = true
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting default camera app", e)
            return null
        }
    }

    /**
     * Check if Garmin Connect app is installed
     */
    fun isGarminConnectInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GARMIN_CONNECT_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Check if app is exempt from battery optimization
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true // No battery optimization on pre-M devices
        }
    }

    /**
     * Get device manufacturer
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    }

    /**
     * Get device model
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * Get Android version string (e.g., "14")
     */
    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE
    }

    /**
     * Get Android API level
     */
    fun getAndroidApiLevel(): Int {
        return Build.VERSION.SDK_INT
    }

    /**
     * Get build number
     */
    fun getBuildNumber(): String {
        return Build.DISPLAY
    }

    /**
     * Get app version name
     */
    fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get app version code
     */
    fun getAppVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get screen resolution as a string (e.g., "1080x2400")
     */
    fun getScreenResolution(context: Context): String {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = context.display
                display?.getRealMetrics(displayMetrics)
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            }
            "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get screen density
     */
    fun getScreenDensity(context: Context): Float {
        return try {
            context.resources.displayMetrics.density
        } catch (e: Exception) {
            1.0f
        }
    }

    /**
     * Get device uptime in milliseconds
     */
    fun getDeviceUptime(): Long {
        return android.os.SystemClock.elapsedRealtime()
    }

    /**
     * Get device fingerprint for debugging
     */
    fun getDeviceFingerprint(): String {
        return Build.FINGERPRINT
    }
}

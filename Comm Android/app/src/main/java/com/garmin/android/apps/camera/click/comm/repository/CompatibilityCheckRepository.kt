package com.garmin.android.apps.camera.click.comm.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.core.content.ContextCompat
import com.garmin.android.apps.camera.click.comm.model.*
import com.garmin.android.apps.camera.click.comm.utils.CompatibilityCheckUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for performing compatibility checks and collecting system diagnostics.
 *
 * This repository orchestrates all compatibility checks, caches results,
 * and provides formatted diagnostic reports for support purposes.
 */
@Singleton
class CompatibilityCheckRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: DevicePreferencesRepository,
    private val buttonPersistenceRepository: ButtonPersistenceRepository
) {
    private var cachedResult: CompatibilityCheckResult? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    /**
     * Perform complete compatibility check
     *
     * @param forceRefresh Skip cache and perform fresh check
     * @return Complete compatibility check result
     */
    fun performCompatibilityCheck(forceRefresh: Boolean = false): CompatibilityCheckResult {
        // Return cached result if available and fresh
        if (!forceRefresh &&
            cachedResult != null &&
            System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS
        ) {
            return cachedResult!!
        }

        val checks = mutableListOf<CompatibilityCheck>()

        // Perform all checks with error handling
        checks.add(checkAccessibilityService())
        checks.add(checkNotificationPermission())
        checks.add(checkGarminConnect())
        checks.add(checkSwitchAccess())
        checks.add(checkBatteryOptimization())

        val systemDiagnostics = collectSystemDiagnostics()
        val overallStatus = calculateOverallStatus(checks)

        val result = CompatibilityCheckResult(
            checks = checks,
            overallStatus = overallStatus,
            systemDiagnostics = systemDiagnostics,
            timestamp = System.currentTimeMillis()
        )

        // Update cache
        cachedResult = result
        cacheTimestamp = System.currentTimeMillis()

        // Update last check timestamp in preferences
        preferencesRepository.lastCompatibilityCheckTimestamp = System.currentTimeMillis()

        return result
    }

    /**
     * Check if CameraClick accessibility service is enabled
     */
    private fun checkAccessibilityService(): CompatibilityCheck {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            val isEnabled = enabledServices.any { service ->
                service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == "com.garmin.android.apps.camera.click.comm.service.CameraAccessibilityService"
            }

            CompatibilityCheck(
                id = "accessibility_service",
                name = "Accessibility Service",
                status = if (isEnabled) CheckStatus.PASS else CheckStatus.WARNING,
                severity = CheckSeverity.CRITICAL,
                message = if (isEnabled) {
                    "Enabled"
                } else {
                    "Required for camera control"
                },
                actionText = if (!isEnabled) "Open Settings" else null,
                actionIntent = if (!isEnabled) Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) else null
            )
        } catch (e: Exception) {
            CompatibilityCheck(
                id = "accessibility_service",
                name = "Accessibility Service",
                status = CheckStatus.INFO,
                severity = CheckSeverity.CRITICAL,
                message = "Unable to verify accessibility status",
                actionText = null,
                actionIntent = null
            )
        }
    }

    /**
     * Check notification permission (Android 13+)
     */
    private fun checkNotificationPermission(): CompatibilityCheck {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            CompatibilityCheck(
                id = "notification_permission",
                name = "Notifications",
                status = if (isGranted) CheckStatus.PASS else CheckStatus.WARNING,
                severity = CheckSeverity.IMPORTANT,
                message = if (isGranted) {
                    "Granted"
                } else {
                    "Helps keep service running"
                },
                actionText = if (!isGranted) "Grant Permission" else null,
                actionIntent = if (!isGranted) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else null
            )
        } else {
            // Notifications don't require permission on Android < 13
            CompatibilityCheck(
                id = "notification_permission",
                name = "Notification Permission",
                status = CheckStatus.PASS,
                severity = CheckSeverity.IMPORTANT,
                message = "Notification permission not required on this Android version",
                actionText = null,
                actionIntent = null
            )
        }
    }

    /**
     * Check if Garmin Connect app is installed
     */
    private fun checkGarminConnect(): CompatibilityCheck {
        val isInstalled = CompatibilityCheckUtils.isGarminConnectInstalled(context)

        return CompatibilityCheck(
            id = "garmin_connect",
            name = "Garmin Connect",
            status = if (isInstalled) CheckStatus.PASS else CheckStatus.WARNING,
            severity = CheckSeverity.IMPORTANT,
            message = if (isInstalled) {
                "Installed"
            } else {
                "Required for watch communication"
            },
            actionText = if (!isInstalled) "Install App" else null,
            actionIntent = if (!isInstalled) {
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.garmin.android.apps.connectmobile"))
            } else null
        )
    }

    /**
     * Check Switch Access status
     */
    private fun checkSwitchAccess(): CompatibilityCheck {
        val isEnabled = CompatibilityCheckUtils.isSwitchAccessEnabled(context)

        return CompatibilityCheck(
            id = "switch_access",
            name = "Switch Access",
            status = if (isEnabled) CheckStatus.PASS else CheckStatus.INFO,
            severity = CheckSeverity.OPTIONAL,
            message = if (isEnabled) {
                "Enabled"
            } else {
                "May be needed for video"
            },
            actionText = if (!isEnabled) "Enable" else null,
            actionIntent = if (!isEnabled) {
                // Try to deep link directly to Switch Access settings
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    // Add fragment arguments to try to navigate directly to Switch Access
                    // This works on most Android versions but may fall back to main accessibility settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        putExtra(":settings:fragment_args_key", "switch_access_service")
                        putExtra(":settings:show_fragment", "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment")
                    }
                }
            } else null
        )
    }

    /**
     * Check battery optimization status
     */
    private fun checkBatteryOptimization(): CompatibilityCheck {
        val isDisabled = CompatibilityCheckUtils.isBatteryOptimizationDisabled(context)

        return CompatibilityCheck(
            id = "battery_optimization",
            name = "Battery Optimization",
            status = if (isDisabled) CheckStatus.PASS else CheckStatus.INFO,
            severity = CheckSeverity.OPTIONAL,
            message = if (isDisabled) {
                "Disabled"
            } else {
                "Improves reliability"
            },
            actionText = if (!isDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "Disable" else null,
            actionIntent = if (!isDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else null
        )
    }

    /**
     * Collect complete system diagnostics
     */
    private fun collectSystemDiagnostics(): SystemDiagnostics {
        val installedCameraApps = CompatibilityCheckUtils.getInstalledCameraApps(context)
        val defaultCameraApp = CompatibilityCheckUtils.getDefaultCameraApp(context)

        // Update default flag in installed camera apps list
        val updatedCameraApps = installedCameraApps.map { app ->
            app.copy(isDefault = app.packageName == defaultCameraApp?.packageName)
        }

        return SystemDiagnostics(
            androidVersion = CompatibilityCheckUtils.getAndroidVersion(),
            androidApiLevel = CompatibilityCheckUtils.getAndroidApiLevel(),
            manufacturer = CompatibilityCheckUtils.getDeviceManufacturer(),
            model = CompatibilityCheckUtils.getDeviceModel(),
            buildNumber = CompatibilityCheckUtils.getBuildNumber(),
            appVersion = CompatibilityCheckUtils.getAppVersion(context),
            appVersionCode = CompatibilityCheckUtils.getAppVersionCode(context),
            connectIqSdkVersion = "2.2.0", // TODO: Get from actual SDK
            installedCameraApps = updatedCameraApps,
            defaultCameraApp = defaultCameraApp,
            screenResolution = CompatibilityCheckUtils.getScreenResolution(context),
            screenDensity = CompatibilityCheckUtils.getScreenDensity(context),
            deviceUptime = CompatibilityCheckUtils.getDeviceUptime()
        )
    }

    /**
     * Calculate overall compatibility status based on individual checks
     */
    private fun calculateOverallStatus(checks: List<CompatibilityCheck>): CompatibilityStatus {
        val hasCriticalIssues = checks.any {
            it.status != CheckStatus.PASS && it.severity == CheckSeverity.CRITICAL
        }

        val hasImportantIssues = checks.any {
            it.status == CheckStatus.WARNING && it.severity == CheckSeverity.IMPORTANT
        }

        return when {
            hasCriticalIssues -> CompatibilityStatus.SETUP_NEEDED
            hasImportantIssues -> CompatibilityStatus.SETUP_NEEDED
            else -> CompatibilityStatus.READY
        }
    }

    /**
     * Generate diagnostic report in copyable text format
     */
    fun getDiagnosticReport(result: CompatibilityCheckResult): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(result.timestamp))

        val builder = StringBuilder()
        builder.appendLine("CameraClick Compatibility Report")
        builder.appendLine("Generated: $timestamp")
        builder.appendLine()

        builder.appendLine("=== COMPATIBILITY STATUS ===")
        builder.appendLine("Overall: ${result.overallStatus}")
        builder.appendLine("Checks Passed: ${result.checks.count { it.status == CheckStatus.PASS }}/${result.checks.size}")
        val warningCount = result.checks.count { it.status == CheckStatus.WARNING }
        if (warningCount > 0) {
            builder.appendLine("Checks with Warnings: $warningCount")
        }
        builder.appendLine()

        builder.appendLine("=== PERMISSION STATUS ===")
        result.checks.forEach { check ->
            val icon = when (check.status) {
                CheckStatus.PASS -> "✓"
                CheckStatus.WARNING -> "⚠"
                CheckStatus.INFO -> "ℹ"
            }
            builder.appendLine("$icon ${check.name}: ${check.message}")
        }
        builder.appendLine()

        builder.appendLine("=== DEVICE INFORMATION ===")
        builder.appendLine("Manufacturer: ${result.systemDiagnostics.manufacturer}")
        builder.appendLine("Model: ${result.systemDiagnostics.model}")
        builder.appendLine("Android Version: ${result.systemDiagnostics.androidVersion} (API ${result.systemDiagnostics.androidApiLevel})")
        builder.appendLine("Build: ${result.systemDiagnostics.buildNumber}")
        builder.appendLine("App Version: ${result.systemDiagnostics.appVersion} (${result.systemDiagnostics.appVersionCode})")
        builder.appendLine("ConnectIQ SDK: ${result.systemDiagnostics.connectIqSdkVersion}")
        builder.appendLine()

        builder.appendLine("=== CAMERA APPS (${result.systemDiagnostics.installedCameraApps.size} detected) ===")
        result.systemDiagnostics.installedCameraApps.forEach { app ->
            val icon = if (app.isKnownCompatible) "✓" else "•"
            val defaultTag = if (app.isDefault) " [DEFAULT]" else ""
            builder.appendLine("$icon ${app.appName} ${app.versionName}$defaultTag")
            builder.appendLine("  Package: ${app.packageName}")
            builder.appendLine("  Known Compatible: ${if (app.isKnownCompatible) "Yes" else "No"}")
            builder.appendLine()
        }

        builder.appendLine("=== SCREEN INFORMATION ===")
        builder.appendLine("Resolution: ${result.systemDiagnostics.screenResolution}")
        builder.appendLine("Density: ${result.systemDiagnostics.screenDensity}")
        builder.appendLine()

        val lastKnownButton = buttonPersistenceRepository.loadLastKnownButton()
        val userPreferredButtons = buttonPersistenceRepository.loadAllUserPreferredButtons()
        val candidateLists = buttonPersistenceRepository.loadCandidateLists()
        val allApps = (userPreferredButtons.keys + candidateLists.keys).toSortedSet()

        builder.appendLine("=== BUTTON CONFIGURATION ===")
        builder.appendLine("Last Used: ${lastKnownButton?.packageName ?: "None"}")
        if (lastKnownButton != null) {
            builder.appendLine("  Method: ${lastKnownButton.getDetectionMethodDescription()}")
            builder.appendLine("  Confidence: ${lastKnownButton.confidenceScore}%")
            builder.appendLine("  Position: ${lastKnownButton.positionOnScreen ?: "unknown"}")
            builder.appendLine("  Bounds: ${lastKnownButton.bounds}")
            if (!lastKnownButton.resourceId.isNullOrEmpty()) builder.appendLine("  Resource ID: ${lastKnownButton.resourceId}")
            if (!lastKnownButton.contentDescription.isNullOrEmpty()) builder.appendLine("  Content Desc: ${lastKnownButton.contentDescription}")
            val ageMinutes = (System.currentTimeMillis() - lastKnownButton.timestamp) / 60000
            builder.appendLine("  Age: ${if (ageMinutes < 60) "${ageMinutes}min" else "${ageMinutes / 60}hr ${ageMinutes % 60}min"}")
        }
        builder.appendLine()

        if (allApps.isEmpty()) {
            builder.appendLine("Per-App Config: No saved button data")
        } else {
            builder.appendLine("Per-App Config (${allApps.size} apps):")
            allApps.forEach { pkg ->
                builder.appendLine()
                builder.appendLine("  [$pkg]")

                val manual = userPreferredButtons[pkg]
                if (manual != null) {
                    val setDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(manual.timestamp))
                    builder.appendLine("  Manual Override: YES (set $setDate)")
                    builder.appendLine("    Position: ${manual.positionOnScreen ?: "unknown"}")
                    builder.appendLine("    Bounds: ${manual.bounds}")
                    if (!manual.resourceId.isNullOrEmpty()) builder.appendLine("    Resource ID: ${manual.resourceId}")
                    if (!manual.contentDescription.isNullOrEmpty()) builder.appendLine("    Content Desc: ${manual.contentDescription}")
                } else {
                    builder.appendLine("  Manual Override: NO")
                }

                val candidates = candidateLists[pkg]
                if (!candidates.isNullOrEmpty()) {
                    builder.appendLine("  Candidates (${candidates.size}):")
                    candidates.forEachIndexed { i, btn ->
                        val line = buildString {
                            append("    ${i + 1}. ${btn.getDetectionMethodDescription()} | ${btn.confidenceScore}%")
                            if (btn.positionOnScreen != null) append(" | ${btn.positionOnScreen}")
                            if (!btn.resourceId.isNullOrEmpty()) append(" | ${btn.resourceId}")
                            if (!btn.contentDescription.isNullOrEmpty()) append(" | \"${btn.contentDescription}\"")
                        }
                        builder.appendLine(line)
                    }
                } else {
                    builder.appendLine("  Candidates: None")
                }
            }
        }
        builder.appendLine()

        if (result.checks.any { it.status != CheckStatus.PASS }) {
            builder.appendLine("=== RECOMMENDATIONS ===")
            result.checks.filter { it.status != CheckStatus.PASS && it.actionText != null }.forEach { check ->
                builder.appendLine("• ${check.message}")
                builder.appendLine("  Action: ${check.actionText}")
                builder.appendLine()
            }
        }

        builder.appendLine("For support: https://forms.gle/3JXQ9fDrTEBAuroG7")

        return builder.toString()
    }

    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        return preferencesRepository.isFirstAppLaunch
    }

    /**
     * Mark first launch as complete
     */
    fun markFirstLaunchComplete() {
        preferencesRepository.isFirstAppLaunch = false
    }

    /**
     * Clear cache to force fresh check on next call
     */
    fun clearCache() {
        cachedResult = null
        cacheTimestamp = 0
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            enabledServices.any { service ->
                service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == "com.garmin.android.apps.camera.click.comm.service.CameraAccessibilityService"
            }
        } catch (e: Exception) {
            false
        }
    }
}

package com.garmin.android.apps.camera.click.comm.model

import android.content.Intent

/**
 * Result of a complete compatibility check containing all checks and diagnostics
 */
data class CompatibilityCheckResult(
    val checks: List<CompatibilityCheck>,
    val overallStatus: CompatibilityStatus,
    val systemDiagnostics: SystemDiagnostics,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun hasCheckPassed(checkId: String): Boolean {
        return checks.find { it.id == checkId }?.status == CheckStatus.PASS
    }

    fun hasCriticalIssues(): Boolean {
        return checks.any { it.status != CheckStatus.PASS && it.severity == CheckSeverity.CRITICAL }
    }
}

/**
 * Individual compatibility check with status and action
 */
data class CompatibilityCheck(
    val id: String,
    val name: String,
    val status: CheckStatus,
    val severity: CheckSeverity,
    val message: String,
    val actionText: String? = null,
    val actionIntent: Intent? = null
)

/**
 * Status of an individual check
 */
enum class CheckStatus {
    PASS,    // Check passed successfully
    WARNING, // Check failed but not critical
    INFO     // Informational only
}

/**
 * Severity level of a check
 */
enum class CheckSeverity {
    CRITICAL,   // Must be fixed for app to work
    IMPORTANT,  // Should be fixed for best experience
    OPTIONAL    // Nice to have, informational
}

/**
 * Overall compatibility status
 */
enum class CompatibilityStatus {
    READY,          // All critical checks passed
    SETUP_NEEDED,   // Some critical checks failed
    INFO_AVAILABLE  // All checks passed but informational items available
}

/**
 * Complete system diagnostics information
 */
data class SystemDiagnostics(
    val androidVersion: String,
    val androidApiLevel: Int,
    val manufacturer: String,
    val model: String,
    val buildNumber: String,
    val appVersion: String,
    val appVersionCode: Int,
    val connectIqSdkVersion: String,
    val installedCameraApps: List<CameraAppInfo>,
    val defaultCameraApp: CameraAppInfo?,
    val screenResolution: String,
    val screenDensity: Float,
    val deviceUptime: Long
)

/**
 * Information about a camera app
 */
data class CameraAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isKnownCompatible: Boolean,
    val isDefault: Boolean
)

/**
 * Switch Access accessibility service status
 */
data class SwitchAccessStatus(
    val isEnabled: Boolean,
    val isAvailable: Boolean,
    val serviceName: String?
)

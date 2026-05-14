package com.garmin.android.apps.camera.click.comm.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.CheckSeverity
import com.garmin.android.apps.camera.click.comm.model.CheckStatus
import com.garmin.android.apps.camera.click.comm.model.CompatibilityCheckResult
import com.garmin.android.apps.camera.click.comm.model.CompatibilityStatus
import com.garmin.android.apps.camera.click.comm.repository.CompatibilityCheckRepository
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity that displays system status and compatibility checks
 *
 * Shows a full-screen view of all health checks, system diagnostics,
 * and provides a copyable diagnostic report for troubleshooting.
 */
@AndroidEntryPoint
class SystemStatusActivity : AppCompatActivity() {

    @Inject
    lateinit var compatibilityRepository: CompatibilityCheckRepository

    private lateinit var result: CompatibilityCheckResult

    companion object {
        private const val TAG = "SystemStatusActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_status)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.compatibility_check_title)

        // Perform compatibility check (force refresh to get latest data)
        result = compatibilityRepository.performCompatibilityCheck(forceRefresh = true)

        // Setup UI
        setupStatusMessage()
        setupChecks()
        setupDeviceInfo()
        setupCameraApps()
        setupButtons()

        // Log analytics
        logCompatibilityCheckAnalytics()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupStatusMessage() {
        val alertBanner = findViewById<LinearLayout>(R.id.alert_banner)
        val alertMessage = findViewById<TextView>(R.id.alert_banner_message)

        when (result.overallStatus) {
            CompatibilityStatus.READY -> {
                alertBanner.visibility = View.GONE
            }
            CompatibilityStatus.SETUP_NEEDED -> {
                // Show alert banner for setup needed
                alertBanner.visibility = View.VISIBLE

                // Customize message based on what's needed
                val criticalIssues = result.checks.filter {
                    it.status == CheckStatus.WARNING && it.severity == CheckSeverity.CRITICAL
                }
                if (criticalIssues.size == 1) {
                    alertMessage.text = "Complete the setup step below to use CameraClick with your Garmin watch"
                } else {
                    alertMessage.text = "Complete the ${criticalIssues.size} setup steps below to use CameraClick with your Garmin watch"
                }
            }
            CompatibilityStatus.INFO_AVAILABLE -> {
                alertBanner.visibility = View.GONE
            }
        }
    }

    private fun setupChecks() {
        val checksContainer = findViewById<LinearLayout>(R.id.checks_container)
        checksContainer.removeAllViews()

        result.checks.forEachIndexed { index, check ->
            val checkView = LayoutInflater.from(this).inflate(
                R.layout.item_compatibility_check,
                checksContainer,
                false
            )

            // Icon and colors based on status
            val iconView = checkView.findViewById<TextView>(R.id.check_icon)
            val iconBackground = checkView.findViewById<View>(R.id.check_icon_background)

            // Special handling for Switch Access - use warning color
            val (icon, color) = when {
                check.id == "switch_access" && check.status == CheckStatus.INFO -> {
                    Pair("⚠", R.color.warning)
                }
                check.status == CheckStatus.PASS -> {
                    Pair("✓", R.color.success)
                }
                check.status == CheckStatus.WARNING -> {
                    Pair("✕", R.color.error)
                }
                check.status == CheckStatus.INFO -> {
                    Pair("ℹ", R.color.info)
                }
                else -> {
                    Pair("?", R.color.text_secondary)
                }
            }
            iconView.text = icon
            iconView.setTextColor(getColor(color))
            iconBackground.setBackgroundResource(
                when (color) {
                    R.color.success -> R.drawable.circle_background
                    R.color.error -> R.drawable.circle_background
                    R.color.warning -> R.drawable.circle_background
                    else -> R.drawable.circle_background
                }
            )
            iconBackground.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(color))

            // Name and message
            checkView.findViewById<TextView>(R.id.check_name).text = check.name
            checkView.findViewById<TextView>(R.id.check_message).text = check.message

            // Badge (Required/Optional) - only show on failed checks
            val badge = checkView.findViewById<TextView>(R.id.check_badge)
            if (check.status != CheckStatus.PASS) {
                when (check.severity) {
                    CheckSeverity.CRITICAL -> {
                        badge.visibility = View.VISIBLE
                        badge.text = getString(R.string.badge_required)
                        badge.setBackgroundResource(R.drawable.badge_required)
                    }
                    CheckSeverity.IMPORTANT -> {
                        badge.visibility = View.VISIBLE
                        badge.text = getString(R.string.badge_required)
                        badge.setBackgroundResource(R.drawable.badge_required)
                    }
                    CheckSeverity.OPTIONAL -> {
                        badge.visibility = View.VISIBLE
                        badge.text = getString(R.string.badge_optional)
                        badge.setBackgroundResource(R.drawable.badge_optional)
                    }
                }
            } else {
                badge.visibility = View.GONE
            }

            // Action button
            val actionButton = checkView.findViewById<MaterialButton>(R.id.check_action_button)
            if (check.actionText != null && check.actionIntent != null) {
                actionButton.visibility = View.VISIBLE
                actionButton.text = check.actionText

                // Style button based on severity
                // Notification permission button should be red (error color)
                when {
                    check.id == "notification_permission" && check.severity == CheckSeverity.IMPORTANT -> {
                        actionButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error))
                    }
                    check.severity == CheckSeverity.CRITICAL -> {
                        actionButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error))
                    }
                    check.severity == CheckSeverity.IMPORTANT -> {
                        actionButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.info))
                    }
                    check.severity == CheckSeverity.OPTIONAL -> {
                        actionButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary))
                    }
                }

                actionButton.setOnClickListener {
                    try {
                        startActivity(check.actionIntent)
                        AnalyticsUtils.logCompatibilityCheckAction("fix_attempted", check.id)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Unable to open settings",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                actionButton.visibility = View.GONE
            }

            // Hide divider for last item
            val divider = checkView.findViewById<View>(R.id.check_divider)
            if (index == result.checks.size - 1) {
                divider.visibility = View.GONE
            }

            checksContainer.addView(checkView)
        }
    }

    private fun setupDeviceInfo() {
        val deviceInfoContainer = findViewById<LinearLayout>(R.id.device_info_container)
        deviceInfoContainer.removeAllViews()

        val diagnostics = result.systemDiagnostics

        // Create info items
        addInfoItem(deviceInfoContainer, "Manufacturer", diagnostics.manufacturer)
        addInfoItem(deviceInfoContainer, "Model", diagnostics.model)
        addInfoItem(deviceInfoContainer, "Android Version", "${diagnostics.androidVersion} (API ${diagnostics.androidApiLevel})")
        addInfoItem(deviceInfoContainer, "App Version", "${diagnostics.appVersion} (${diagnostics.appVersionCode})")
        addInfoItem(deviceInfoContainer, "ConnectIQ SDK", diagnostics.connectIqSdkVersion)
        addInfoItem(deviceInfoContainer, "Screen", "${diagnostics.screenResolution} (${diagnostics.screenDensity}x)")
    }

    private fun setupCameraApps() {
        val cameraAppsContainer = findViewById<LinearLayout>(R.id.camera_apps_container)
        cameraAppsContainer.removeAllViews()

        val cameraApps = result.systemDiagnostics.installedCameraApps
        val defaultCamera = result.systemDiagnostics.defaultCameraApp

        if (cameraApps.isEmpty()) {
            addInfoItem(cameraAppsContainer, "No camera apps detected", "")
        } else {
            cameraApps.forEach { cameraApp ->
                val isDefault = cameraApp.packageName == defaultCamera?.packageName
                val displayName = buildString {
                    append(cameraApp.appName)
                    if (isDefault) append(" [DEFAULT]")
                    if (cameraApp.isKnownCompatible) append(" ✓")
                }
                val details = buildString {
                    append(cameraApp.packageName)
                    if (cameraApp.versionName.isNotEmpty()) {
                        append("\nVersion: ${cameraApp.versionName}")
                    }
                }
                addInfoItem(cameraAppsContainer, displayName, details)
            }
        }
    }

    private fun addInfoItem(container: LinearLayout, label: String, value: String) {
        val itemView = LayoutInflater.from(this).inflate(
            R.layout.item_system_info,
            container,
            false
        )

        itemView.findViewById<TextView>(R.id.info_label).text = label
        itemView.findViewById<TextView>(R.id.info_value).text = value

        container.addView(itemView)
    }

    private fun setupButtons() {
        // Copy Diagnostics Button
        findViewById<MaterialButton>(R.id.btn_copy_diagnostics).setOnClickListener {
            copyDiagnosticsToClipboard()
        }

        // Close Button
        findViewById<MaterialButton>(R.id.btn_close).setOnClickListener {
            finish()
        }
    }

    private fun copyDiagnosticsToClipboard() {
        val diagnosticReport = compatibilityRepository.getDiagnosticReport(result)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("CameraClick Diagnostics", diagnosticReport)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            this,
            R.string.compatibility_diagnostics_copied,
            Toast.LENGTH_SHORT
        ).show()

        AnalyticsUtils.logCompatibilityCheckAction("copied_diagnostics", "activity")
    }

    private fun logCompatibilityCheckAnalytics() {
        val switchAccessCheck = result.checks.find { it.id == "switch_access" }
        val accessibilityCheck = result.checks.find { it.id == "accessibility_service" }
        val notificationCheck = result.checks.find { it.id == "notification_permission" }
        val garminConnectCheck = result.checks.find { it.id == "garmin_connect" }

        AnalyticsUtils.logCompatibilityCheck(
            overallStatus = result.overallStatus.name,
            checksPassed = result.checks.count { it.status == CheckStatus.PASS },
            checksWarning = result.checks.count { it.status == CheckStatus.WARNING },
            checksInfo = result.checks.count { it.status == CheckStatus.INFO },
            accessibilityEnabled = accessibilityCheck?.status == CheckStatus.PASS,
            switchAccessEnabled = switchAccessCheck?.status == CheckStatus.PASS,
            notificationsGranted = notificationCheck?.status == CheckStatus.PASS,
            garminConnectInstalled = garminConnectCheck?.status == CheckStatus.PASS,
            deviceManufacturer = result.systemDiagnostics.manufacturer,
            deviceModel = result.systemDiagnostics.model,
            androidVersion = result.systemDiagnostics.androidVersion,
            cameraAppsCount = result.systemDiagnostics.installedCameraApps.size,
            defaultCamera = result.systemDiagnostics.defaultCameraApp?.packageName,
            isFirstLaunch = compatibilityRepository.isFirstLaunch()
        )

        // Log Switch Access status separately
        AnalyticsUtils.logSwitchAccessStatus(
            enabled = switchAccessCheck?.status == CheckStatus.PASS,
            detected = switchAccessCheck != null,
            deviceManufacturer = result.systemDiagnostics.manufacturer,
            deviceModel = result.systemDiagnostics.model
        )

        // Log camera app detection
        AnalyticsUtils.logCameraAppDetection(
            installedCount = result.systemDiagnostics.installedCameraApps.size,
            knownCount = result.systemDiagnostics.installedCameraApps.count { it.isKnownCompatible },
            defaultApp = result.systemDiagnostics.defaultCameraApp?.packageName,
            hasDefault = result.systemDiagnostics.defaultCameraApp != null
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Mark first launch as complete when activity is closed
        if (compatibilityRepository.isFirstLaunch()) {
            compatibilityRepository.markFirstLaunchComplete()
        }
    }
}

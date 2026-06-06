package com.garmin.android.apps.camera.click.comm.dialogs

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.DeveloperNoteContent
import com.garmin.android.apps.camera.click.comm.repository.CompatibilityCheckRepository
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * DialogFragment that displays developer note popup with user interaction handling.
 * Shows periodic messages from the app developer with options to visit website or send email.
 */
@AndroidEntryPoint
class DeveloperNoteDialogFragment : DialogFragment() {

    @Inject
    lateinit var compatibilityRepository: CompatibilityCheckRepository

    companion object {
        private const val TAG = "DeveloperNoteDialog"
        private const val ARG_CONTENT = "content"
        private const val WEBSITE_URL = "https://CalebSeely.com"

        /**
         * Creates a new instance of the dialog with the specified content.
         * 
         * @param content The DeveloperNoteContent to display
         * @return New DeveloperNoteDialogFragment instance
         */
        fun newInstance(content: DeveloperNoteContent): DeveloperNoteDialogFragment {
            return DeveloperNoteDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CONTENT, content)
                }
            }
        }
    }

    private var content: DeveloperNoteContent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Retrieve content from arguments
        content = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_CONTENT, DeveloperNoteContent::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_CONTENT) as? DeveloperNoteContent
        }
        
        setStyle(STYLE_NO_TITLE, R.style.DeveloperNoteDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_developer_note, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val currentContent = content
        if (currentContent == null) {
            Log.e(TAG, "No content provided to dialog")
            dismiss()
            return
        }

        setupClickListeners(view, currentContent)
        logPopupShown(currentContent)

        context?.let { ctx ->
            val isTestPopup = tag == "developer_note_test"
            if (!isTestPopup) {
                DeveloperNoteManager.recordPopupShown(ctx)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return try {
            val dialog = super.onCreateDialog(savedInstanceState)
            
            // Make dialog cancelable by touching outside
            dialog.setCanceledOnTouchOutside(true)
            
            Log.d(TAG, "Dialog created successfully")
            
            // Log dialog creation analytics
            content?.let { content ->
                val params = android.os.Bundle().apply {
                    putInt("version", content.version)
                    putString("lifecycle_event", "created")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_dialog_lifecycle", params)
            }
            
            dialog
        } catch (e: Exception) {
            Log.e(TAG, "Error creating dialog", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "dialog_creation_failed", e.message ?: "unknown")
            
            // Return a basic dialog as fallback
            super.onCreateDialog(savedInstanceState)
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Configure dialog width for better readability
        try {
            dialog?.window?.let { window ->
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                
                // Set dialog width to 85% of screen width, with min and max constraints
                val minWidth = (320 * displayMetrics.density).toInt() // 320dp minimum
                val maxWidth = (480 * displayMetrics.density).toInt() // 480dp maximum
                val preferredWidth = (screenWidth * 0.85).toInt()
                
                val dialogWidth = when {
                    preferredWidth < minWidth -> minWidth
                    preferredWidth > maxWidth -> maxWidth
                    else -> preferredWidth
                }
                
                window.setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                Log.d(TAG, "Dialog width set to: ${dialogWidth}px (screen: ${screenWidth}px)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting dialog dimensions", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Setup click listeners for all interactive elements
     */
    private fun setupClickListeners(view: View, content: DeveloperNoteContent) {
        // Close button
        view.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            logPopupDismissed(content)
            dismissSmoothly()
        }
        
        // Website button
        view.findViewById<MaterialButton>(R.id.btn_website)?.setOnClickListener {
            logWebsiteClicked(content)
            openWebsite()
            dismissSmoothly()
        }
        
        // Review button
        view.findViewById<MaterialButton>(R.id.btn_email)?.setOnClickListener {
            logReviewClicked(content)
            openReviewPrompt()
            dismissSmoothly()
        }
    }

    /**
     * Dismiss dialog smoothly without custom animations to prevent shadow artifacts
     */
    private fun dismissSmoothly() {
        try {
            // Skip custom animations to prevent the dark shadow artifact
            // The system's default dismiss animation is smoother and doesn't cause visual glitches
            dismiss()
            Log.d(TAG, "Dialog dismissed smoothly")
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing dialog", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "dismiss_failed", e.message ?: "unknown")
        }
    }

    /**
     * Open website in default browser with comprehensive error handling
     * Uses direct startActivity approach to work around Android 11+ package visibility restrictions
     */
    private fun openWebsite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))
            
            // On Android 11+, resolveActivity may return null due to package visibility restrictions
            // So we'll try to start the activity directly and catch any exceptions
            startActivity(intent)
            
            Log.d(TAG, "Website opened successfully: $WEBSITE_URL")
            FirebaseCrashlytics.getInstance().log("Developer note website opened successfully")
            
            // Log successful website opening
            val params = android.os.Bundle().apply {
                putString("url", WEBSITE_URL)
                putBoolean("success", true)
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_website_open", params)
            
        } catch (e: ActivityNotFoundException) {
            // This exception is thrown when no app can handle the intent
            Log.w(TAG, "No app found to handle website URL: $WEBSITE_URL", e)
            FirebaseCrashlytics.getInstance().log("No app found to handle website URL")
            AnalyticsUtils.logError("developer_note", "no_browser_app", WEBSITE_URL)
            
            // Show user-friendly message
            Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening website", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "website_open_failed", e.message ?: "unknown")
            
            // Log failed website opening
            val params = android.os.Bundle().apply {
                putString("url", WEBSITE_URL)
                putBoolean("success", false)
                putString("error", e.message ?: "unknown")
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_website_open", params)
            
            // Show user-friendly error message
            Toast.makeText(context, R.string.error_open_website, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open Google Play Store page for review with comprehensive error handling
     * Uses direct Play Store intent for reliable navigation to review page
     */
    private fun openReviewPrompt() {
        try {
            val activity = requireActivity()
            
            Log.d(TAG, "Opening Google Play Store for review from developer note")
            FirebaseCrashlytics.getInstance().log("Developer note Play Store review initiated")
            
            // Try to open Play Store app first
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
                activity.startActivity(playStoreIntent)
                
                Log.d(TAG, "Successfully opened Play Store app for review")
                
                // Log successful Play Store opening
                val params = android.os.Bundle().apply {
                    putBoolean("success", true)
                    putString("method", "play_store_app")
                    putString("source", "developer_note")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_review_open", params)
                
            } catch (e: ActivityNotFoundException) {
                // Fallback to web browser if Play Store app is not available
                Log.w(TAG, "Play Store app not found, using web fallback")
                
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}"))
                    activity.startActivity(webIntent)
                    
                    Log.d(TAG, "Successfully opened Play Store web page for review")
                    
                    // Log successful web fallback
                    val params = android.os.Bundle().apply {
                        putBoolean("success", true)
                        putString("method", "web_browser")
                        putString("source", "developer_note")
                        putString("timestamp", System.currentTimeMillis().toString())
                    }
                    AnalyticsUtils.logEvent("developer_note_review_open", params)
                    
                } catch (e2: Exception) {
                    Log.e(TAG, "Error opening Play Store web page", e2)
                    FirebaseCrashlytics.getInstance().recordException(e2)
                    
                    // Log failed web fallback
                    val params = android.os.Bundle().apply {
                        putBoolean("success", false)
                        putString("error", e2.message ?: "unknown")
                        putString("method", "web_browser_failed")
                        putString("source", "developer_note")
                        putString("timestamp", System.currentTimeMillis().toString())
                    }
                    AnalyticsUtils.logEvent("developer_note_review_open", params)
                    
                    Toast.makeText(context, R.string.error_open_play_store, Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening review page", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "review_open_failed", e.message ?: "unknown")
            
            // Log failed review opening
            val params = android.os.Bundle().apply {
                putBoolean("success", false)
                putString("error", e.message ?: "unknown")
                putString("source", "developer_note")
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_review_open", params)
            
            // Show user-friendly error message
            Toast.makeText(context, R.string.error_open_review, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Log analytics event for popup shown with comprehensive error handling
     */
    private fun logPopupShown(content: DeveloperNoteContent) {
        try {
            AnalyticsUtils.logFeatureUsage("developer_note", "popup_shown", true)

            // Additional detailed analytics
            val params = android.os.Bundle().apply {
                putInt("version", content.version)
                putString("title", content.title)
                putString("message_length", content.message.length.toString())
                putString("website_button_text", content.websiteButtonText)
                putString("email_button_text", content.emailButtonText)
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_popup_shown", params)

            Log.d(TAG, "Analytics logged for popup shown - version ${content.version}")
            FirebaseCrashlytics.getInstance().log("Developer note popup analytics logged - version ${content.version}")

            // Collect compatibility check analytics when developer note is shown
            logCompatibilityAnalytics()

        } catch (e: Exception) {
            Log.e(TAG, "Error logging popup shown analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "analytics_popup_shown_failed", e.message ?: "unknown")
        }
    }

    /**
     * Log system compatibility analytics when developer note popup is shown
     * This ensures we capture device/compatibility data even if users never visit the System Status page
     */
    private fun logCompatibilityAnalytics() {
        try {
            // Perform compatibility check
            val result = compatibilityRepository.performCompatibilityCheck(forceRefresh = false)

            val switchAccessCheck = result.checks.find { it.id == "switch_access" }
            val accessibilityCheck = result.checks.find { it.id == "accessibility_service" }
            val notificationCheck = result.checks.find { it.id == "notification_permission" }
            val garminConnectCheck = result.checks.find { it.id == "garmin_connect" }

            // Log compatibility check (same as SystemStatusActivity but from developer note popup)
            AnalyticsUtils.logCompatibilityCheck(
                overallStatus = result.overallStatus.name,
                checksPassed = result.checks.count { it.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.PASS },
                checksWarning = result.checks.count { it.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.WARNING },
                checksInfo = result.checks.count { it.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.INFO },
                accessibilityEnabled = accessibilityCheck?.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.PASS,
                switchAccessEnabled = switchAccessCheck?.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.PASS,
                notificationsGranted = notificationCheck?.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.PASS,
                garminConnectInstalled = garminConnectCheck?.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.PASS,
                deviceManufacturer = result.systemDiagnostics.manufacturer,
                deviceModel = result.systemDiagnostics.model,
                androidVersion = result.systemDiagnostics.androidVersion,
                cameraAppsCount = result.systemDiagnostics.installedCameraApps.size,
                defaultCamera = result.systemDiagnostics.defaultCameraApp?.packageName,
                isFirstLaunch = false // Not first launch since developer note appears after 5 days
            )

            // Log Switch Access status
            AnalyticsUtils.logSwitchAccessStatus(
                enabled = switchAccessCheck?.status == com.garmin.android.apps.camera.click.comm.model.CheckStatus.PASS,
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

            Log.d(TAG, "Compatibility analytics logged from developer note popup")

        } catch (e: Exception) {
            Log.e(TAG, "Error logging compatibility analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            // Don't fail the popup if analytics fail
        }
    }

    /**
     * Log analytics event for popup dismissed with comprehensive error handling
     */
    private fun logPopupDismissed(content: DeveloperNoteContent) {
        try {
            AnalyticsUtils.logFeatureUsage("developer_note", "popup_dismissed", true)
            
            val params = android.os.Bundle().apply {
                putInt("version", content.version)
                putString("dismissal_method", "close_button")
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_popup_dismissed", params)
            
            Log.d(TAG, "Analytics logged for popup dismissed - version ${content.version}")
            FirebaseCrashlytics.getInstance().log("Developer note popup dismissed analytics logged - version ${content.version}")
            
            // Record dismissal in manager (but not for test popups)
            context?.let { ctx ->
                val isTestPopup = tag == "developer_note_test"
                if (!isTestPopup) {
                    DeveloperNoteManager.recordPopupDismissed(ctx)
                    Log.d(TAG, "Recorded popup dismissal in manager")
                } else {
                    Log.d(TAG, "Skipped recording popup dismissal - this is a test popup")
                }
            } ?: Log.w(TAG, "Context is null, cannot record dismissal in manager")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging popup dismissed analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "analytics_popup_dismissed_failed", e.message ?: "unknown")
        }
    }

    /**
     * Log analytics event for website button clicked with comprehensive error handling
     */
    private fun logWebsiteClicked(content: DeveloperNoteContent) {
        try {
            AnalyticsUtils.logFeatureUsage("developer_note", "website_clicked", true)
            
            val params = android.os.Bundle().apply {
                putInt("version", content.version)
                putString("url", WEBSITE_URL)
                putString("button_text", content.websiteButtonText)
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_website_clicked", params)
            
            Log.d(TAG, "Analytics logged for website clicked - version ${content.version}")
            FirebaseCrashlytics.getInstance().log("Developer note website clicked analytics logged - version ${content.version}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging website clicked analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "analytics_website_clicked_failed", e.message ?: "unknown")
        }
    }

    /**
     * Log analytics event for review button clicked with comprehensive error handling
     */
    private fun logReviewClicked(content: DeveloperNoteContent) {
        try {
            AnalyticsUtils.logFeatureUsage("developer_note", "review_clicked", true)
            
            val params = android.os.Bundle().apply {
                putInt("version", content.version)
                putString("button_text", content.emailButtonText)
                putString("source", "developer_note")
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_review_clicked", params)
            
            Log.d(TAG, "Analytics logged for review clicked - version ${content.version}")
            FirebaseCrashlytics.getInstance().log("Developer note review clicked analytics logged - version ${content.version}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging review clicked analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "analytics_review_clicked_failed", e.message ?: "unknown")
        }
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        
        try {
            // Log cancellation as dismissal with specific method
            content?.let { content ->
                AnalyticsUtils.logFeatureUsage("developer_note", "popup_dismissed", true)
                
                val params = android.os.Bundle().apply {
                    putInt("version", content.version)
                    putString("dismissal_method", "cancel")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_popup_dismissed", params)
                
                Log.d(TAG, "Dialog cancelled - version ${content.version}")
                FirebaseCrashlytics.getInstance().log("Developer note dialog cancelled - version ${content.version}")
                
                // Record dismissal in manager (but not for test popups)
                context?.let { ctx ->
                    val isTestPopup = tag == "developer_note_test"
                    if (!isTestPopup) {
                        DeveloperNoteManager.recordPopupDismissed(ctx)
                        Log.d(TAG, "Recorded popup cancellation in manager")
                    } else {
                        Log.d(TAG, "Skipped recording popup cancellation - this is a test popup")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging dialog cancellation", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        
        try {
            Log.d(TAG, "Developer note dialog dismissed")
            FirebaseCrashlytics.getInstance().log("Developer note dialog dismissed")
            
            // Log dialog lifecycle event
            content?.let { content ->
                val params = android.os.Bundle().apply {
                    putInt("version", content.version)
                    putString("lifecycle_event", "dismissed")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_dialog_lifecycle", params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging dialog dismissal", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
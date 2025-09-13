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
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.DeveloperNoteContent
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.DeveloperNoteManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.widget.Toast

/**
 * DialogFragment that displays developer note popup with user interaction handling.
 * Shows periodic messages from the app developer with options to visit website or send email.
 */
class DeveloperNoteDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "DeveloperNoteDialog"
        private const val ARG_CONTENT = "content"
        private const val WEBSITE_URL = "https://CalebSeely.com"
        private const val EMAIL_ADDRESS = "CalebSeely@gmail.com"

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
        
        // Set dialog style for proper appearance
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Dialog)
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

        setupViews(view, currentContent)
        setupClickListeners(view, currentContent)
        
        // Apply fade-in animation
        applyFadeInAnimation(view)
        
        // Log analytics event for popup shown
        logPopupShown(currentContent)
        
        // Record popup shown in manager (but not for test popups)
        context?.let { ctx ->
            // Check if this is a test popup by looking at the fragment tag
            val isTestPopup = tag == "developer_note_test"
            if (!isTestPopup) {
                DeveloperNoteManager.recordPopupShown(ctx)
                Log.d(TAG, "Recorded popup shown in manager")
            } else {
                Log.d(TAG, "Skipped recording popup shown - this is a test popup")
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
     * Setup all views with content data
     */
    private fun setupViews(view: View, content: DeveloperNoteContent) {
        try {
            // Set title
            view.findViewById<TextView>(R.id.tv_title)?.text = content.title
            
            // Set message content
            view.findViewById<TextView>(R.id.tv_message)?.text = content.message
            
            // Set button texts
            view.findViewById<MaterialButton>(R.id.btn_website)?.text = content.websiteButtonText
            view.findViewById<MaterialButton>(R.id.btn_email)?.text = content.emailButtonText
            
            // Load avatar image with enhanced error handling
            loadAvatarImage(view, content)
            
            Log.d(TAG, "Views setup completed for version ${content.version}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up views", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "view_setup_failed", e.message ?: "unknown")
        }
    }
    
    /**
     * Load avatar image with comprehensive error handling
     */
    private fun loadAvatarImage(view: View, content: DeveloperNoteContent) {
        val avatarImageView = view.findViewById<ImageView>(R.id.iv_avatar)
        avatarImageView?.let { imageView ->
            try {
                // Use direct image resource loading since Glide is not available
                imageView.setImageResource(R.drawable.caleb_avatar)
                Log.d(TAG, "Avatar image loaded successfully")
                
                // Log successful image loading
                val params = android.os.Bundle().apply {
                    putInt("version", content.version)
                    putBoolean("success", true)
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_image_load", params)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading avatar image", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                AnalyticsUtils.logError("developer_note", "image_load_failed", e.message ?: "unknown")
                
                // Log failed image loading
                val params = android.os.Bundle().apply {
                    putInt("version", content.version)
                    putBoolean("success", false)
                    putString("error", e.message ?: "unknown")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_image_load", params)
                
                // Fallback - leave default image from layout or set a placeholder
                try {
                    // Try to set a fallback drawable if available
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "Error setting fallback image", fallbackException)
                    // Leave whatever default is in the layout
                }
            }
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
        
        // Email button
        view.findViewById<MaterialButton>(R.id.btn_email)?.setOnClickListener {
            logEmailClicked(content)
            openEmailClient()
            dismissSmoothly()
        }
    }

    /**
     * Apply smooth appearance to the dialog (removed custom animation to prevent artifacts)
     */
    private fun applyFadeInAnimation(view: View) {
        try {
            // Removed custom fade-in animation for consistency with dismissal behavior
            // Android's default dialog appearance animation is smoother and more reliable
            Log.d(TAG, "Dialog appeared smoothly with system animation")
        } catch (e: Exception) {
            Log.e(TAG, "Error in dialog appearance", e)
            FirebaseCrashlytics.getInstance().recordException(e)
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
            Toast.makeText(context, "No browser app found to open website", Toast.LENGTH_SHORT).show()
            
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
            Toast.makeText(context, "Unable to open website. Please try again later.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open email client with pre-filled recipient and comprehensive error handling
     * Uses direct startActivity approach to work around Android 11+ package visibility restrictions
     */
    private fun openEmailClient() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$EMAIL_ADDRESS")
                putExtra(Intent.EXTRA_SUBJECT, "CameraClick App - Contact")
            }
            
            // On Android 11+, resolveActivity may return null due to package visibility restrictions
            // So we'll try to start the activity directly and catch any exceptions
            startActivity(intent)
            
            Log.d(TAG, "Email client opened successfully for: $EMAIL_ADDRESS")
            FirebaseCrashlytics.getInstance().log("Developer note email client opened successfully")
            
            // Log successful email opening
            val params = android.os.Bundle().apply {
                putString("email", EMAIL_ADDRESS)
                putBoolean("success", true)
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_email_open", params)
            
        } catch (e: ActivityNotFoundException) {
            // This exception is thrown when no app can handle the intent
            Log.w(TAG, "No email app found to handle mailto intent", e)
            FirebaseCrashlytics.getInstance().log("No email app found to handle mailto intent")
            AnalyticsUtils.logError("developer_note", "no_email_app", EMAIL_ADDRESS)
            
            // Fallback: try to copy email to clipboard and show message
            try {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Email", EMAIL_ADDRESS)
                clipboard.setPrimaryClip(clip)
                
                Toast.makeText(context, "No email app found. Email address copied to clipboard: $EMAIL_ADDRESS", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Email address copied to clipboard as fallback")
                
            } catch (clipboardException: Exception) {
                Log.e(TAG, "Error copying email to clipboard", clipboardException)
                Toast.makeText(context, "No email app found. Please email: $EMAIL_ADDRESS", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening email client", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "email_open_failed", e.message ?: "unknown")
            
            // Log failed email opening
            val params = android.os.Bundle().apply {
                putString("email", EMAIL_ADDRESS)
                putBoolean("success", false)
                putString("error", e.message ?: "unknown")
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_email_open", params)
            
            // Show user-friendly error message with email address
            Toast.makeText(context, "Unable to open email app. Please email: $EMAIL_ADDRESS", Toast.LENGTH_LONG).show()
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
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging popup shown analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "analytics_popup_shown_failed", e.message ?: "unknown")
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
     * Log analytics event for email button clicked with comprehensive error handling
     */
    private fun logEmailClicked(content: DeveloperNoteContent) {
        try {
            AnalyticsUtils.logFeatureUsage("developer_note", "email_clicked", true)
            
            val params = android.os.Bundle().apply {
                putInt("version", content.version)
                putString("email", EMAIL_ADDRESS)
                putString("button_text", content.emailButtonText)
                putString("timestamp", System.currentTimeMillis().toString())
            }
            AnalyticsUtils.logEvent("developer_note_email_clicked", params)
            
            Log.d(TAG, "Analytics logged for email clicked - version ${content.version}")
            FirebaseCrashlytics.getInstance().log("Developer note email clicked analytics logged - version ${content.version}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging email clicked analytics", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "analytics_email_clicked_failed", e.message ?: "unknown")
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
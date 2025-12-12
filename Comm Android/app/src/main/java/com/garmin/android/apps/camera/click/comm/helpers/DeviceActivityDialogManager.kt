package com.garmin.android.apps.camera.click.comm.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.repository.DeveloperNoteContentRepository
import com.garmin.android.apps.camera.click.comm.dialogs.DeveloperNoteDialogFragment
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.InAppReviewUtils
import com.google.firebase.crashlytics.FirebaseCrashlytics

private const val TAG = "DeviceActivityDialog"

/**
 * DeviceActivityDialogManager - Centralized dialog management
 *
 * This class manages all dialog interactions for DeviceActivity, providing consistent
 * dialog behavior and styling across the activity.
 *
 * Features:
 * - Accessibility settings dialog
 * - App not installed dialog
 * - Developer note popup
 * - In-app review prompt
 * - App store rating flow
 *
 * Benefits:
 * - Eliminates code duplication
 * - Consistent dialog styling
 * - Easy to modify dialog behavior globally
 * - Testable dialog logic
 */
class DeviceActivityDialogManager(private val activity: Activity) {

    /**
     * Show accessibility permission dialog
     *
     * Displays a non-dismissible dialog explaining that accessibility permission is required
     * and provides a button to open the accessibility settings.
     */
    fun showAccessibilityDialog() {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.accessibility_dialog_title)
            .setMessage(R.string.accessibility_dialog_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                FirebaseCrashlytics.getInstance().log("Opening accessibility settings")
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(false)  // Make dialog non-dismissible
            .create()

        dialog.show()

        // Apply custom styling to the buttons after the dialog is shown
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(ContextCompat.getColor(activity, R.color.primary_color))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
        }
    }

    /**
     * Show app not installed dialog
     *
     * Displays a dialog informing the user that the watch app is not installed.
     */
    fun showAppNotInstalledDialog() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.missing_widget)
            .setMessage(R.string.missing_widget_message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .show()
    }

    /**
     * Show developer note popup
     *
     * Displays a developer note dialog for the specified version. The dialog content
     * is loaded from DeveloperNoteContentRepository.
     *
     * @param version The version number of the developer note to show
     * @param isAutomatic Whether this dialog was triggered automatically
     */
    fun showDeveloperNotePopup(version: Int, isAutomatic: Boolean) {
        try {
            val content = DeveloperNoteContentRepository.getContentForVersion(activity, version)

            if (content != null) {
                Log.d(TAG, "Showing developer note popup - version $version (automatic: $isAutomatic)")

                val dialog = DeveloperNoteDialogFragment.newInstance(content)
                val tag = if (isAutomatic) "developer_note_auto" else "developer_note_manual"

                // Cast to FragmentActivity to get supportFragmentManager
                val fragmentActivity = activity as? androidx.fragment.app.FragmentActivity
                fragmentActivity?.let {
                    dialog.show(it.supportFragmentManager, tag)
                }

                val params = Bundle().apply {
                    putInt("version", version)
                    putBoolean("automatic_trigger", isAutomatic)
                    putBoolean("from_device_activity", true)
                    putString("location", "device_activity")
                    putString("timestamp", System.currentTimeMillis().toString())
                }
                AnalyticsUtils.logEvent("developer_note_popup_shown", params)

                FirebaseCrashlytics.getInstance().log("Developer note popup shown - version $version")
            } else {
                Log.w(TAG, "No content found for developer note version $version")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing developer note popup", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            AnalyticsUtils.logError("developer_note", "popup_failed", e.message ?: "unknown")
        }
    }

    /**
     * Check and show in-app review prompt if appropriate
     *
     * Checks if we should show the review prompt based on InAppReviewUtils logic
     * and displays it at natural moments in the app flow (like connecting to a device).
     */
    fun checkAndShowReviewPrompt() {
        if (InAppReviewUtils.shouldShowReviewPrompt(activity)) {
            Log.d(TAG, "Showing in-app review prompt")
            AnalyticsUtils.logFeatureUsage("in_app_review", "prompt_shown", true)

            InAppReviewUtils.launchInAppReview(activity) { success ->
                Log.d(TAG, "In-app review completed: $success")
                AnalyticsUtils.logFeatureUsage("in_app_review", "completed", success)
            }
        }
    }

    /**
     * Open the app store for rating
     *
     * Tries to open Google Play Store app first, then falls back to web browser
     * if the Play Store app is not available.
     */
    fun openAppStoreForRating() {
        try {
            AnalyticsUtils.logFeatureUsage("rate_app", "menu_click", true)

            // Try to open Google Play Store app
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
            activity.startActivity(playStoreIntent)

            Log.d(TAG, "Opened Play Store app for rating")
            AnalyticsUtils.logFeatureUsage("rate_app", "play_store_opened", true)

            // Show appreciation message
            Toast.makeText(activity, "Thanks for taking the time to rate CameraClick! 📸⭐", Toast.LENGTH_SHORT).show()

        } catch (e: ActivityNotFoundException) {
            // Fallback to web browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}"))
                activity.startActivity(webIntent)

                Log.d(TAG, "Opened Play Store web page for rating")
                AnalyticsUtils.logFeatureUsage("rate_app", "web_store_opened", true)

                // Show appreciation message
                Toast.makeText(activity, "Thanks for taking the time to rate CameraClick! 📸⭐", Toast.LENGTH_SHORT).show()

            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open app store for rating", e2)
                AnalyticsUtils.logError("rate_app", "store_open_failed", e2.message ?: "unknown")
                Toast.makeText(activity, "Unable to open app store", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

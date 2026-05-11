package com.garmin.android.apps.camera.click.comm.helpers

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.views.ButtonLocationOverlay
import com.google.android.material.card.MaterialCardView

/**
 * DeviceActivityViews - Type-safe view holder for DeviceActivity
 *
 * This data class holds all view references for DeviceActivity, eliminating the need
 * for nullable view properties scattered throughout the activity.
 *
 * Benefits:
 * - Type-safe view access
 * - Single initialization point
 * - Clear view lifecycle
 * - No more null checks
 *
 * @property toolbar The toolbar at the top of the activity
 * @property deviceNameView TextView displaying the device name
 * @property deviceStatusView TextView displaying the device status
 * @property deviceStatusBadge MaterialCardView containing the status view
 * @property openAppButtonView MaterialCardView for the "Open App" button
 * @property openAppTextView TextView for the "Open App" button text
 * @property autoLaunchSwitch Switch for auto-launch camera preference
 * @property buttonLocationOverlay Overlay showing button location preview
 * @property tapToSendButton MaterialCardView for sending test message
 * @property cameraButton LinearLayout for launching camera
 * @property squareCameraButton View for the square camera launch button
 * @property manualSelectionButton MaterialCardView for manual button selection
 * @property deviceInfoSection View for the entire device info section
 * @property mainCard View for the main card
 * @property autoLaunchSection View for the auto-launch section
 */
data class DeviceActivityViews(
    val toolbar: Toolbar,
    val deviceNameView: TextView,
    val deviceStatusView: TextView,
    val deviceStatusBadge: MaterialCardView?,
    val openAppButtonView: MaterialCardView,
    val openAppTextView: TextView,
    val autoLaunchSwitch: SwitchCompat,
    val buttonLocationOverlay: ButtonLocationOverlay,
    val tapToSendButton: MaterialCardView,
    val cameraButton: LinearLayout,
    val squareCameraButton: View,
    val manualSelectionButton: MaterialCardView,
    val deviceInfoSection: View,
    val mainCard: View,
    val autoLaunchSection: View,
    val setupIncompleteBanner: MaterialCardView?,
    val btnBannerConfigure: Button?
) {
    companion object {
        /**
         * Bind all views from the activity
         *
         * @param activity The DeviceActivity instance
         * @return DeviceActivityViews with all views bound
         */
        fun bind(activity: Activity): DeviceActivityViews {
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val deviceNameView = activity.findViewById<TextView>(R.id.devicename)
            val deviceStatusView = activity.findViewById<TextView>(R.id.devicestatus)
            val deviceStatusBadge = deviceStatusView.parent as? MaterialCardView

            return DeviceActivityViews(
                toolbar = toolbar,
                deviceNameView = deviceNameView,
                deviceStatusView = deviceStatusView,
                deviceStatusBadge = deviceStatusBadge,
                openAppButtonView = activity.findViewById(R.id.openapp),
                openAppTextView = activity.findViewById(R.id.openapp_text),
                autoLaunchSwitch = activity.findViewById(R.id.auto_launch_switch),
                buttonLocationOverlay = activity.findViewById(R.id.button_location_overlay),
                tapToSendButton = activity.findViewById(R.id.taptosend),
                cameraButton = activity.findViewById(R.id.camera_button),
                squareCameraButton = activity.findViewById(R.id.square_camera_button),
                manualSelectionButton = activity.findViewById(R.id.manual_shutter_selection_button),
                deviceInfoSection = activity.findViewById(R.id.device_info_section),
                mainCard = activity.findViewById(R.id.main_card),
                autoLaunchSection = activity.findViewById(R.id.auto_launch_section),
                setupIncompleteBanner = activity.findViewById(R.id.setup_incomplete_banner),
                btnBannerConfigure = activity.findViewById(R.id.btn_banner_configure)
            )
        }
    }
}

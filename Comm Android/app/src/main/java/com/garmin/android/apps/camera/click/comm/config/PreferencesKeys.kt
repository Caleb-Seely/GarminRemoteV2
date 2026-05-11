package com.garmin.android.apps.camera.click.comm.config

/**
 * Centralized SharedPreferences keys and preference file names.
 * This ensures consistency across the application and prevents typos.
 */
object PreferencesKeys {

    /**
     * SharedPreferences file names
     */
    object Files {
        const val CAMERA_CLICK_PREFS = "CameraClickPrefs"
        const val ACCESSIBILITY_PREFS = "AccessibilityPrefs"
        const val DEVELOPER_NOTE_PREFS = "developer_note_prefs"
        const val IN_APP_REVIEW_PREFS = "in_app_review_prefs"
    }

    /**
     * Main application preferences (CameraClickPrefs)
     */
    object App {
        const val AUTO_LAUNCH_CAMERA = "auto_launch_camera"
        const val PREFERRED_DEVICE_ID = "preferred_device_id"
        const val SETUP_COMPLETE = "setup_complete"
    }

    /**
     * Accessibility service preferences (AccessibilityPrefs)
     */
    object Accessibility {
        const val BUTTON_INFO = "last_known_button_info"
        const val DETECTION_STATS = "detection_stats"
        const val CANDIDATE_LISTS = "candidate_lists_by_app"
        const val USER_PREFERRED_BUTTONS = "user_preferred_buttons"
    }

    /**
     * Developer note preferences (developer_note_prefs)
     */
    object DeveloperNote {
        const val FIRST_INSTALL_DATE = "developer_note_first_install_date"
        const val LAST_POPUP_DATE = "developer_note_last_popup_date"
        const val POPUP_VERSION_INDEX = "developer_note_popup_version_index"
        const val DEFER_TO_NEXT_LAUNCH = "developer_note_defer_next_launch"

        // New simplified state tracking
        const val POPUP_SHOWN = "developer_note_popup_shown"
        const val POPUP_SHOWN_DATE = "developer_note_popup_shown_date"
    }

    /**
     * In-app review preferences (in_app_review_prefs)
     */
    object Review {
        const val FIRST_INSTALL_DATE = "first_install_date"
        const val LAUNCH_COUNT = "launch_count"
        const val LAST_REVIEW_REQUEST = "last_review_request"
        const val REVIEW_COMPLETED = "review_completed"
        const val REVIEW_SHOW_COUNT = "review_show_count"
        const val REVIEW_DELAY_AFTER_DEVELOPER_NOTE = "review_delay_after_developer_note"
    }

    /**
     * Compatibility check preferences (CameraClickPrefs)
     */
    object Compatibility {
        const val FIRST_LAUNCH_COMPLETE = "first_launch_complete"
        const val LAST_CHECK_TIMESTAMP = "last_compatibility_check"
        const val SESSION_DISMISSED = "compatibility_session_dismissed"
    }
}

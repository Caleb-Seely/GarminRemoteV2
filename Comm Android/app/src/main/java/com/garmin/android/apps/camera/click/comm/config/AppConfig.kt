package com.garmin.android.apps.camera.click.comm.config

/**
 * Centralized configuration constants for the CameraClick application.
 * This file consolidates all magic numbers, timing values, and configuration
 * settings to improve maintainability and consistency.
 */
object AppConfig {

    /**
     * Garmin ConnectIQ Watch Application ID
     */
    const val COMM_WATCH_ID = "f4ac6a38-3843-4ae7-8bec-1b4f752b9d1f"

    /**
     * Developer website URL
     */
    const val WEBSITE_URL = "https://CalebSeely.com"

    /**
     * Request codes for permissions and activities
     */
    object RequestCodes {
        const val NOTIFICATION_PERMISSION = 123
        const val ACCESSIBILITY_SETTINGS = 124
    }

    /**
     * Timing-related constants
     */
    object Timing {
        /**
         * Developer note initial delay (5 days in milliseconds)
         */
        const val DEVELOPER_NOTE_INITIAL_DELAY_MS = 5 * 24 * 60 * 60 * 1000L

        /**
         * Developer note repeat interval (60 days in milliseconds)
         */
        const val DEVELOPER_NOTE_REPEAT_INTERVAL_MS = 60 * 24 * 60 * 60 * 1000L

        /**
         * Minimum days before showing review prompt
         */
        const val REVIEW_PROMPT_MIN_DAYS = 5

        /**
         * Minimum days between review prompts
         */
        const val REVIEW_PROMPT_MIN_DAYS_BETWEEN = 60

        /**
         * Handler delay for async operations (milliseconds)
         */
        const val ASYNC_HANDLER_DELAY_MS = 1000L

        /**
         * Focus before click delay (milliseconds)
         */
        const val FOCUS_CLICK_DELAY_MS = 100L

        /**
         * Short delay between operations (milliseconds)
         */
        const val SHORT_DELAY_MS = 50L
    }

    /**
     * UI-related constants
     */
    object UI {
        /**
         * Minimum button size for validity (pixels)
         */
        const val MIN_BUTTON_SIZE_PX = 50

        /**
         * Default screen dimensions
         */
        const val DEFAULT_SCREEN_WIDTH = 1080
        const val DEFAULT_SCREEN_HEIGHT = 1920

        /**
         * Button position threshold (fraction of screen height)
         * Prefer buttons in bottom half for camera apps
         */
        const val BUTTON_POSITION_THRESHOLD = 0.5f

        /**
         * Bottom center Y position threshold (fraction of screen height)
         */
        const val BOTTOM_CENTER_Y_THRESHOLD = 0.8f

        /**
         * Status bar color (black)
         */
        const val STATUS_BAR_COLOR = android.graphics.Color.BLACK
    }

    /**
     * Validation and scoring thresholds
     */
    object Validation {
        /**
         * Minimum score for button validity
         */
        const val MIN_BUTTON_VALIDATION_SCORE = 70

        /**
         * Score weights for button validation
         */
        const val SCORE_CLICKABLE = 30
        const val SCORE_ENABLED = 20
        const val SCORE_SIZE = 20
        const val SCORE_IDENTIFIERS = 15
        const val SCORE_POSITION = 10
        const val SCORE_SQUARE_SHAPE = 5
    }

    /**
     * Intent actions and extras
     */
    object Intents {
        const val ACTION_MESSAGE_RECEIVED = "com.garmin.android.apps.camera.click.comm.ACTION_MESSAGE_RECEIVED"
        const val ACTION_SEND_MESSAGE = "SEND_MESSAGE"

        const val EXTRA_DEVICE = "device"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_MESSAGE_TIME = "message_time"
        const val EXTRA_MESSAGE_TYPE = "message_type"
        const val EXTRA_MESSAGE_DETAILS = "message_details"
        const val EXTRA_IQ_DEVICE = "IQDevice"

        const val MESSAGE_TYPE_SHUTTER_SUCCESS = "SHUTTER_SUCCESS"
        const val MESSAGE_TYPE_SHUTTER_FAILED = "SHUTTER_FAILED"
    }

    /**
     * Notification configuration
     */
    object Notifications {
        const val CHANNEL_ID = "GarminMessageChannel"
        const val NOTIFICATION_ID = 1
    }
}

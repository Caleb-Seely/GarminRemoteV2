package com.garmin.android.apps.camera.click.comm.detection

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScreenMetricsHelper - Provides screen dimension utilities
 *
 * This class provides access to real screen dimensions and orientation information.
 * It replaces hardcoded fallback values that were previously used in AccessibilityUtils.
 *
 * Uses dependency injection to access the application context for real-time screen metrics.
 *
 * @param context Application context injected by Hilt
 */
@Singleton
class ScreenMetricsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Get the current screen width in pixels
     *
     * @return Screen width in pixels, or 1080 as fallback
     */
    fun getScreenWidth(): Int {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            size.x
        } catch (e: Exception) {
            1080 // Fallback to common phone width
        }
    }

    /**
     * Get the current screen height in pixels
     *
     * @return Screen height in pixels, or 1920 as fallback
     */
    fun getScreenHeight(): Int {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            size.y
        } catch (e: Exception) {
            1920 // Fallback to common phone height
        }
    }

    /**
     * Get the current screen orientation
     *
     * @return Configuration.ORIENTATION_PORTRAIT or Configuration.ORIENTATION_LANDSCAPE
     */
    fun getScreenOrientation(): Int {
        return try {
            context.resources.configuration.orientation
        } catch (e: Exception) {
            Configuration.ORIENTATION_PORTRAIT // Fallback to portrait
        }
    }

    /**
     * Calculate the position description of a button on the screen
     *
     * Determines if the button is in the top/bottom half and left/center/right third
     * of the screen for human-readable position descriptions.
     *
     * @param bounds The bounds of the button
     * @return A string like "bottom-center" or "top-right"
     */
    fun calculatePositionOnScreen(bounds: Rect): String {
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        val vertical = if (centerY < screenHeight / 2) "top" else "bottom"
        val horizontal = when {
            centerX < screenWidth / 3 -> "left"
            centerX > (screenWidth * 2) / 3 -> "right"
            else -> "center"
        }

        return "$vertical-$horizontal"
    }

    /**
     * Get display metrics for the current screen
     *
     * Useful for getting density, DPI, and other detailed screen information
     *
     * @return DisplayMetrics object with screen details
     */
    fun getDisplayMetrics(): DisplayMetrics {
        return context.resources.displayMetrics
    }

    /**
     * Check if the screen is currently in landscape orientation
     *
     * @return true if landscape, false if portrait
     */
    fun isLandscape(): Boolean {
        return getScreenOrientation() == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Check if the screen is currently in portrait orientation
     *
     * @return true if portrait, false if landscape
     */
    fun isPortrait(): Boolean {
        return getScreenOrientation() == Configuration.ORIENTATION_PORTRAIT
    }
}

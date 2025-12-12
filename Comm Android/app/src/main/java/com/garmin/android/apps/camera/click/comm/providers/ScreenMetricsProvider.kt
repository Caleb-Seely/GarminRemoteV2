package com.garmin.android.apps.camera.click.comm.providers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides screen metrics and dimensions for button detection and positioning.
 */
@Singleton
class ScreenMetricsProvider @Inject constructor(
    private val context: Context
) {

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    /**
     * Get screen width in pixels
     */
    fun getScreenWidth(): Int {
        return try {
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            size.x
        } catch (e: Exception) {
            // Fallback to common screen width
            1080
        }
    }

    /**
     * Get screen height in pixels
     */
    fun getScreenHeight(): Int {
        return try {
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            size.y
        } catch (e: Exception) {
            // Fallback to common screen height
            1920
        }
    }

    /**
     * Get screen orientation
     * @return Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE, or Configuration.ORIENTATION_UNDEFINED
     */
    fun getScreenOrientation(): Int {
        return try {
            context.resources.configuration.orientation
        } catch (e: Exception) {
            Configuration.ORIENTATION_PORTRAIT
        }
    }

    /**
     * Get display metrics
     */
    fun getDisplayMetrics(): DisplayMetrics {
        return context.resources.displayMetrics
    }

    /**
     * Calculate relative position of bounds on screen
     * @return String like "bottom_center", "top_left", etc.
     */
    fun calculatePositionOnScreen(bounds: Rect): String {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val horizontalPosition = when {
            centerX < screenWidth * 0.33 -> "left"
            centerX > screenWidth * 0.67 -> "right"
            else -> "center"
        }

        val verticalPosition = when {
            centerY < screenHeight * 0.33 -> "top"
            centerY > screenHeight * 0.67 -> "bottom"
            else -> "middle"
        }

        return "${verticalPosition}_${horizontalPosition}"
    }

    /**
     * Check if a position is in the bottom center area of the screen
     * (common location for camera shutter buttons)
     */
    fun isInBottomCenter(bounds: Rect): Boolean {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val isInBottomHalf = centerY > screenHeight * 0.5f
        val isInCenterThird = centerX > screenWidth * 0.33f && centerX < screenWidth * 0.67f

        return isInBottomHalf && isInCenterThird
    }

    /**
     * Get screen density (dpi)
     */
    fun getScreenDensity(): Float {
        return context.resources.displayMetrics.density
    }

    /**
     * Convert dp to pixels
     */
    fun dpToPx(dp: Float): Int {
        return (dp * getScreenDensity()).toInt()
    }

    /**
     * Convert pixels to dp
     */
    fun pxToDp(px: Int): Float {
        return px / getScreenDensity()
    }

    /**
     * Get screen size category (small, normal, large, xlarge)
     */
    fun getScreenSizeCategory(): String {
        return when (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> "small"
            Configuration.SCREENLAYOUT_SIZE_NORMAL -> "normal"
            Configuration.SCREENLAYOUT_SIZE_LARGE -> "large"
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> "xlarge"
            else -> "undefined"
        }
    }

    /**
     * Check if device is in landscape mode
     */
    fun isLandscape(): Boolean {
        return getScreenOrientation() == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Check if device is in portrait mode
     */
    fun isPortrait(): Boolean {
        return getScreenOrientation() == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * Get screen aspect ratio (width / height)
     */
    fun getAspectRatio(): Float {
        val width = getScreenWidth()
        val height = getScreenHeight()
        return width.toFloat() / height.toFloat()
    }
}

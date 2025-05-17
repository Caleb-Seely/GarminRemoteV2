package com.garmin.android.apps.camera.click.comm.model

import android.graphics.Rect

/**
 * Data class to store shutter button location information.
 * This class holds the bounds and package name of a camera app's shutter button.
 *
 * @property bounds The screen coordinates of the shutter button
 * @property packageName The package name of the camera app
 */
data class ShutterButtonInfo(
    val bounds: Rect,
    val packageName: String
) 
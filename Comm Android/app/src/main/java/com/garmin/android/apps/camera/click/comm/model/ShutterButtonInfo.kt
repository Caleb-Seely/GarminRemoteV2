package com.garmin.android.apps.camera.click.comm.model

import android.graphics.Rect

/**
 * Data class to store button location and identification information.
 * This class holds the bounds and identifying information of a button.
 *
 * @property bounds The screen coordinates of the button
 * @property packageName The package name of the app containing the button
 * @property contentDescription The content description of the button
 * @property resourceId The resource ID of the button
 * @property className The class name of the button
 * @property text The text content of the button
 */
data class ShutterButtonInfo(
    val bounds: Rect,
    val packageName: String,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val text: String? = null
) 
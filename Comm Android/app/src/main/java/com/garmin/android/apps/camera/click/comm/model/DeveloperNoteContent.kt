package com.garmin.android.apps.camera.click.comm.model

import java.io.Serializable

/**
 * Data class representing the content for a developer note popup.
 * Contains all the text and metadata needed to display a specific version of the popup.
 *
 * @param version The version number (0-3) of this content variation
 * @param title The title text to display in the popup
 * @param message The main message body text
 * @param websiteButtonText The text for the website action button
 * @param emailButtonText The text for the email action button
 */
data class DeveloperNoteContent(
    val version: Int,
    val title: String,
    val message: String,
    val websiteButtonText: String = "Visit My Website",
    val emailButtonText: String = "Email"
) : Serializable
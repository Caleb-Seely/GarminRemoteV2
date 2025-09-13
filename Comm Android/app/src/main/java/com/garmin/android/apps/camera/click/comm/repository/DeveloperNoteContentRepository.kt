package com.garmin.android.apps.camera.click.comm.repository

import com.garmin.android.apps.camera.click.comm.model.DeveloperNoteContent

/**
 * Repository object that provides all developer note content variations.
 * Contains the 4 different message versions that cycle through over time.
 * Content is loaded from string resources for proper localization support.
 */
object DeveloperNoteContentRepository {

    private var contentVariations: List<DeveloperNoteContent>? = null

    /**
     * Initialize content variations from string resources.
     * This should be called once with a Context to load the strings.
     */
    private fun initializeContent(context: android.content.Context) {
        if (contentVariations == null) {
            contentVariations = listOf(
                // Version 0: Introduction and project growth focus (Requirements 3.1)
                DeveloperNoteContent(
                    version = 0,
                    title = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_title),
                    message = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_message_v1),
                    websiteButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_website_button),
                    emailButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_email_button)
                ),
                
                // Version 1: Continued opportunity seeking focus (Requirements 3.2)
                DeveloperNoteContent(
                    version = 1,
                    title = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_title),
                    message = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_message_v2),
                    websiteButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_website_button),
                    emailButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_email_button)
                ),
                
                // Version 2: Community and usefulness focus (Requirements 3.3)
                DeveloperNoteContent(
                    version = 2,
                    title = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_title),
                    message = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_message_v3),
                    websiteButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_website_button),
                    emailButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_email_button)
                ),
                
                // Version 3: Personal persistence and scale focus (Requirements 3.4)
                DeveloperNoteContent(
                    version = 3,
                    title = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_title),
                    message = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_message_v4),
                    websiteButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_website_button),
                    emailButtonText = context.getString(com.garmin.android.apps.camera.click.comm.R.string.developer_note_email_button)
                )
            )
        }
    }

    /**
     * Gets the content for a specific version number.
     * 
     * @param context Android context needed to load string resources
     * @param version The version number (0-3) to retrieve
     * @return The DeveloperNoteContent for the specified version, or null if version is invalid
     */
    fun getContentForVersion(context: android.content.Context, version: Int): DeveloperNoteContent? {
        initializeContent(context)
        return contentVariations?.find { it.version == version }
    }
    
    /**
     * Gets the content for a specific version number (legacy method for backward compatibility).
     * This method is deprecated - use getContentForVersion(context, version) instead.
     * 
     * @param version The version number (0-3) to retrieve
     * @return The DeveloperNoteContent for the specified version, or null if version is invalid
     */
    @Deprecated("Use getContentForVersion(context, version) instead for proper string resource loading")
    fun getContentForVersion(version: Int): DeveloperNoteContent? {
        // Fallback to hardcoded content if no context available
        val fallbackContent = listOf(
            DeveloperNoteContent(
                version = 0,
                title = "Hi, I'm Caleb",
                message = "I built CameraClick as a side project, and it's grown into something that helps people every day. I'd love to keep working on meaningful projects like this full-time—if you know of opportunities, please visit my website.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            ),
            DeveloperNoteContent(
                version = 1,
                title = "Hi, I'm Caleb",
                message = "Thanks for continuing to use CameraClick! I'm still actively seeking new opportunities to build impactful software. If you're hiring or know someone who is, I'd appreciate you checking out my work.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            ),
            DeveloperNoteContent(
                version = 2,
                title = "Hi, I'm Caleb",
                message = "It's amazing to see how CameraClick has become useful to so many people. Building tools that genuinely help others is what drives me as a developer. I'm always open to discussing new projects and opportunities.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            ),
            DeveloperNoteContent(
                version = 3,
                title = "Hi, I'm Caleb",
                message = "CameraClick started as a simple idea and has grown beyond what I imagined. I believe in the power of persistent, thoughtful development. If you're looking for someone who builds with purpose, let's connect.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            )
        )
        return fallbackContent.find { it.version == version }
    }

    /**
     * Gets all available content variations.
     * 
     * @param context Android context needed to load string resources
     * @return List of all DeveloperNoteContent variations
     */
    fun getAllContent(context: android.content.Context): List<DeveloperNoteContent> {
        initializeContent(context)
        return contentVariations?.toList() ?: emptyList()
    }
    
    /**
     * Gets all available content variations (legacy method for backward compatibility).
     * This method is deprecated - use getAllContent(context) instead for proper string resource loading.
     * 
     * @return List of all DeveloperNoteContent variations using fallback content
     */
    @Deprecated("Use getAllContent(context) instead for proper string resource loading")
    fun getAllContent(): List<DeveloperNoteContent> {
        // Return fallback content for tests
        return listOf(
            DeveloperNoteContent(
                version = 0,
                title = "Hi, I'm Caleb",
                message = "I built CameraClick as a side project, and it's grown into something that helps people every day. I'd love to keep working on meaningful projects like this full-time—if you know of opportunities, please visit my website.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            ),
            DeveloperNoteContent(
                version = 1,
                title = "Hi, I'm Caleb",
                message = "Thanks for continuing to use CameraClick! I'm still actively seeking new opportunities to build impactful software. If you're hiring or know someone who is, I'd appreciate you checking out my work.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            ),
            DeveloperNoteContent(
                version = 2,
                title = "Hi, I'm Caleb",
                message = "It's amazing to see how CameraClick has become useful to so many people. Building tools that genuinely help others is what drives me as a developer. I'm always open to discussing new projects and opportunities.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            ),
            DeveloperNoteContent(
                version = 3,
                title = "Hi, I'm Caleb",
                message = "CameraClick started as a simple idea and has grown beyond what I imagined. I believe in the power of persistent, thoughtful development. If you're looking for someone who builds with purpose, let's connect.",
                websiteButtonText = "Visit My Website",
                emailButtonText = "Email"
            )
        )
    }

    /**
     * Gets the total number of content variations available.
     * 
     * @return The count of available content variations (always 4)
     */
    fun getContentCount(): Int {
        return 4 // We always have 4 versions
    }

    /**
     * Checks if a version number is valid.
     * 
     * @param version The version number to validate
     * @return True if the version is valid (0-3), false otherwise
     */
    fun isValidVersion(version: Int): Boolean {
        return version in 0..3
    }
}
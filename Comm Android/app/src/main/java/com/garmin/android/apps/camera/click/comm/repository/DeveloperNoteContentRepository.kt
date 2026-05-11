package com.garmin.android.apps.camera.click.comm.repository

import android.content.Context
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.DeveloperNoteContent

object DeveloperNoteContentRepository {

    fun getContent(context: Context): DeveloperNoteContent {
        return DeveloperNoteContent(
            version = 0,
            title = context.getString(R.string.developer_note_title),
            message = context.getString(R.string.developer_note_message_v1),
            websiteButtonText = context.getString(R.string.developer_note_website_button),
            emailButtonText = context.getString(R.string.developer_note_email_button)
        )
    }

    fun getContentForVersion(context: Context, version: Int): DeveloperNoteContent? {
        return if (version == 0) getContent(context) else null
    }
}

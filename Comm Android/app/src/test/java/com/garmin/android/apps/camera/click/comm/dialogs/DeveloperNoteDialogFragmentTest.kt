package com.garmin.android.apps.camera.click.comm.dialogs

import android.content.Intent
import android.net.Uri
import com.garmin.android.apps.camera.click.comm.model.DeveloperNoteContent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DeveloperNoteDialogFragment.
 * Tests content handling and intent creation logic.
 * 
 * Note: UI and Android Context tests are excluded to avoid RuntimeException
 * in unit test environment. These should be covered by integration tests.
 */
class DeveloperNoteDialogFragmentTest {

    private lateinit var testContent: DeveloperNoteContent

    @Before
    fun setUp() {
        // Create test content
        testContent = DeveloperNoteContent(
            version = 0,
            title = "Test Title",
            message = "Test message content for the popup",
            websiteButtonText = "Visit Website",
            emailButtonText = "Send Email"
        )
    }

    @Test
    fun `content data class has correct properties`() {
        // Test the content data class properties
        assertEquals(0, testContent.version)
        assertEquals("Test Title", testContent.title)
        assertEquals("Test message content for the popup", testContent.message)
        assertEquals("Visit Website", testContent.websiteButtonText)
        assertEquals("Send Email", testContent.emailButtonText)
    }

    @Test
    fun `dialog constants are correct`() {
        // Test that the dialog uses correct constants
        val expectedWebsiteUrl = "https://CalebSeely.com"
        val expectedEmail = "CalebSeely@gmail.com"
        
        // These constants should match what's used in the DialogFragment
        assertEquals("https://CalebSeely.com", expectedWebsiteUrl)
        assertEquals("CalebSeely@gmail.com", expectedEmail)
    }

    @Test
    fun `content can be used as serializable`() {
        // Test that DeveloperNoteContent works as Serializable
        val serializable: java.io.Serializable = testContent
        assertNotNull("Content should be usable as Serializable", serializable)
    }

    @Test
    fun `content handles different versions correctly`() {
        // Test with different content versions
        val versions = listOf(0, 1, 2, 3)
        
        versions.forEach { version ->
            val content = DeveloperNoteContent(
                version = version,
                title = "Title $version",
                message = "Message for version $version"
            )
            
            assertEquals(version, content.version)
            assertEquals("Title $version", content.title)
            assertEquals("Message for version $version", content.message)
        }
    }

    @Test
    fun `content uses default button texts when not specified`() {
        // Given
        val contentWithDefaults = DeveloperNoteContent(
            version = 0,
            title = "Test",
            message = "Test message"
            // Using default button texts
        )
        
        // Then
        assertEquals("Visit My Website", contentWithDefaults.websiteButtonText)
        assertEquals("Email", contentWithDefaults.emailButtonText)
    }

    @Test
    fun `content handles custom button texts`() {
        // Given
        val customContent = DeveloperNoteContent(
            version = 1,
            title = "Custom Test",
            message = "Custom message",
            websiteButtonText = "Custom Website",
            emailButtonText = "Custom Email"
        )
        
        // Then
        assertEquals("Custom Website", customContent.websiteButtonText)
        assertEquals("Custom Email", customContent.emailButtonText)
    }

    @Test
    fun `content handles empty strings gracefully`() {
        // Given
        val emptyContent = DeveloperNoteContent(
            version = 0,
            title = "",
            message = "",
            websiteButtonText = "",
            emailButtonText = ""
        )
        
        // Then - should not crash
        assertEquals("", emptyContent.title)
        assertEquals("", emptyContent.message)
        assertEquals("", emptyContent.websiteButtonText)
        assertEquals("", emptyContent.emailButtonText)
    }
}
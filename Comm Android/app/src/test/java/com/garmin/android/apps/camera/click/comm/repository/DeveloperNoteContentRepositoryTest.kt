package com.garmin.android.apps.camera.click.comm.repository

import com.garmin.android.apps.camera.click.comm.model.DeveloperNoteContent
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for DeveloperNoteContentRepository.
 * Verifies that all content variations are correctly stored and retrieved.
 */
class DeveloperNoteContentRepositoryTest {

    @Test
    fun `getContentForVersion returns correct content for version 0`() {
        val content = DeveloperNoteContentRepository.getContentForVersion(0)
        
        assertNotNull("Content for version 0 should not be null", content)
        assertEquals("Version should be 0", 0, content!!.version)
        assertEquals("Title should be correct", "Hi, I'm Caleb", content.title)
        assertTrue("Message should contain project growth focus", 
            content.message.contains("side project") && content.message.contains("grown"))
        assertEquals("Website button text should be default", "Visit My Website", content.websiteButtonText)
        assertEquals("Email button text should be default", "Email", content.emailButtonText)
    }

    @Test
    fun `getContentForVersion returns correct content for version 1`() {
        val content = DeveloperNoteContentRepository.getContentForVersion(1)
        
        assertNotNull("Content for version 1 should not be null", content)
        assertEquals("Version should be 1", 1, content!!.version)
        assertEquals("Title should be correct", "Hi, I'm Caleb", content.title)
        assertTrue("Message should contain opportunity seeking focus", 
            content.message.contains("seeking") && content.message.contains("opportunities"))
        assertEquals("Website button text should be default", "Visit My Website", content.websiteButtonText)
        assertEquals("Email button text should be default", "Email", content.emailButtonText)
    }

    @Test
    fun `getContentForVersion returns correct content for version 2`() {
        val content = DeveloperNoteContentRepository.getContentForVersion(2)
        
        assertNotNull("Content for version 2 should not be null", content)
        assertEquals("Version should be 2", 2, content!!.version)
        assertEquals("Title should be correct", "Hi, I'm Caleb", content.title)
        assertTrue("Message should contain community and usefulness focus", 
            content.message.contains("useful") && content.message.contains("help"))
        assertEquals("Website button text should be default", "Visit My Website", content.websiteButtonText)
        assertEquals("Email button text should be default", "Email", content.emailButtonText)
    }

    @Test
    fun `getContentForVersion returns correct content for version 3`() {
        val content = DeveloperNoteContentRepository.getContentForVersion(3)
        
        assertNotNull("Content for version 3 should not be null", content)
        assertEquals("Version should be 3", 3, content!!.version)
        assertEquals("Title should be correct", "Hi, I'm Caleb", content.title)
        assertTrue("Message should contain persistence and scale focus", 
            content.message.contains("persistent") && content.message.contains("purpose"))
        assertEquals("Website button text should be default", "Visit My Website", content.websiteButtonText)
        assertEquals("Email button text should be default", "Email", content.emailButtonText)
    }

    @Test
    fun `getContentForVersion returns null for invalid versions`() {
        assertNull("Version -1 should return null", DeveloperNoteContentRepository.getContentForVersion(-1))
        assertNull("Version 4 should return null", DeveloperNoteContentRepository.getContentForVersion(4))
        assertNull("Version 100 should return null", DeveloperNoteContentRepository.getContentForVersion(100))
    }

    @Test
    fun `getAllContent returns all 4 variations`() {
        val allContent = DeveloperNoteContentRepository.getAllContent()
        
        assertEquals("Should have exactly 4 content variations", 4, allContent.size)
        
        // Verify all versions are present
        val versions = allContent.map { it.version }.sorted()
        assertEquals("Should have versions 0, 1, 2, 3", listOf(0, 1, 2, 3), versions)
        
        // Verify all have the same title
        allContent.forEach { content ->
            assertEquals("All content should have same title", "Hi, I'm Caleb", content.title)
        }
        
        // Verify all have default button text
        allContent.forEach { content ->
            assertEquals("All content should have default website button text", "Visit My Website", content.websiteButtonText)
            assertEquals("All content should have default email button text", "Email", content.emailButtonText)
        }
    }

    @Test
    fun `getContentCount returns correct count`() {
        assertEquals("Content count should be 4", 4, DeveloperNoteContentRepository.getContentCount())
    }

    @Test
    fun `isValidVersion returns correct validation`() {
        assertTrue("Version 0 should be valid", DeveloperNoteContentRepository.isValidVersion(0))
        assertTrue("Version 1 should be valid", DeveloperNoteContentRepository.isValidVersion(1))
        assertTrue("Version 2 should be valid", DeveloperNoteContentRepository.isValidVersion(2))
        assertTrue("Version 3 should be valid", DeveloperNoteContentRepository.isValidVersion(3))
        
        assertFalse("Version -1 should be invalid", DeveloperNoteContentRepository.isValidVersion(-1))
        assertFalse("Version 4 should be invalid", DeveloperNoteContentRepository.isValidVersion(4))
        assertFalse("Version 100 should be invalid", DeveloperNoteContentRepository.isValidVersion(100))
    }

    @Test
    fun `all content has non-empty strings`() {
        val allContent = DeveloperNoteContentRepository.getAllContent()
        
        allContent.forEach { content ->
            assertFalse("Title should not be empty for version ${content.version}", content.title.isBlank())
            assertFalse("Message should not be empty for version ${content.version}", content.message.isBlank())
            assertFalse("Website button text should not be empty for version ${content.version}", content.websiteButtonText.isBlank())
            assertFalse("Email button text should not be empty for version ${content.version}", content.emailButtonText.isBlank())
        }
    }

    @Test
    fun `all messages are unique`() {
        val allContent = DeveloperNoteContentRepository.getAllContent()
        val messages = allContent.map { it.message }
        val uniqueMessages = messages.toSet()
        
        assertEquals("All messages should be unique", messages.size, uniqueMessages.size)
    }

    @Test
    fun `content variations follow expected themes`() {
        val version0 = DeveloperNoteContentRepository.getContentForVersion(0)!!
        val version1 = DeveloperNoteContentRepository.getContentForVersion(1)!!
        val version2 = DeveloperNoteContentRepository.getContentForVersion(2)!!
        val version3 = DeveloperNoteContentRepository.getContentForVersion(3)!!
        
        // Version 0: Introduction and project growth
        assertTrue("Version 0 should mention building the project", 
            version0.message.contains("built") || version0.message.contains("project"))
        
        // Version 1: Continued opportunity seeking
        assertTrue("Version 1 should mention seeking opportunities", 
            version1.message.contains("seeking") || version1.message.contains("opportunities"))
        
        // Version 2: Community and usefulness
        assertTrue("Version 2 should mention usefulness or helping", 
            version2.message.contains("useful") || version2.message.contains("help"))
        
        // Version 3: Personal persistence and scale
        assertTrue("Version 3 should mention persistence or purpose", 
            version3.message.contains("persistent") || version3.message.contains("purpose"))
    }
}
package com.garmin.android.apps.camera.click.comm.utils

import android.graphics.Rect
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AccessibilityUtils functionality
 * Note: These are simplified tests that focus on the data model logic
 */
class AccessibilityUtilsTest {

    @Test
    fun testShutterButtonInfo_DataIntegrity() {
        val packageName = "com.android.camera"
        val buttonInfo = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 200),
            packageName = packageName,
            contentDescription = "Shutter button",
            resourceId = "shutter_button",
            confidenceScore = 95
        )

        // Test basic data integrity
        assertEquals("Package name should match", packageName, buttonInfo.packageName)
        assertEquals("Content description should match", "Shutter button", buttonInfo.contentDescription)
        assertEquals("Resource ID should match", "shutter_button", buttonInfo.resourceId)
        assertEquals("Confidence score should match", 95, buttonInfo.confidenceScore)
        
        // Test bounds
        assertEquals("Left bound should be 100", 100, buttonInfo.bounds.left)
        assertEquals("Top bound should be 100", 100, buttonInfo.bounds.top)
        assertEquals("Right bound should be 200", 200, buttonInfo.bounds.right)
        assertEquals("Bottom bound should be 200", 200, buttonInfo.bounds.bottom)
    }

    @Test
    fun testShutterButtonInfo_InvalidBounds() {
        val buttonInfo = ShutterButtonInfo(
            bounds = Rect(0, 0, 0, 0), // Invalid empty bounds
            packageName = "com.android.camera"
        )
        
        assertTrue("Empty bounds should be detected as invalid", buttonInfo.bounds.isEmpty)
    }

    @Test
    fun testShutterButtonInfo_NoExpiry() {
        // Create button info with old timestamp (should still be valid for user preferences)
        val oldTimestamp = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days ago
        val buttonInfo = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 200),
            packageName = "com.android.camera",
            timestamp = oldTimestamp
        )

        // For user-preferred buttons, age shouldn't matter
        assertEquals("Timestamp should be preserved", oldTimestamp, buttonInfo.timestamp)
        
        // Test that isValid method exists and works for automatic detection (with age limit)
        assertFalse("Old timestamp should be invalid for automatic detection", 
            buttonInfo.isValid(7 * 24 * 60 * 60 * 1000)) // 7 days
        
        // But for user preferences, we don't use age validation
        assertTrue("Old timestamp should still be valid for user preferences", 
            buttonInfo.isValid(Long.MAX_VALUE)) // No age limit
    }

    @Test
    fun testShutterButtonInfo_PackageMatching() {
        val buttonInfo = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 200),
            packageName = "com.android.camera"
        )
        
        assertTrue("Should match same package", buttonInfo.matchesPackage("com.android.camera"))
        assertFalse("Should not match different package", buttonInfo.matchesPackage("com.google.camera"))
    }

    @Test
    fun testShutterButtonInfo_PositionCalculation() {
        val buttonInfo = ShutterButtonInfo(
            bounds = Rect(500, 1500, 600, 1600), // Bottom center area
            packageName = "com.android.camera"
        )
        
        val position = buttonInfo.calculatePositionOnScreen(1080, 1920)
        assertEquals("Should be bottom center", "bottom_center", position)
        
        // Test other positions
        val topLeft = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 200),
            packageName = "com.android.camera"
        )
        assertEquals("Should be top left", "top_left", topLeft.calculatePositionOnScreen(1080, 1920))
    }

    @Test
    fun testShutterButtonInfo_ButtonSizeCalculation() {
        val buttonInfo = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 250),
            packageName = "com.android.camera"
        )
        
        assertEquals("Button size should be calculated correctly", "100x150", buttonInfo.calculateButtonSize())
        assertFalse("Button should not be square", buttonInfo.calculateIsSquare())
        
        val squareButton = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 200),
            packageName = "com.android.camera"
        )
        
        assertEquals("Square button size", "100x100", squareButton.calculateButtonSize())
        assertTrue("Button should be square", squareButton.calculateIsSquare())
    }
}
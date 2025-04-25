package com.garmin.android.apps.connectiq.sample.comm

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class CameraAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "CameraAccessibility"
        private const val DEBUG = true // Temporarily hardcoded for testing
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (DEBUG) {
            Log.d(TAG, "Accessibility service connected")
            Toast.makeText(this, "Accessibility service connected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!DEBUG) return

        val packageName = event.packageName?.toString() ?: "unknown"
        val eventType = getEventTypeString(event.eventType)
        val className = event.className?.toString() ?: "unknown"
        val source = event.source?.toString() ?: "unknown"

        Log.d(TAG, """
            Package: $packageName
            Event Type: $eventType
            Class: $className
            Source: $source
            Event Time: ${event.eventTime}
            Window ID: ${event.windowId}
        """.trimIndent())
    }

    override fun onInterrupt() {
        if (DEBUG) {
            Log.d(TAG, "Accessibility service interrupted")
        }
    }

    private fun getEventTypeString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
//            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
//            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
//            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
//            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "TYPE_NOTIFICATION_STATE_CHANGED"
//            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> "TYPE_TOUCH_EXPLORATION_GESTURE_START"
//            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> "TYPE_TOUCH_EXPLORATION_GESTURE_END"
//            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
//            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> "TYPE_VIEW_HOVER_ENTER"
//            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> "TYPE_VIEW_HOVER_EXIT"
//            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TYPE_VIEW_TEXT_SELECTION_CHANGED"
            else -> "UNKNOWN_TYPE($eventType)"
        }
    }
} 
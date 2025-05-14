package com.garmin.android.apps.camera.click.comm.utils

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility class for accessibility-related functions.
 * This class provides helper methods for working with AccessibilityNodeInfo objects.
 */
object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"

    /**
     * Finds and logs details about the largest clickable node on the screen.
     * This is useful for debugging purposes to understand the current screen state.
     * 
     * @param rootNode The root AccessibilityNodeInfo to start traversal from
     * @param tag Optional tag for logging. If not provided, uses the default TAG
     */
    fun largestClickableNode(rootNode: AccessibilityNodeInfo?, tag: String = TAG): AccessibilityNodeInfo? {
        if (rootNode == null) {
            Log.d(tag, "No root node available")
            return null
        }

        var largestNode: AccessibilityNodeInfo? = null
        var largestArea = 0

        // Recursive function to traverse the node tree
        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()
                
                if (area > largestArea) {
                    largestArea = area
                    largestNode = node
                }
            }

            // Recursively check child nodes
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }

        // Start traversal
        traverse(rootNode)

        if (largestNode != null) {
            val bounds = android.graphics.Rect()
            largestNode?.getBoundsInScreen(bounds)
            
            Log.d(tag, """
                Largest clickable node found:
                contentDescription: ${largestNode?.contentDescription}
                resource-id: ${largestNode?.viewIdResourceName}
                className: ${largestNode?.className}
                text: ${largestNode?.text}
                boundsInScreen: $bounds
                isClickable: ${largestNode?.isClickable}
            """.trimIndent())
        } else {
            Log.d(tag, "No clickable nodes found on screen")
        }

        return largestNode
    }
} 
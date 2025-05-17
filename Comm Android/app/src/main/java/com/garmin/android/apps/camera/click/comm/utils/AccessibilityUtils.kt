package com.garmin.android.apps.camera.click.comm.utils

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

/**
 * AccessibilityUtils.kt
 * Utility class for accessibility-related functions.
 * This class provides helper methods for working with AccessibilityNodeInfo objects.
 */
object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"
    
    // Store the last known shutter button location for each camera app
    private var lastKnownButtonInfo: ShutterButtonInfo? = null

    /**
     * Returns the last known shutter button info for the given package
     * @param packageName The package name of the camera app
     * @return ShutterButtonInfo if found for the package, null otherwise
     */
    fun getLastKnownButtonInfo(packageName: String): ShutterButtonInfo? {
        return lastKnownButtonInfo?.takeIf { it.packageName == packageName }
    }

    /**
     * Finds a clickable node at the specified location in the accessibility tree.
     * 
     * @param root The root AccessibilityNodeInfo to start traversal from
     * @param bounds The bounds to search within
     * @return The found AccessibilityNodeInfo or null if not found
     */
    fun findClickableNodeAtLocation(root: AccessibilityNodeInfo, bounds: Rect): AccessibilityNodeInfo? {
        val result = ArrayList<AccessibilityNodeInfo>()
        
        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)
                if (nodeBounds == bounds) {
                    result.add(node)
                    return
                }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                if (result.isNotEmpty()) {
                    child.recycle()
                    break
                }
                child.recycle()
            }
        }
        
        traverse(root)
        return result.firstOrNull()
    }

    /**
     * Finds and logs details about the largest clickable node on the screen.
     * This is useful for debugging purposes to understand the current screen state.
     * 
     * @param rootNode The root AccessibilityNodeInfo to start traversal from
     * @param packageName The package name of the current camera app
     * @param tag Optional tag for logging. If not provided, uses the default TAG
     * @return The largest clickable node found, or null if none exists
     */
    fun largestClickableNode(rootNode: AccessibilityNodeInfo?, packageName: String, tag: String = TAG): AccessibilityNodeInfo? {
        if (rootNode == null) {
            Log.d(tag, "No root node available")
            return null
        }

        var largestNode: AccessibilityNodeInfo? = null
        var largestArea = 0

        // Recursive function to traverse the node tree
        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val bounds = Rect()
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
            val bounds = Rect()
            largestNode?.getBoundsInScreen(bounds)
            
            // Save the button location for this package
            lastKnownButtonInfo = ShutterButtonInfo(bounds, packageName)
            
            Log.d(tag, """
                Largest clickable node found:
                contentDescription: ${largestNode?.contentDescription}
                resource-id: ${largestNode?.viewIdResourceName}
                className: ${largestNode?.className}
                text: ${largestNode?.text}
                boundsInScreen: $bounds
                isClickable: ${largestNode?.isClickable}
                Saved button location for package: $packageName
            """.trimIndent())
        } else {
            Log.d(tag, "No clickable nodes found on screen")
        }

        return largestNode
    }
} 
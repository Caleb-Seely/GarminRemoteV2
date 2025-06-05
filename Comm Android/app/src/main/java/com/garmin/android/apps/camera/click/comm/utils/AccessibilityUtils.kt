package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.google.gson.Gson

/**
 * AccessibilityUtils.kt
 * Utility class for accessibility-related functions.
 * This class provides helper methods for working with AccessibilityNodeInfo objects.
 */
object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"
    private const val PREFS_NAME = "AccessibilityPrefs"
    private const val KEY_BUTTON_INFO = "last_known_button_info"
    
    // Store the last known shutter button location
    private var lastKnownButtonInfo: ShutterButtonInfo? = null
    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    /**
     * Initialize the SharedPreferences
     * @param context Application context
     */
    fun initialize(context: Context) {
        try {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadLastKnownButtonInfo()
            Log.d(TAG, "Successfully initialized SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SharedPreferences", e)
        }
    }

    /**
     * Check if SharedPreferences is initialized
     * @return true if initialized, false otherwise
     */
    private fun isInitialized(): Boolean {
        return prefs != null
    }

    /**
     * Load the last known button info from SharedPreferences
     */
    private fun loadLastKnownButtonInfo() {
        if (!isInitialized()) {
            Log.w(TAG, "Cannot load button info: SharedPreferences not initialized")
            return
        }

        val json = prefs?.getString(KEY_BUTTON_INFO, null)
        if (json != null) {
            try {
                lastKnownButtonInfo = gson.fromJson(json, ShutterButtonInfo::class.java)
                Log.d(TAG, "Successfully loaded button info from SharedPreferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading button info from SharedPreferences", e)
            }
        }
    }

    /**
     * Save the last known button info to SharedPreferences
     */
    private fun saveLastKnownButtonInfo() {
        if (!isInitialized()) {
            Log.w(TAG, "Cannot save button info: SharedPreferences not initialized")
            return
        }

        lastKnownButtonInfo?.let { info ->
            try {
                val json = gson.toJson(info)
                prefs?.edit()?.putString(KEY_BUTTON_INFO, json)?.apply()
                Log.d(TAG, "Successfully saved button info to SharedPreferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving button info to SharedPreferences", e)
            }
        }
    }

    /**
     * Returns the last known shutter button info
     * @return ShutterButtonInfo if found, null otherwise
     */
    fun getLastKnownButtonInfo(): ShutterButtonInfo? {
        if (!isInitialized()) {
            Log.w(TAG, "Cannot get button info: SharedPreferences not initialized")
        }
        return lastKnownButtonInfo
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
            
            // Save the button location and information
            lastKnownButtonInfo = ShutterButtonInfo(
                bounds = bounds,
                packageName = packageName,
                contentDescription = largestNode?.contentDescription?.toString(),
                resourceId = largestNode?.viewIdResourceName,
                className = largestNode?.className?.toString(),
                text = largestNode?.text?.toString()
            )
            
            // Save to SharedPreferences
            saveLastKnownButtonInfo()
            
            Log.d(tag, """
                Largest clickable node found:
                contentDescription: ${largestNode?.contentDescription}
                resource-id: ${largestNode?.viewIdResourceName}
                className: ${largestNode?.className}
                text: ${largestNode?.text}
                boundsInScreen: $bounds
                isClickable: ${largestNode?.isClickable}
                Saved button location and information
            """.trimIndent())
        } else {
            Log.d(tag, "No clickable nodes found on screen")
        }

        return largestNode
    }
} 
package com.garmin.android.apps.camera.click.comm.detection

import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NodeTraverser - Centralized tree traversal utility for AccessibilityNodeInfo
 *
 * This class provides generic tree traversal methods to eliminate code duplication
 * across different detection strategies. All traversal logic is consolidated here.
 *
 * @see AccessibilityNodeInfo for the node tree structure
 */
@Singleton
class NodeTraverser @Inject constructor() {

    /**
     * Traverse the entire accessibility node tree and apply a visitor function to each node
     *
     * @param root The root node to start traversal from
     * @param visitor Function to apply to each node during traversal
     */
    fun traverse(root: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Unit) {
        visitor(root)

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            traverse(child, visitor)
            // Node recycling is handled automatically by the system
        }
    }

    /**
     * Find the first node that matches the given predicate
     *
     * @param root The root node to start searching from
     * @param predicate Function that returns true for the desired node
     * @return The first matching node, or null if not found
     */
    fun findFirst(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirst(child, predicate)
            if (found != null) {
                return found
            }
        }

        return null
    }

    /**
     * Find all nodes that match the given predicate
     *
     * @param root The root node to start searching from
     * @param predicate Function that returns true for desired nodes
     * @return List of all matching nodes (may be empty)
     */
    fun findAll(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        fun traverseAndCollect(node: AccessibilityNodeInfo) {
            if (predicate(node)) {
                results.add(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverseAndCollect(child)
            }
        }

        traverseAndCollect(root)
        return results
    }

    /**
     * Count the number of nodes that match the given predicate
     *
     * @param root The root node to start counting from
     * @param predicate Function that returns true for nodes to count
     * @return The count of matching nodes
     */
    fun countNodes(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Int {
        var count = 0

        fun traverseAndCount(node: AccessibilityNodeInfo) {
            if (predicate(node)) {
                count++
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverseAndCount(child)
            }
        }

        traverseAndCount(root)
        return count
    }

    /**
     * Count all clickable nodes in the tree
     * Convenience method for common use case
     *
     * @param root The root node to start counting from
     * @return The count of clickable nodes
     */
    fun countClickableNodes(root: AccessibilityNodeInfo): Int {
        return countNodes(root) { it.isClickable }
    }
}

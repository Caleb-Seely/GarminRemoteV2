package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.garmin.android.apps.camera.click.comm.utils.CameraDetectionUtils

/**
 * AccessibilityUtils.kt
 * Utility class for accessibility-related functions.
 * This class provides helper methods for working with AccessibilityNodeInfo objects.
 */
object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"
    private const val PREFS_NAME = "AccessibilityPrefs"
    private const val KEY_BUTTON_INFO = "last_known_button_info"
    private const val KEY_DETECTION_STATS = "detection_stats"
    private const val KEY_CANDIDATE_LISTS = "candidate_lists_by_app"
    private const val KEY_USER_PREFERRED_BUTTONS = "user_preferred_buttons"
    
    // Store the last known shutter button location
    private var lastKnownButtonInfo: ShutterButtonInfo? = null
    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    // Camera app specific detection patterns
    private val cameraAppPatterns = mapOf(
        "com.google.android.GoogleCamera" to CameraAppPattern(
            resourceIds = listOf("com.google.android.GoogleCamera:id/shutter_button", "shutter_button"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        ),
        "com.android.camera" to CameraAppPattern(
            resourceIds = listOf("com.android.camera:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        ),
        "com.sec.android.app.camera" to CameraAppPattern( // Samsung
            resourceIds = listOf("com.sec.android.app.camera:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        ),
        "com.oplus.camera" to CameraAppPattern( // OnePlus
            resourceIds = listOf("com.oplus.camera:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageView", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        ),
        "com.motorola.camera3" to CameraAppPattern( // Motorola
            resourceIds = listOf("com.motorola.camera3:id/shutter_button", "shutter_button", "btn_camera_capture"),
            contentDescriptions = listOf("Shutter", "Take photo", "Camera shutter"),
            classNames = listOf("android.widget.ImageButton", "android.widget.Button"),
            positions = listOf("bottom_center", "center_bottom")
        )
    )

    /**
     * Data class representing detection patterns for specific camera apps
     */
    data class CameraAppPattern(
        val resourceIds: List<String>,
        val contentDescriptions: List<String>,
        val classNames: List<String>,
        val positions: List<String>
    )

    /**
     * Detection statistics for learning and improvement
     */
    data class DetectionStats(
        val packageName: String,
        val successfulMethods: MutableMap<String, Int> = mutableMapOf(),
        val failedAttempts: MutableMap<String, Int> = mutableMapOf(),
        val lastSuccessfulMethod: String? = null
    )

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
     * Enhanced shutter button detection with multiple strategies
     * @param root The root AccessibilityNodeInfo to start traversal from
     * @param packageName The package name of the current camera app
     * @param tag Optional tag for logging
     * @param toastCallback Optional callback for displaying toast messages
     * @return The found AccessibilityNodeInfo or null if not found
     */
    fun findShutterButtonEnhanced(
        root: AccessibilityNodeInfo,
        packageName: String,
        tag: String = TAG,
        toastCallback: ((String) -> Unit)? = null
    ): AccessibilityNodeInfo? {
        val startTime = System.currentTimeMillis()
        val strategiesTried = mutableListOf<String>()
        var successfulMethod: String? = null
        var finalButtonInfo: ShutterButtonInfo? = null
        
        Log.d(tag, "Enhanced shutter button detection for package: $packageName")
        toastCallback?.invoke("Detecting shutter button for $packageName…")
        
        // Log camera app characteristics
        val totalClickableNodes = countClickableNodes(root)
        AnalyticsUtils.logCameraAppCharacteristics(
            packageName = packageName,
            isKnownCameraApp = CameraDetectionUtils.isKnownCameraApp(packageName),
            totalClickableNodes = totalClickableNodes,
            screenWidth = getScreenWidth(),
            screenHeight = getScreenHeight(),
            screenOrientation = getScreenOrientation()
        )
        
        // Validate that this is likely a camera app
        if (!CameraDetectionUtils.isKnownCameraApp(packageName)) {
            Log.d(tag, "Package $packageName is not a known camera app, but continuing with detection")
            toastCallback?.invoke("Warning: Unknown camera app, continuing with detection")
        }
        
        // Strategy 1: Try package-specific detection first
        strategiesTried.add("package_specific")
        toastCallback?.invoke("Trying package-specific detection…")
        val packageSpecificStartTime = System.currentTimeMillis()
        val packageSpecificButton = findPackageSpecificButton(root, packageName, tag)
        val packageSpecificTime = System.currentTimeMillis() - packageSpecificStartTime
        
        if (packageSpecificButton != null) {
            val validation = CameraDetectionUtils.validateShutterButton(packageSpecificButton, packageName)
            AnalyticsUtils.logButtonValidation(packageName, packageSpecificButton, validation, "package_specific")
            
            if (validation.isValid) {
                Log.d(tag, "Found button using package-specific detection (score: ${validation.score})")
                toastCallback?.invoke("Found button using package-specific detection!")
                successfulMethod = "package_specific"
                finalButtonInfo = saveButtonInfo(packageSpecificButton, packageName, "package_specific")
                
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "package_specific",
                    success = true,
                    detectionTimeMs = packageSpecificTime,
                    candidatesFound = 1,
                    bestCandidateScore = validation.score
                )
                
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = "package_specific",
                    success = true,
                    buttonInfo = packageSpecificButton,
                    validationScore = validation.score,
                    detectionTimeMs = packageSpecificTime
                )
                
                logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
                return packageSpecificButton
            } else {
                Log.d(tag, "Package-specific button failed validation (score: ${validation.score})")
                toastCallback?.invoke("Package-specific button failed validation, trying next method…")
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "package_specific",
                    success = false,
                    detectionTimeMs = packageSpecificTime,
                    candidatesFound = 0
                )
            }
        }

        // Strategy 2: Try saved location if available
        strategiesTried.add("saved_location")
        toastCallback?.invoke("Trying saved location…")
        val savedButton = trySavedLocation(root, tag)
        if (savedButton != null) {
            val validation = CameraDetectionUtils.validateShutterButton(savedButton, packageName)
            AnalyticsUtils.logButtonValidation(packageName, savedButton, validation, "saved_location")
            
            if (validation.isValid) {
                Log.d(tag, "Found button using saved location (score: ${validation.score})")
                toastCallback?.invoke("Found button using saved location!")
                successfulMethod = "saved_location"
                
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = "saved_location",
                    success = true,
                    buttonInfo = savedButton,
                    validationScore = validation.score
                )
                
                logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
                return savedButton
            } else {
                Log.d(tag, "Saved location button failed validation (score: ${validation.score})")
                toastCallback?.invoke("Saved location failed validation, trying next method…")
            }
        }

        // Strategy 3: Try content description matching
        strategiesTried.add("content_description")
        toastCallback?.invoke("Trying content description matching…")
        val contentDescStartTime = System.currentTimeMillis()
        val contentDescButton = findButtonByContentDescription(root, tag)
        val contentDescTime = System.currentTimeMillis() - contentDescStartTime
        
        if (contentDescButton != null) {
            val validation = CameraDetectionUtils.validateShutterButton(contentDescButton, packageName)
            AnalyticsUtils.logButtonValidation(packageName, contentDescButton, validation, "content_description")
            
            if (validation.isValid) {
                Log.d(tag, "Found button using content description (score: ${validation.score})")
                toastCallback?.invoke("Found button using content description!")
                successfulMethod = "content_description"
                finalButtonInfo = saveButtonInfo(contentDescButton, packageName, "content_description")
                
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "content_description",
                    success = true,
                    detectionTimeMs = contentDescTime,
                    candidatesFound = 1,
                    bestCandidateScore = validation.score
                )
                
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = "content_description",
                    success = true,
                    buttonInfo = contentDescButton,
                    validationScore = validation.score,
                    detectionTimeMs = contentDescTime
                )
                
                logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
                return contentDescButton
            } else {
                Log.d(tag, "Content description button failed validation (score: ${validation.score})")
                toastCallback?.invoke("Content description failed validation, trying next method…")
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "content_description",
                    success = false,
                    detectionTimeMs = contentDescTime,
                    candidatesFound = 0
                )
            }
        }

        // Strategy 4: Try largest clickable node (fallback)
        strategiesTried.add("largest_node")
        toastCallback?.invoke("Trying largest clickable node…")
        val largestStartTime = System.currentTimeMillis()
        val largestButton = largestClickableNode(root, packageName, tag)
        val largestTime = System.currentTimeMillis() - largestStartTime
        
        if (largestButton != null) {
            val validation = CameraDetectionUtils.validateShutterButton(largestButton, packageName)
            AnalyticsUtils.logButtonValidation(packageName, largestButton, validation, "largest_node")
            
            if (validation.isValid) {
                Log.d(tag, "Found button using largest clickable node (score: ${validation.score})")
                toastCallback?.invoke("Found button using largest clickable node!")
                successfulMethod = "largest_node"
                finalButtonInfo = saveButtonInfo(largestButton, packageName, "largest_node")
                
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "largest_node",
                    success = true,
                    detectionTimeMs = largestTime,
                    candidatesFound = 1,
                    bestCandidateScore = validation.score
                )
                
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = "largest_node",
                    success = true,
                    buttonInfo = largestButton,
                    validationScore = validation.score,
                    detectionTimeMs = largestTime
                )
                
                logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
                return largestButton
            } else {
                Log.d(tag, "Largest node button failed validation (score: ${validation.score})")
                toastCallback?.invoke("Largest clickable node failed validation, trying next method…")
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "largest_node",
                    success = false,
                    detectionTimeMs = largestTime,
                    candidatesFound = 0
                )
            }
        }

        // Strategy 5: Try position-based detection
        strategiesTried.add("position_based")
        toastCallback?.invoke("Trying position-based detection…")
        val positionStartTime = System.currentTimeMillis()
        val positionButton = findButtonByPosition(root, tag)
        val positionTime = System.currentTimeMillis() - positionStartTime
        
        if (positionButton != null) {
            val validation = CameraDetectionUtils.validateShutterButton(positionButton, packageName)
            AnalyticsUtils.logButtonValidation(packageName, positionButton, validation, "position_based")
            
            if (validation.isValid) {
                Log.d(tag, "Found button using position-based detection (score: ${validation.score})")
                toastCallback?.invoke("Found button using position-based detection!")
                successfulMethod = "position_based"
                finalButtonInfo = saveButtonInfo(positionButton, packageName, "position_based")
                
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "position_based",
                    success = true,
                    detectionTimeMs = positionTime,
                    candidatesFound = 1,
                    bestCandidateScore = validation.score
                )
                
                AnalyticsUtils.logButtonDetectionAttempt(
                    packageName = packageName,
                    detectionMethod = "position_based",
                    success = true,
                    buttonInfo = positionButton,
                    validationScore = validation.score,
                    detectionTimeMs = positionTime
                )
                
                logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
                return positionButton
            } else {
                Log.d(tag, "Position-based button failed validation (score: ${validation.score})")
                toastCallback?.invoke("Position-based detection failed validation, trying next method…")
                AnalyticsUtils.logDetectionStrategyPerformance(
                    packageName = packageName,
                    strategyName = "position_based",
                    success = false,
                    detectionTimeMs = positionTime,
                    candidatesFound = 0
                )
            }
        }

        // Strategy 6: Try comprehensive search with validation
        strategiesTried.add("comprehensive_search")
        toastCallback?.invoke("Trying comprehensive search…")
        val comprehensiveStartTime = System.currentTimeMillis()
        val comprehensiveButton = findComprehensiveButton(root, packageName, tag)
        val comprehensiveTime = System.currentTimeMillis() - comprehensiveStartTime
        
        if (comprehensiveButton != null) {
            val validation = CameraDetectionUtils.validateShutterButton(comprehensiveButton, packageName)
            AnalyticsUtils.logButtonValidation(packageName, comprehensiveButton, validation, "comprehensive_search")
            
            Log.d(tag, "Found button using comprehensive search")
            toastCallback?.invoke("Found button using comprehensive search!")
            successfulMethod = "comprehensive_search"
            finalButtonInfo = saveButtonInfo(comprehensiveButton, packageName, "comprehensive_search")
            
            AnalyticsUtils.logDetectionStrategyPerformance(
                packageName = packageName,
                strategyName = "comprehensive_search",
                success = true,
                detectionTimeMs = comprehensiveTime,
                candidatesFound = 1,
                bestCandidateScore = validation.score
            )
            
            AnalyticsUtils.logButtonDetectionAttempt(
                packageName = packageName,
                detectionMethod = "comprehensive_search",
                success = true,
                buttonInfo = comprehensiveButton,
                validationScore = validation.score,
                detectionTimeMs = comprehensiveTime
            )
            
            logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
            return comprehensiveButton
        }

        // Log failure analysis
        Log.d(tag, "No shutter button found with any detection method")
        toastCallback?.invoke("No shutter button found with any detection method.")
        AnalyticsUtils.logDetectionFailure(
            packageName = packageName,
            strategiesTried = strategiesTried,
            failureReason = "all_strategies_failed",
            screenCharacteristics = "width:${getScreenWidth()},height:${getScreenHeight()},orientation:${getScreenOrientation()}"
        )
        
        logDetectionSessionSummary(packageName, successfulMethod, strategiesTried.size, System.currentTimeMillis() - startTime, strategiesTried, finalButtonInfo)
        return null
    }

    /**
     * Count total clickable nodes for analytics
     */
    private fun countClickableNodes(root: AccessibilityNodeInfo): Int {
        var count = 0
        
        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                count++
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }
        
        traverse(root)
        return count
    }

    /**
     * Log detection session summary
     */
    private fun logDetectionSessionSummary(
        packageName: String,
        successfulMethod: String?,
        totalAttempts: Int,
        totalTimeMs: Long,
        strategiesTried: List<String>,
        finalButtonInfo: ShutterButtonInfo?
    ) {
        AnalyticsUtils.logDetectionSessionSummary(
            packageName = packageName,
            successfulMethod = successfulMethod,
            totalAttempts = totalAttempts,
            totalTimeMs = totalTimeMs,
            strategiesTried = strategiesTried,
            finalButtonInfo = finalButtonInfo
        )
    }

    /**
     * Comprehensive button search that collects all candidates and picks the best one
     */
    private fun findComprehensiveButton(root: AccessibilityNodeInfo, packageName: String, tag: String): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                candidates.add(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }

        traverse(root)

        // Filter and rank candidates
        val validCandidates = candidates.mapNotNull { node ->
            val validation = CameraDetectionUtils.validateShutterButton(node, packageName)
            if (validation.isValid) {
                node to validation.score
            } else null
        }

        if (validCandidates.isNotEmpty()) {
            val bestCandidate = validCandidates.maxByOrNull { it.second }
            Log.d(tag, "Comprehensive search found ${validCandidates.size} valid candidates, best score: ${bestCandidate?.second}")
            return bestCandidate?.first
        }

        return null
    }

    /**
     * Find button using package-specific patterns
     */
    private fun findPackageSpecificButton(root: AccessibilityNodeInfo, packageName: String, tag: String): AccessibilityNodeInfo? {
        val pattern = cameraAppPatterns[packageName] ?: return null
        Log.d(tag, "Using package-specific pattern for $packageName")

        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val resourceId = node.viewIdResourceName
                val contentDesc = node.contentDescription?.toString()?.lowercase()
                val className = node.className?.toString()

                // Check resource ID match
                if (resourceId != null && pattern.resourceIds.any { resourceId.contains(it, ignoreCase = true) }) {
                    candidates.add(node)
                    Log.d(tag, "Found candidate by resource ID: $resourceId")
                }

                // Check content description match
                if (contentDesc != null && pattern.contentDescriptions.any { contentDesc.contains(it.lowercase()) }) {
                    candidates.add(node)
                    Log.d(tag, "Found candidate by content description: $contentDesc")
                }

                // Check class name match
                if (className != null && pattern.classNames.any { className.contains(it, ignoreCase = true) }) {
                    candidates.add(node)
                    Log.d(tag, "Found candidate by class name: $className")
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }

        traverse(root)

        // Return the best candidate (prefer larger, more central buttons)
        return candidates.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val area = bounds.width() * bounds.height()
            val centerY = bounds.centerY()
            val screenHeight = getScreenHeight()
            
            // Prefer buttons in the bottom half of the screen
            val positionScore = if (centerY > screenHeight / 2) 1000 else 0
            area + positionScore
        }
    }

    /**
     * Find button by content description patterns
     */
    private fun findButtonByContentDescription(root: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        val shutterKeywords = listOf("shutter", "take photo", "camera shutter", "capture", "shoot")
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val contentDesc = node.contentDescription?.toString()?.lowercase()
                if (contentDesc != null && shutterKeywords.any { contentDesc.contains(it) }) {
                    candidates.add(node)
                    Log.d(tag, "Found candidate by content description: $contentDesc")
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }

        traverse(root)
        return candidates.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            bounds.width() * bounds.height()
        }
    }

    /**
     * Find button by position (bottom center of screen)
     */
    private fun findButtonByPosition(root: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        val screenHeight = getScreenHeight()
        val bottomCenterY = screenHeight * 0.8f // 80% down the screen
        val tolerance = screenHeight * 0.1f // 10% tolerance

        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val centerY = bounds.centerY()

                if (centerY >= bottomCenterY - tolerance && centerY <= bottomCenterY + tolerance) {
                    candidates.add(node)
                    Log.d(tag, "Found candidate by position: centerY=$centerY")
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }

        traverse(root)
        return candidates.maxByOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            bounds.width() * bounds.height()
        }
    }

    /**
     * Try to find button using saved location
     */
    private fun trySavedLocation(root: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        val savedInfo = lastKnownButtonInfo ?: return null
        Log.d(tag, "Trying saved location: ${savedInfo.bounds}")
        
        return findClickableNodeAtLocation(root, savedInfo.bounds)
    }

    /**
     * Save button information for future use with enhanced metadata
     */
    private fun saveButtonInfo(button: AccessibilityNodeInfo, packageName: String, detectionMethod: String): ShutterButtonInfo {
        val bounds = Rect()
        button.getBoundsInScreen(bounds)
        
        // Calculate additional metadata
        val buttonSize = "${bounds.width()}x${bounds.height()}"
        val isSquare = bounds.width() == bounds.height()
        val screenHeight = getScreenHeight()
        val screenWidth = getScreenWidth()
        val positionOnScreen = calculatePositionOnScreen(bounds, screenWidth, screenHeight)
        
        // Calculate confidence score based on detection method
        val confidenceScore = when (detectionMethod) {
            "package_specific" -> 95
            "content_description" -> 85
            "saved_location" -> 80
            "position_based" -> 70
            "largest_node" -> 60
            else -> 50
        }
        
        val buttonInfo = ShutterButtonInfo(
            bounds = bounds,
            packageName = packageName,
            contentDescription = button.contentDescription?.toString(),
            resourceId = button.viewIdResourceName,
            className = button.className?.toString(),
            text = button.text?.toString(),
            detectionMethod = detectionMethod,
            confidenceScore = confidenceScore,
            timestamp = System.currentTimeMillis(),
            screenOrientation = getScreenOrientation(),
            buttonSize = buttonSize,
            isSquare = isSquare,
            positionOnScreen = positionOnScreen
        )
        
        lastKnownButtonInfo = buttonInfo
        saveLastKnownButtonInfo()
        updateDetectionStats(packageName, detectionMethod, true)
        
        Log.d(TAG, """
            Saved enhanced button info:
            Method: $detectionMethod
            Confidence: $confidenceScore%
            Size: $buttonSize
            Position: $positionOnScreen
            Square: $isSquare
            Resource ID: ${button.viewIdResourceName}
            Content Description: ${button.contentDescription}
        """.trimIndent())
        
        return buttonInfo
    }

    /**
     * Calculate the relative position of a button on screen
     */
    private fun calculatePositionOnScreen(bounds: Rect, screenWidth: Int, screenHeight: Int): String {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        
        val horizontalPosition = when {
            centerX < screenWidth * 0.33 -> "left"
            centerX > screenWidth * 0.67 -> "right"
            else -> "center"
        }
        
        val verticalPosition = when {
            centerY < screenHeight * 0.33 -> "top"
            centerY > screenHeight * 0.67 -> "bottom"
            else -> "middle"
        }
        
        return "${verticalPosition}_${horizontalPosition}"
    }

    /**
     * Get screen width (approximate)
     */
    private fun getScreenWidth(): Int {
        // This is a rough approximation - in a real app you'd get this from WindowManager
        return 1080 // Default to common screen width
    }

    /**
     * Get screen orientation
     */
    private fun getScreenOrientation(): Int {
        // This would typically come from Configuration or WindowManager
        // For now, default to portrait
        return 0
    }

    /**
     * Update detection statistics for learning
     */
    private fun updateDetectionStats(packageName: String, method: String, success: Boolean) {
        if (!isInitialized()) return

        val statsJson = prefs?.getString(KEY_DETECTION_STATS, "{}")
        val statsMap = try {
            gson.fromJson(statsJson, Map::class.java) as? Map<String, Any> ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing detection stats", e)
            emptyMap()
        }

        val packageStats = statsMap[packageName] as? Map<String, Any> ?: emptyMap()
        
        // Safely convert the maps with proper type handling
        val successfulMethods = mutableMapOf<String, Int>()
        val failedAttempts = mutableMapOf<String, Int>()
        
        // Convert successful methods
        val rawSuccessfulMethods = packageStats["successfulMethods"] as? Map<*, *>
        rawSuccessfulMethods?.forEach { (key, value) ->
            if (key is String) {
                val count = when (value) {
                    is Int -> value
                    is Double -> value.toInt()
                    is Number -> value.toInt()
                    else -> 0
                }
                successfulMethods[key] = count
            }
        }
        
        // Convert failed attempts
        val rawFailedAttempts = packageStats["failedAttempts"] as? Map<*, *>
        rawFailedAttempts?.forEach { (key, value) ->
            if (key is String) {
                val count = when (value) {
                    is Int -> value
                    is Double -> value.toInt()
                    is Number -> value.toInt()
                    else -> 0
                }
                failedAttempts[key] = count
            }
        }

        if (success) {
            successfulMethods[method] = (successfulMethods[method] ?: 0) + 1
        } else {
            failedAttempts[method] = (failedAttempts[method] ?: 0) + 1
        }

        val updatedStats = packageStats.toMutableMap().apply {
            put("successfulMethods", successfulMethods)
            put("failedAttempts", failedAttempts)
            if (success) put("lastSuccessfulMethod", method)
        }

        val updatedMap = statsMap.toMutableMap().apply {
            put(packageName, updatedStats)
        }

        try {
            prefs?.edit()?.putString(KEY_DETECTION_STATS, gson.toJson(updatedMap))?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving detection stats", e)
        }
    }

    /**
     * Get screen height (approximate)
     */
    private fun getScreenHeight(): Int {
        // This is a rough approximation - in a real app you'd get this from WindowManager
        return 1920 // Default to common screen height
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
     * Checks if a node's bounds form a square shape
     * @param node The AccessibilityNodeInfo to check
     * @return true if the node is square, false otherwise
     */
    private fun isSquareNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.width() == bounds.height()
    }

    /**
     * Finds and logs details about the largest square clickable node on the screen.
     * This is useful for debugging purposes to understand the current screen state.
     * 
     * @param rootNode The root AccessibilityNodeInfo to start traversal from
     * @param packageName The package name of the current camera app
     * @param tag Optional tag for logging. If not provided, uses the default TAG
     * @return The largest square clickable node found, or null if none exists
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
            if (node.isClickable && isSquareNode(node)) {
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
            
            // Calculate position on screen
            val screenWidth = getScreenWidth()
            val screenHeight = getScreenHeight()
            val positionOnScreen = calculatePositionOnScreen(bounds, screenWidth, screenHeight)
            
            // Save the button location and information
            lastKnownButtonInfo = ShutterButtonInfo(
                bounds = bounds,
                packageName = packageName,
                contentDescription = largestNode?.contentDescription?.toString(),
                resourceId = largestNode?.viewIdResourceName,
                className = largestNode?.className?.toString(),
                text = largestNode?.text?.toString(),
                positionOnScreen = positionOnScreen
            )
            
            // Save to SharedPreferences
            saveLastKnownButtonInfo()
            
            Log.d(tag, """
                Largest square clickable node found:
                contentDescription: ${largestNode?.contentDescription}
                resource-id: ${largestNode?.viewIdResourceName}
                className: ${largestNode?.className}
                text: ${largestNode?.text}
                boundsInScreen: $bounds
                positionOnScreen: $positionOnScreen
                isClickable: ${largestNode?.isClickable}
                isSquare: true
                Saved button location and information
            """.trimIndent())
        } else {
            Log.d(tag, "No square clickable nodes found on screen")
        }

        return largestNode
    }

    /**
     * Returns a list of all clickable node candidates that could be shutter buttons.
     * This is for manual selection UI and does not affect automatic detection.
     */
    fun getAllShutterButtonCandidates(root: AccessibilityNodeInfo, packageName: String): List<ShutterButtonInfo> {
        val candidates = mutableListOf<ShutterButtonInfo>()
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()
        
        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val validation = CameraDetectionUtils.validateShutterButton(node, packageName)
                
                // Calculate position on screen
                val positionOnScreen = calculatePositionOnScreen(bounds, screenWidth, screenHeight)
                
                candidates.add(
                    ShutterButtonInfo(
                        bounds = bounds,
                        packageName = packageName,
                        contentDescription = node.contentDescription?.toString(),
                        resourceId = node.viewIdResourceName,
                        className = node.className?.toString(),
                        text = node.text?.toString(),
                        confidenceScore = validation.score,
                        detectionMethod = null, // Not detected by a specific method
                        positionOnScreen = positionOnScreen
                    )
                )
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }
        traverse(root)
        return candidates.sortedByDescending { it.confidenceScore }
    }

    /**
     * Save the user-selected button info to preferences for a specific app.
     */
    fun saveUserPreferredButton(context: Context, packageName: String, buttonInfo: ShutterButtonInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val allPreferred = loadUserPreferredButtons(context).toMutableMap()
        allPreferred[packageName] = buttonInfo
        val json = gson.toJson(allPreferred)
        prefs.edit().putString(KEY_USER_PREFERRED_BUTTONS, json).apply()
    }

    /**
     * Load the user-selected button info for a specific app.
     */
    fun loadUserPreferredButton(context: Context, packageName: String): ShutterButtonInfo? {
        return loadUserPreferredButtons(context)[packageName]
    }

    /**
     * Load all user-preferred buttons from SharedPreferences.
     */
    private fun loadUserPreferredButtons(context: Context): Map<String, ShutterButtonInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY_USER_PREFERRED_BUTTONS, null)
        val loadedData: Map<String, ShutterButtonInfo> = if (json != null) {
            val type = object : TypeToken<Map<String, ShutterButtonInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } else {
            emptyMap()
        }
        
        // Migrate existing data by calculating missing positionOnScreen values
        val migratedData = loadedData.mapValues { (_, buttonInfo) ->
            if (buttonInfo.positionOnScreen == null) {
                // Calculate position on screen for existing data
                val positionOnScreen = buttonInfo.calculatePositionOnScreen(1080, 1920) // Default screen dimensions
                buttonInfo.copy(positionOnScreen = positionOnScreen)
            } else {
                buttonInfo
            }
        }
        
        // Save migrated data back to SharedPreferences if any changes were made
        if (migratedData != loadedData) {
            val allPreferred = migratedData.toMutableMap()
            val json = gson.toJson(allPreferred)
            prefs.edit().putString(KEY_USER_PREFERRED_BUTTONS, json).apply()
        }
        
        return migratedData
    }

    /**
     * Save all candidate lists to SharedPreferences (persisted by package name)
     */
    fun saveAllCandidateLists(context: Context, candidatesByApp: Map<String, List<ShutterButtonInfo>>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(candidatesByApp)
        prefs.edit().putString(KEY_CANDIDATE_LISTS, json).apply()
    }

    /**
     * Load all candidate lists from SharedPreferences
     */
    fun loadAllCandidateLists(context: Context): Map<String, List<ShutterButtonInfo>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY_CANDIDATE_LISTS, null)
        val loadedData: Map<String, List<ShutterButtonInfo>> = if (json != null) {
            val type = object : TypeToken<Map<String, List<ShutterButtonInfo>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } else {
            emptyMap()
        }
        
        // Migrate existing data by calculating missing positionOnScreen values
        val migratedData = loadedData.mapValues { (_, candidates) ->
            candidates.map { candidate ->
                if (candidate.positionOnScreen == null) {
                    // Calculate position on screen for existing data
                    val positionOnScreen = candidate.calculatePositionOnScreen(1080, 1920) // Default screen dimensions
                    candidate.copy(positionOnScreen = positionOnScreen)
                } else {
                    candidate
                }
            }
        }
        
        // Save migrated data back to SharedPreferences if any changes were made
        if (migratedData != loadedData) {
            saveAllCandidateLists(context, migratedData)
        }
        
        return migratedData
    }
} 
package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.garmin.android.apps.camera.click.comm.detection.ButtonLocationRepository
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug utilities to help monitor and validate reliability improvements
 */
object ReliabilityDebugUtils {
    private const val TAG = "ReliabilityDebug"
    
    /**
     * Comprehensive button analysis for debugging
     */
    fun analyzeButton(node: AccessibilityNodeInfo, packageName: String, context: String = ""): ButtonAnalysis {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val reliability = ButtonReliabilityUtils.validateButtonReliability(node, packageName)
        val isSwitch = ButtonReliabilityUtils.isLikelyCameraSwitchButton(node)
        val isShutter = ButtonReliabilityUtils.isLikelyShutterButton(node)
        
        val analysis = ButtonAnalysis(
            context = context,
            packageName = packageName,
            bounds = bounds,
            contentDescription = node.contentDescription?.toString(),
            resourceId = node.viewIdResourceName,
            className = node.className?.toString(),
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            reliabilityScore = reliability.score,
            reliabilityIssues = reliability.issues,
            isLikelySwitch = isSwitch,
            isLikelyShutter = isShutter,
            timestamp = System.currentTimeMillis()
        )
        
        logButtonAnalysis(analysis)
        return analysis
    }
    
    /**
     * Log detailed button analysis
     */
    private fun logButtonAnalysis(analysis: ButtonAnalysis) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(analysis.timestamp))
        
        Log.d(TAG, """
            ========== BUTTON ANALYSIS [$timestamp] ==========
            Context: ${analysis.context}
            Package: ${analysis.packageName}
            Bounds: ${analysis.bounds}
            Content Description: ${analysis.contentDescription}
            Resource ID: ${analysis.resourceId}
            Class: ${analysis.className}
            Clickable: ${analysis.isClickable}
            Enabled: ${analysis.isEnabled}
            
            === RELIABILITY ASSESSMENT ===
            Score: ${analysis.reliabilityScore}/100
            Issues: ${analysis.reliabilityIssues.joinToString(", ")}
            
            === CLASSIFICATION ===
            Likely Switch Button: ${analysis.isLikelySwitch}
            Likely Shutter Button: ${analysis.isLikelyShutter}
            
            === RECOMMENDATION ===
            ${getRecommendation(analysis)}
            ================================================
        """.trimIndent())
        
        // Also log to Firebase for remote monitoring
        FirebaseCrashlytics.getInstance().log("Button Analysis: ${analysis.packageName} - Score: ${analysis.reliabilityScore}, Switch: ${analysis.isLikelySwitch}, Shutter: ${analysis.isLikelyShutter}")
    }
    
    /**
     * Get recommendation based on analysis
     */
    private fun getRecommendation(analysis: ButtonAnalysis): String {
        return when {
            analysis.isLikelySwitch -> "❌ AVOID - This appears to be a camera switch button"
            analysis.isLikelyShutter && analysis.reliabilityScore >= 80 -> "✅ EXCELLENT - High confidence shutter button"
            analysis.reliabilityScore >= 70 -> "✅ GOOD - Reliable button candidate"
            analysis.reliabilityScore >= 50 -> "⚠️ FAIR - May work but has issues: ${analysis.reliabilityIssues.joinToString(", ")}"
            else -> "❌ POOR - Low reliability, avoid if possible"
        }
    }
    
    /**
     * Test click reliability on a button
     */
    fun testClickReliability(node: AccessibilityNodeInfo, packageName: String): ClickTestResult {
        val startTime = System.currentTimeMillis()
        val clickResult = ButtonReliabilityUtils.performReliableClick(node, packageName)
        val endTime = System.currentTimeMillis()
        
        val result = ClickTestResult(
            success = clickResult.success,
            method = clickResult.method,
            duration = clickResult.duration,
            totalTestTime = endTime - startTime,
            error = clickResult.error
        )
        
        Log.d(TAG, """
            ========== CLICK TEST RESULT ==========
            Package: $packageName
            Success: ${result.success}
            Method: ${result.method}
            Click Duration: ${result.duration}ms
            Total Test Time: ${result.totalTestTime}ms
            Error: ${result.error ?: "None"}
            ======================================
        """.trimIndent())
        
        return result
    }
    
    /**
     * Validate user preferences persistence
     */
    fun testUserPreferencesPersistence(
        context: Context,
        packageName: String,
        repository: ButtonLocationRepository
    ): PreferencesTestResult {
        val testButton = ShutterButtonInfo(
            bounds = Rect(100, 100, 200, 200),
            packageName = packageName,
            contentDescription = "Test Button",
            resourceId = "test_button",
            confidenceScore = 95,
            timestamp = System.currentTimeMillis()
        )

        try {
            // Test saving
            val saveStartTime = System.currentTimeMillis()
            repository.saveUserPreferredButton(packageName, testButton)
            val saveTime = System.currentTimeMillis() - saveStartTime

            // Test loading
            val loadStartTime = System.currentTimeMillis()
            val loadedButton = repository.getUserPreferredButton(packageName)
            val loadTime = System.currentTimeMillis() - loadStartTime
            
            val success = loadedButton != null && 
                         loadedButton.packageName == testButton.packageName &&
                         loadedButton.contentDescription == testButton.contentDescription
            
            val result = PreferencesTestResult(
                success = success,
                saveTime = saveTime,
                loadTime = loadTime,
                savedButton = testButton,
                loadedButton = loadedButton,
                error = null
            )
            
            Log.d(TAG, """
                ========== PREFERENCES TEST RESULT ==========
                Package: $packageName
                Success: ${result.success}
                Save Time: ${result.saveTime}ms
                Load Time: ${result.loadTime}ms
                Data Integrity: ${if (success) "✅ PASSED" else "❌ FAILED"}
                ============================================
            """.trimIndent())
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Preferences test failed", e)
            return PreferencesTestResult(
                success = false,
                saveTime = 0,
                loadTime = 0,
                savedButton = testButton,
                loadedButton = null,
                error = e.message
            )
        }
    }
    
    /**
     * Generate a comprehensive reliability report
     */
    fun generateReliabilityReport(
        context: Context,
        repository: ButtonLocationRepository
    ): ReliabilityReport {
        val packageNames = listOf(
            "com.google.android.GoogleCamera",
            "com.android.camera",
            "com.sec.android.app.camera",
            "com.oplus.camera",
            "com.motorola.camera3"
        )

        val preferencesTests = packageNames.map { packageName ->
            packageName to testUserPreferencesPersistence(context, packageName, repository)
        }.toMap()
        
        val report = ReliabilityReport(
            timestamp = System.currentTimeMillis(),
            preferencesTests = preferencesTests,
            overallSuccess = preferencesTests.values.all { it.success }
        )
        
        Log.i(TAG, """
            ========== RELIABILITY REPORT ==========
            Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp))}
            Overall Success: ${if (report.overallSuccess) "✅ PASSED" else "❌ FAILED"}
            
            Preferences Tests:
            ${preferencesTests.entries.joinToString("\n") { (pkg, result) ->
                "  $pkg: ${if (result.success) "✅" else "❌"} (Save: ${result.saveTime}ms, Load: ${result.loadTime}ms)"
            }}
            =======================================
        """.trimIndent())
        
        return report
    }
    
    data class ButtonAnalysis(
        val context: String,
        val packageName: String,
        val bounds: Rect,
        val contentDescription: String?,
        val resourceId: String?,
        val className: String?,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val reliabilityScore: Int,
        val reliabilityIssues: List<String>,
        val isLikelySwitch: Boolean,
        val isLikelyShutter: Boolean,
        val timestamp: Long
    )
    
    data class ClickTestResult(
        val success: Boolean,
        val method: String,
        val duration: Long,
        val totalTestTime: Long,
        val error: String?
    )
    
    data class PreferencesTestResult(
        val success: Boolean,
        val saveTime: Long,
        val loadTime: Long,
        val savedButton: ShutterButtonInfo,
        val loadedButton: ShutterButtonInfo?,
        val error: String?
    )
    
    data class ReliabilityReport(
        val timestamp: Long,
        val preferencesTests: Map<String, PreferencesTestResult>,
        val overallSuccess: Boolean
    )
}
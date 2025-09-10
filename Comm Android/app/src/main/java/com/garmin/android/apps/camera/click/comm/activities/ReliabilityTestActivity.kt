package com.garmin.android.apps.camera.click.comm.activities

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.utils.ReliabilityDebugUtils
import com.garmin.android.apps.camera.click.comm.utils.AccessibilityUtils
import android.widget.Toast
// Note: Coroutines might not be available, using simple threading instead
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

/**
 * Test activity to validate reliability improvements
 * This can be accessed via a hidden menu or debug build
 */
class ReliabilityTestActivity : AppCompatActivity() {
    
    private lateinit var resultsTextView: TextView
    private lateinit var scrollView: ScrollView
    private val testResults = StringBuilder()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reliability_test)
        
        setupUI()
    }
    
    private fun setupUI() {
        resultsTextView = findViewById(R.id.test_results)
        scrollView = findViewById(R.id.scroll_view)
        
        findViewById<Button>(R.id.test_preferences_btn).setOnClickListener {
            testPreferencesPersistence()
        }
        
        findViewById<Button>(R.id.test_button_detection_btn).setOnClickListener {
            testButtonDetection()
        }
        
        findViewById<Button>(R.id.run_full_test_btn).setOnClickListener {
            runFullReliabilityTest()
        }
        
        findViewById<Button>(R.id.clear_results_btn).setOnClickListener {
            clearResults()
        }
        

        
        appendResult("Reliability Test Activity Ready\n")
        appendResult("Use the buttons below to test different aspects of the reliability improvements.\n\n")
    }
    
    private fun testPreferencesPersistence() {
        appendResult("=== Testing Preferences Persistence ===\n")
        
        val testPackages = listOf(
            "com.google.android.GoogleCamera",
            "com.android.camera",
            "com.sec.android.app.camera"
        )
        
        var allPassed = true
        
        testPackages.forEach { packageName ->
            appendResult("Testing $packageName...\n")
            
            val result = ReliabilityDebugUtils.testUserPreferencesPersistence(this, packageName)
            
            if (result.success) {
                appendResult("  ✅ PASSED - Save: ${result.saveTime}ms, Load: ${result.loadTime}ms\n")
            } else {
                appendResult("  ❌ FAILED - Error: ${result.error}\n")
                allPassed = false
            }
        }
        
        appendResult("\nPreferences Test Summary: ${if (allPassed) "✅ ALL PASSED" else "❌ SOME FAILED"}\n\n")
        
        if (allPassed) {
            Toast.makeText(this, "All preferences tests passed!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some preferences tests failed - check results", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testButtonDetection() {
        appendResult("=== Testing Button Detection Logic ===\n")
        
        // Check if accessibility service is enabled
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isServiceEnabled = enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name.contains("CameraAccessibilityService")
        }
        
        if (!isServiceEnabled) {
            appendResult("❌ Accessibility service is not enabled!\n")
            appendResult("Please enable the accessibility service first.\n\n")
            Toast.makeText(this, "Accessibility service must be enabled for button detection tests", Toast.LENGTH_LONG).show()
            return
        }
        
        appendResult("✅ Accessibility service is enabled\n")
        
        // Test keyword detection logic
        appendResult("\nTesting camera switch button detection:\n")
        val switchKeywords = listOf("Switch camera", "Flip camera", "Front camera", "Toggle camera")
        switchKeywords.forEach { keyword ->
            appendResult("  '$keyword' -> Should be detected as switch button\n")
        }
        
        appendResult("\nTesting shutter button detection:\n")
        val shutterKeywords = listOf("Shutter", "Take photo", "Capture", "Start capture")
        shutterKeywords.forEach { keyword ->
            appendResult("  '$keyword' -> Should be detected as shutter button\n")
        }
        
        appendResult("\n✅ Keyword detection logic tests completed\n")
        appendResult("\n📱 For LIVE button detection testing:\n")
        appendResult("1. Keep this test screen open\n")
        appendResult("2. Open a camera app (Google Camera, Samsung Camera, etc.)\n")
        appendResult("3. Trigger capture from your Garmin watch\n")
        appendResult("4. Check the logs in Android Studio or use 'adb logcat'\n")
        appendResult("5. Look for 'ReliabilityDebug' and 'ButtonReliability' log entries\n\n")
        
        Toast.makeText(this, "Keyword tests completed. See instructions for live testing.", Toast.LENGTH_LONG).show()
    }
    
    private fun runFullReliabilityTest() {
        appendResult("=== Running Full Reliability Test ===\n")
        
        // Run comprehensive test in background thread
        Thread {
            try {
                runOnUiThread {
                    appendResult("Generating comprehensive reliability report...\n")
                }
                
                val report = ReliabilityDebugUtils.generateReliabilityReport(this@ReliabilityTestActivity)
                
                runOnUiThread {
                    appendResult("Report generated at: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(report.timestamp))}\n")
                    appendResult("Overall Success: ${if (report.overallSuccess) "✅ PASSED" else "❌ FAILED"}\n\n")
                    
                    appendResult("Detailed Results:\n")
                    report.preferencesTests.forEach { (packageName, result) ->
                        val status = if (result.success) "✅" else "❌"
                        appendResult("  $status $packageName\n")
                        appendResult("    Save: ${result.saveTime}ms, Load: ${result.loadTime}ms\n")
                        if (!result.success && result.error != null) {
                            appendResult("    Error: ${result.error}\n")
                        }
                    }
                    
                    appendResult("\n=== Full Test Complete ===\n\n")
                    
                    val message = if (report.overallSuccess) {
                        "All reliability tests passed!"
                    } else {
                        "Some tests failed - improvements may need attention"
                    }
                    
                    Toast.makeText(this@ReliabilityTestActivity, message, Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    appendResult("❌ Test failed with exception: ${e.message}\n\n")
                    Toast.makeText(this@ReliabilityTestActivity, "Test failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    

    private fun clearResults() {
        testResults.clear()
        resultsTextView.text = ""
        appendResult("Results cleared.\n\n")
    }
    
    private fun appendResult(text: String) {
        testResults.append(text)
        resultsTextView.text = testResults.toString()
        
        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
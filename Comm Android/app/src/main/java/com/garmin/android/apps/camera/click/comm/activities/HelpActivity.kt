package com.garmin.android.apps.camera.click.comm.activities

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.garmin.android.apps.camera.click.comm.R
import com.google.firebase.analytics.FirebaseAnalytics
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils

/**
 * Activity that displays help information about the app in a structured, elegant format.
 */
class HelpActivity : AppCompatActivity() {
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Record start time
        startTime = System.currentTimeMillis()

        // Log help screen view
        val bundle = Bundle().apply {
            putString("page_type", "documentation")
        }
        AnalyticsUtils.logScreenView("help", "HelpActivity", bundle)

        setupTitleAndDescription()
        setupSections()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Calculate time spent and log it
        val timeSpent = System.currentTimeMillis() - startTime
        val bundle = Bundle().apply {
            putString("page_name", "help")
            putLong("time_spent_ms", timeSpent)
        }
        FirebaseAnalytics.getInstance(this).logEvent("time_spent", bundle)
    }

    private fun setupTitleAndDescription() {
//        findViewById<TextView>(R.id.help_title).text = "CameraClick Help & Setup Guide"

        val descriptionText = "<font color='#3fe9a3'><b>CameraClick</b></font> lets you use your <b>Garmin watch as a remote shutter</b> " +
                "for your phone's camera. Whether you're snapping solo trail shots or setting up for " +
                "hands-free filming, CameraClick makes it simple."

        findViewById<TextView>(R.id.help_description).apply {
            text = HtmlCompat.fromHtml(descriptionText, HtmlCompat.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun setupSections() {
        val sectionsContainer = findViewById<LinearLayout>(R.id.sections_container)

        // Getting Started Section
        createSection(
            sectionsContainer,
            "Getting Started",
            "<ol>" +
                    "<b>Install Both Apps</b><br>" +
                    "• <b>Phone App:</b> You're here — already installed!<br>" +
                    "• <b>Watch App:</b> Install CameraClick on your Garmin via the <b>Garmin Connect IQ Store</b> <br><br>" +

                    "<b>Grant Permissions</b><br>" +
                    "• <b><font color='#FF0000'>⚠️ Accessibility Permission</font></b> – <b>REQUIRED!</b> This allows the app to tap the shutter button on screen.<br>" +
                    "• <b>Notification Access</b> – Keeps CameraClick running in the background.<br>" +
                    "• <b>Camera</b> – Required by your camera app, not CameraClick.<br><br>" +

                    "<b>Enable Accessibility Service:</b><br>" +
                    "   - Go to <code>Settings > Accessibility</code><br>" +
                    "   - Find <b>CameraClick</b><br>" +
                    "   - Toggle it <b>ON</b></li><br>" +

                    "<br><b>Enable Switch Access</b><br>" +
                    "Optional but required for video on some devices (no need to configure any switches).<br>" +
                    "<b>To enable:</b><br>" +
                    "   - Go to <code>Settings > Accessibility > Switch Access</code><br>" +
                    "   - Toggle it <b>ON</b></li>" +
                    "</ol>"
        )

        // Common Issues & Fixes Section
        createSection(
            sectionsContainer,
            "⚠️ Common Issues & Fixes",
            "<b>Watch doesn't trigger shutter</b><br>" +
                    "→ Check Bluetooth & ensure Garmin app is connected<br><br>" +

                    "<b>Nothing happens</b><br>" +
                    "→ Ensure the <b>camera app is open and visible</b><br><br>" +

                    "<b>Shutter not responding</b><br>" +
                    "→ Make sure <b>Accessibility is enabled</b><br><br>" +

                    "<b>Video capture doesn't work</b><br>" +
                    "→ Try turning <b>Switch Access ON</b> (no configuration needed)<br><br>" +

                    "<b>App stops working in background</b><br>" +
                    "→ Exempt CameraClick from <b>Battery Optimization</b><br><br>" +

                    "<b>Clicking the wrong button</b><br>" +
                    "→ Choose a different button from the settings menu "
        )

        // How It Works Section
        createSection(
            sectionsContainer,
            "How It Works",
            "<ul>" +
                    "<li> Open <b>CameraClick</b> on your phone.</li>" +
                    "<li> Open your camera app.</li>" +
                    "<li> When your Garmin sends the command, CameraClick uses Accessibility to tap a button on screen.</li>" +
                    "<br>That's it!"
        )

        // What You Can Use Section
        createSection(
            sectionsContainer,
            "What You Can Use",
            "Because CameraClick taps your native camera app:<br>" +
                    "<ul>" +
                    "<li> All settings (flash, timer, filters) are controlled in your camera app</li>" +
                    "<li> Works with <b>front and back cameras</b></li>" +
                    "<li> Supports <b>photo and video mode</b> <br></li>" +
                    "</ul>" +
                    "<br>Set buttons to control other apps from your watch"

        )


        // Test Feature Section
        createSection(
            sectionsContainer,
            "🧪 Test Feature",
            "You can use the <b>\"Send Test\"</b> button in the app to verify communication with your watch."
        )

        // Quick Links Section
        createSection(
            sectionsContainer,
            "📎 Quick Links",
            "📝 <a href='https://forms.gle/3JXQ9fDrTEBAuroG7'>Send Feedback</a>" +
                    "<br><br>⌚ <a href='https://apps.garmin.com/apps/789012f0-dcb2-46c2-b5b8-ef00a75968fa'>CameraClick Garmin App</a>" +
                    "<br><br>🌐 <a href='https://calebseely.com'>I'm looking for a job!</a>"

        )
    }

    private fun createSection(container: LinearLayout, title: String, content: String) {
        val sectionView = LayoutInflater.from(this).inflate(R.layout.help_section, container, false)

        // Set the section title
        sectionView.findViewById<TextView>(R.id.section_title).text = title

        // Set the section content with HTML formatting
        sectionView.findViewById<TextView>(R.id.section_content).apply {
            text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance() // Makes links clickable
        }

        // Add the section to the container
        container.addView(sectionView)
    }
}
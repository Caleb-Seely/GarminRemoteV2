package com.garmin.android.apps.camera.click.comm.activities

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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

        // Setup modern back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

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
        finish()
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
        findViewById<TextView>(R.id.help_description).apply {
            text = HtmlCompat.fromHtml(getString(R.string.help_intro_description), HtmlCompat.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun setupSections() {
        val sectionsContainer = findViewById<LinearLayout>(R.id.sections_container)

        createSection(
            sectionsContainer,
            getString(R.string.help_section_system_status_title),
            getString(R.string.help_section_system_status_content),
            isSystemStatus = true
        )

        createSection(
            sectionsContainer,
            getString(R.string.help_section_getting_started_title),
            getString(R.string.help_section_getting_started_content)
        )

        createSection(
            sectionsContainer,
            getString(R.string.help_section_common_issues_title),
            getString(R.string.help_section_common_issues_content)
        )

        createSection(
            sectionsContainer,
            getString(R.string.help_section_how_it_works_title),
            getString(R.string.help_section_how_it_works_content)
        )

        createSection(
            sectionsContainer,
            getString(R.string.help_section_what_you_can_use_title),
            getString(R.string.help_section_what_you_can_use_content)
        )

        createSection(
            sectionsContainer,
            getString(R.string.help_section_test_feature_title),
            getString(R.string.help_section_test_feature_content)
        )

        createSection(
            sectionsContainer,
            getString(R.string.help_section_quick_links_title),
            getString(R.string.help_section_quick_links_content)
        )
    }

    private fun createSection(
        container: LinearLayout,
        title: String,
        content: String,
        isSystemStatus: Boolean = false
    ) {
        val sectionView = LayoutInflater.from(this).inflate(R.layout.help_section, container, false)

        sectionView.findViewById<TextView>(R.id.section_title).text = title

        val contentView = sectionView.findViewById<TextView>(R.id.section_content)
        contentView.apply {
            text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance()
        }

        if (isSystemStatus) {
            contentView.setOnClickListener {
                startActivity(Intent(this, SystemStatusActivity::class.java))
                AnalyticsUtils.logFeatureUsage("compatibility_check", "manual_trigger_from_help", true)
            }
        }

        container.addView(sectionView)
    }
}
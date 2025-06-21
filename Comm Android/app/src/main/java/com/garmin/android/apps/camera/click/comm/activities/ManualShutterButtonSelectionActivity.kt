package com.garmin.android.apps.camera.click.comm.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.AccessibilityUtils
import com.garmin.android.apps.camera.click.comm.views.ButtonLocationOverlay
import com.google.gson.Gson
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import android.widget.Toast

class ManualShutterButtonSelectionActivity : AppCompatActivity() {
    private lateinit var candidateList: List<ShutterButtonInfo>
    private lateinit var overlay: ButtonLocationOverlay
    private var selectedIndex: Int = 0
    private lateinit var appSpinner: AutoCompleteTextView
    private lateinit var appList: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_shutter_selection)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val title = findViewById<TextView>(R.id.manual_selection_title)
        val recycler = findViewById<RecyclerView>(R.id.candidate_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        val confirmBtn = findViewById<TextView>(R.id.confirm_button)
        overlay = findViewById(R.id.button_location_overlay)

        title.text = getString(R.string.manual_selection_title)

        appSpinner = findViewById(R.id.camera_app_spinner)
        appList = CameraAppCandidateStore.candidatesByApp.keys.toList()
        val spinnerAdapter = ArrayAdapter(this, R.layout.item_camera_app, appList)
        appSpinner.setAdapter(spinnerAdapter)

        appSpinner.setOnItemClickListener { parent, view, position, id ->
            val selectedApp = appList[position]
            candidateList = CameraAppCandidateStore.candidatesByApp[selectedApp] ?: emptyList()
            
            // Check if there's a saved preference for this app
            val savedButton = AccessibilityUtils.loadUserPreferredButton(this@ManualShutterButtonSelectionActivity, selectedApp)
            selectedIndex = if (savedButton != null) {
                // Find the index of the saved button in the candidate list
                candidateList.indexOfFirst { candidate ->
                    candidate.resourceId == savedButton.resourceId &&
                    candidate.contentDescription == savedButton.contentDescription &&
                    candidate.className == savedButton.className
                }.takeIf { it >= 0 } ?: 0
            } else {
                0 // Default to first button if no saved preference
            }
            
            val adapter = ShutterButtonCandidateAdapter(candidateList, selectedIndex) { index ->
                selectedIndex = index
                overlay.setButtonInfo(candidateList[index])
            }
            recycler.adapter = adapter
            if (candidateList.isNotEmpty()) {
                overlay.setButtonInfo(candidateList[selectedIndex])
            } else {
                overlay.setButtonInfo(null)
                Toast.makeText(this@ManualShutterButtonSelectionActivity, "No shutter button candidates found for this app. Try opening the camera app and returning here.", Toast.LENGTH_LONG).show()
            }
        }

        // Default to first app if available
        if (appList.isNotEmpty()) {
            appSpinner.setText(appList[0], false)
            // Manually trigger the listener
            appSpinner.onItemClickListener?.onItemClick(null, null, 0, 0)
        }

        confirmBtn.setOnClickListener {
            if (candidateList.isNotEmpty() && appList.isNotEmpty()) {
                val selectedPackageName = appSpinner.text.toString()
                val selectedButtonInfo = candidateList[selectedIndex]
                AccessibilityUtils.saveUserPreferredButton(this, selectedPackageName, selectedButtonInfo)
                Toast.makeText(this, "Preference saved for $selectedPackageName", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
} 
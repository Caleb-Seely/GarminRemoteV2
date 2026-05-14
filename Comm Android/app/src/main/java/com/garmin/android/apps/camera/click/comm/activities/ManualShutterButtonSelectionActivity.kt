package com.garmin.android.apps.camera.click.comm.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.detection.ButtonLocationRepository
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.views.ButtonLocationOverlay
import com.google.gson.Gson
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ManualShutterButtonSelectionActivity : AppCompatActivity() {

    @Inject
    lateinit var buttonLocationRepository: ButtonLocationRepository

    private var candidateList: List<ShutterButtonInfo> = emptyList()
    private lateinit var overlay: ButtonLocationOverlay
    private lateinit var recycler: RecyclerView
    private lateinit var emptyStateHint: TextView
    private lateinit var spinnerContainer: View
    private var selectedIndex: Int = 0
    private lateinit var appSpinner: AutoCompleteTextView
    private lateinit var appInfoList: List<AppInfo>

    // Data class to hold both app name and package name
    private data class AppInfo(val appName: String, val packageName: String) {
        override fun toString(): String = appName
    }

    // Custom adapter to display app name and package name
    private class AppInfoAdapter(
        context: Context,
        private val appInfoList: List<AppInfo>
    ) : ArrayAdapter<AppInfo>(context, R.layout.item_camera_app, appInfoList) {

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            return createViewFromResource(position, convertView, parent)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            return createViewFromResource(position, convertView, parent)
        }

        private fun createViewFromResource(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = convertView ?: android.view.LayoutInflater.from(context)
                .inflate(R.layout.item_camera_app, parent, false)

            val appInfo = appInfoList[position]
            view.findViewById<TextView>(R.id.app_name).text = appInfo.appName
            view.findViewById<TextView>(R.id.package_name).text = appInfo.packageName

            return view
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_shutter_selection)

        // Set status bar color to black (modern approach)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.BLACK

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Enable back button in the toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val title = findViewById<TextView>(R.id.manual_selection_title)
        recycler = findViewById(R.id.candidate_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        val confirmBtn = findViewById<TextView>(R.id.confirm_button)
        overlay = findViewById(R.id.button_location_overlay)

        title.text = getString(R.string.manual_selection_title)

        emptyStateHint = findViewById(R.id.empty_state_hint)
        spinnerContainer = findViewById(R.id.spinner_container)
        appSpinner = findViewById(R.id.camera_app_spinner)

        appSpinner.setOnItemClickListener { _, _, position, _ ->
            loadCandidatesForPosition(position)
        }

        confirmBtn.setOnClickListener {
            if (candidateList.isNotEmpty() && appInfoList.isNotEmpty()) {
                // Find the AppInfo matching the displayed app name
                val selectedAppName = appSpinner.text.toString()
                val selectedAppInfo = appInfoList.find { it.appName == selectedAppName }

                if (selectedAppInfo != null) {
                    val selectedPackageName = selectedAppInfo.packageName
                    val selectedButtonInfo = candidateList[selectedIndex]
                    Log.d("ManualSelection", "Saving user preferred button for $selectedPackageName (${selectedAppInfo.appName})")
                    Log.d("ManualSelection", "Button resourceId: ${selectedButtonInfo.resourceId}")
                    Log.d("ManualSelection", "Button bounds: ${selectedButtonInfo.bounds}")
                    Log.d("ManualSelection", "Button contentDesc: ${selectedButtonInfo.contentDescription}")
                    buttonLocationRepository.saveUserPreferredButton(selectedPackageName, selectedButtonInfo)
                    Toast.makeText(this, "Preference saved for ${selectedAppInfo.appName}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only load from prefs if the in-memory store is empty (e.g., after a process restart).
        // If the accessibility service already populated the store in this session, trust it —
        // calling loadAllFromPrefs() unconditionally would clear those fresh candidates and
        // replace them with potentially stale or empty persisted data.
        if (CameraAppCandidateStore.candidatesByApp.isEmpty()) {
            CameraAppCandidateStore.loadAllFromPrefs(this)
        }
        refreshAppList()
    }

    private fun refreshAppList() {
        val previousSelection = appSpinner.text?.toString()

        appInfoList = CameraAppCandidateStore.candidatesByApp.keys.map { packageName ->
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) {
                packageName.substringAfterLast('.')
            }
            AppInfo(appName, packageName)
        }.sortedBy { it.appName }

        val spinnerAdapter = AppInfoAdapter(this, appInfoList)
        appSpinner.setAdapter(spinnerAdapter)

        val hasApps = appInfoList.isNotEmpty()
        emptyStateHint.visibility = if (hasApps) View.GONE else View.VISIBLE
        recycler.visibility = if (hasApps) View.VISIBLE else View.GONE
        spinnerContainer.visibility = if (hasApps) View.VISIBLE else View.GONE

        if (hasApps) {
            val restoredIndex = appInfoList.indexOfFirst { it.appName == previousSelection }
                .takeIf { it >= 0 } ?: 0
            appSpinner.setText(appInfoList[restoredIndex].appName, false)
            loadCandidatesForPosition(restoredIndex)
        }
    }

    private fun loadCandidatesForPosition(position: Int) {
        val selectedAppInfo = appInfoList[position]
        val selectedPackageName = selectedAppInfo.packageName
        candidateList = CameraAppCandidateStore.candidatesByApp[selectedPackageName] ?: emptyList()
        Log.d("ManualSelection", "Package: $selectedPackageName | Store keys: ${CameraAppCandidateStore.candidatesByApp.keys} | Candidates: ${candidateList.size}")

        val savedButton = buttonLocationRepository.getUserPreferredButton(selectedPackageName)
        selectedIndex = if (savedButton != null) {
            candidateList.indexOfFirst { candidate ->
                candidate.resourceId == savedButton.resourceId &&
                candidate.contentDescription == savedButton.contentDescription &&
                candidate.className == savedButton.className
            }.takeIf { it >= 0 } ?: 0
        } else {
            0
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
            Toast.makeText(
                this,
                "No shutter button candidates found for this app. Try opening the camera app and returning here.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle back button press
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 
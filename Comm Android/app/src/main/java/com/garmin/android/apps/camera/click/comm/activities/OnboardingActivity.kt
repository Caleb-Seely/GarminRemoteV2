package com.garmin.android.apps.camera.click.comm.activities

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.CommConstants
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.adapter.IQDeviceAdapter
import com.garmin.android.apps.camera.click.comm.repository.CompatibilityCheckRepository
import com.garmin.android.apps.camera.click.comm.repository.DevicePreferencesRepository
import com.garmin.android.apps.camera.click.comm.service.CameraAccessibilityService
import com.garmin.android.apps.camera.click.comm.service.MessageService
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import com.garmin.android.apps.camera.click.comm.utils.CameraUtils
import com.garmin.android.apps.camera.click.comm.viewmodels.MainActivityViewModel
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "OnboardingActivity"
private const val TOTAL_STEPS = 5

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    @Inject lateinit var preferencesRepository: DevicePreferencesRepository
    @Inject lateinit var compatibilityRepo: CompatibilityCheckRepository

    private val viewModel: MainActivityViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startMessageServiceIfReady() }

    private var currentStep = 1
    private var selectedDevice: IQDevice? = null
    private var cameraTestStartTime: Long = 0L
    private var waitingForTrigger = false

    // Dots
    private val dots = mutableListOf<View>()

    // Nav buttons
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button
    private lateinit var stepContainer: FrameLayout

    // Current step view (cached after inflation)
    private var stepView: View? = null

    // Step 5 receiver for watch trigger during waiting state
    private var triggerReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.BLACK

        CameraAppCandidateStore.loadAllFromPrefs(this)

        btnNext = findViewById(R.id.btn_next)
        btnSkip = findViewById(R.id.btn_skip)
        stepContainer = findViewById(R.id.step_container)

        dots.add(findViewById(R.id.dot1))
        dots.add(findViewById(R.id.dot2))
        dots.add(findViewById(R.id.dot3))
        dots.add(findViewById(R.id.dot4))
        dots.add(findViewById(R.id.dot5))

        viewModel.initializeConnectIQSdk(this)
        viewModel.devices.observe(this) { devices ->
            if (currentStep == 2) updateDeviceList(devices)
        }

        showStep(1)
    }

    override fun onResume() {
        super.onResume()
        when (currentStep) {
            4 -> checkAccessibilityAndAdvance()
            5 -> onResumeStep5()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterTriggerReceiver()
        viewModel.shutdownConnectIQSdk(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        AnalyticsUtils.logOnboardingStep(currentStep, "abandoned")
    }

    // ── Step navigation ───────────────────────────────────────────────────────

    private fun showStep(step: Int) {
        currentStep = step
        updateDots()
        stepContainer.removeAllViews()
        stepView = null

        when (step) {
            1 -> showStep1()
            2 -> showStep2()
            3 -> showStep3()
            4 -> showStep4()
            5 -> showStep5()
        }

        AnalyticsUtils.logOnboardingStep(step, "viewed")
    }

    private fun advanceStep() {
        AnalyticsUtils.logOnboardingStep(currentStep, "completed")
        if (currentStep < TOTAL_STEPS) {
            showStep(currentStep + 1)
        } else {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        preferencesRepository.isFirstAppLaunch = false
        // Mark setup complete only if camera test passed (step 5 success state)
        // Otherwise the banner in DeviceActivity will prompt them to configure
        AnalyticsUtils.logOnboardingStep(TOTAL_STEPS, "completed")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun updateDots() {
        val activeDrawable = R.drawable.step_dot_active
        val inactiveDrawable = R.drawable.step_dot_inactive
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(if (i < currentStep) activeDrawable else inactiveDrawable)
        }
        findViewById<TextView>(R.id.step_counter)?.text = getString(R.string.step_counter_format, currentStep, TOTAL_STEPS)
    }

    // ── Step 1 — Welcome ─────────────────────────────────────────────────────

    private fun showStep1() {
        val view = layoutInflater.inflate(R.layout.onboarding_step1, stepContainer, false)

        // Set hint texts
        setHint(view, R.id.hint1, getString(R.string.onboarding_hint1))
        setHint(view, R.id.hint2, getString(R.string.onboarding_hint2))
        setHint(view, R.id.hint3, getString(R.string.onboarding_hint3))

        stepContainer.addView(view)
        stepView = view

        btnNext.setText(R.string.btn_get_started)
        btnSkip.visibility = View.GONE
        btnNext.setOnClickListener { advanceStep() }
    }

    private fun setHint(root: View, id: Int, text: String) {
        val hintLayout = root.findViewById<LinearLayout>(id) ?: return
        hintLayout.findViewById<TextView>(R.id.hint_text)?.text = text
    }

    // ── Step 2 — Connect Watch ────────────────────────────────────────────────

    private fun showStep2() {
        val view = layoutInflater.inflate(R.layout.onboarding_step2, stepContainer, false)
        stepContainer.addView(view)
        stepView = view

        btnNext.setText(R.string.btn_continue)
        btnSkip.visibility = View.VISIBLE
        btnSkip.setText(R.string.btn_skip)

        btnSkip.setOnClickListener { advanceStep() }
        btnNext.setOnClickListener {
            if (selectedDevice != null) {
                advanceStep()
            } else {
                Toast.makeText(this, R.string.onboarding_no_watch_connected, Toast.LENGTH_SHORT).show()
            }
        }

        val currentDevices = viewModel.devices.value ?: emptyList()
        updateDeviceList(currentDevices)
        viewModel.loadDevices()
    }

    private fun updateDeviceList(devices: List<IQDevice>) {
        val view = stepView ?: return
        if (currentStep != 2) return

        val recycler = view.findViewById<RecyclerView>(R.id.device_list) ?: return
        val noDevicesText = view.findViewById<TextView>(R.id.no_devices_text) ?: return
        val connectedBadge = view.findViewById<LinearLayout>(R.id.connected_badge)
        val connectedName = view.findViewById<TextView>(R.id.connected_device_name)

        val adapter = IQDeviceAdapter(
            onItemClickListener = { device ->
                selectedDevice = device
                preferencesRepository.preferredDeviceId = device.deviceIdentifier.toString()
                connectedBadge?.visibility = View.VISIBLE
                connectedName?.text = getString(R.string.onboarding_connected_device, device.friendlyName)
                // Auto-advance if only one device
                if (devices.size == 1) {
                    Handler(Looper.getMainLooper()).postDelayed({ advanceStep() }, 800)
                }
            },
            onSetDefaultDeviceListener = { device ->
                selectedDevice = device
                preferencesRepository.preferredDeviceId = device.deviceIdentifier.toString()
            },
            getPreferredDeviceId = { preferencesRepository.preferredDeviceId }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        adapter.submitList(devices)

        // Auto-select if only one connected device
        val connected = devices.filter { device ->
            try {
                ConnectIQ.getInstance().getDeviceStatus(device) == IQDevice.IQDeviceStatus.CONNECTED
            } catch (e: Exception) { false }
        }
        if (connected.size == 1 && selectedDevice == null) {
            selectedDevice = connected.first()
            connectedBadge?.visibility = View.VISIBLE
            connectedName?.text = "Connected: ${connected.first().friendlyName}"
            Handler(Looper.getMainLooper()).post { advanceStep() }
        }

        noDevicesText.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Step 3 — Test Phone → Watch ───────────────────────────────────────────

    private fun showStep3() {
        val view = layoutInflater.inflate(R.layout.onboarding_step4, stepContainer, false)
        stepContainer.addView(view)
        stepView = view

        btnNext.setText(R.string.btn_continue)
        btnSkip.visibility = View.VISIBLE
        btnSkip.setText(R.string.btn_skip)
        btnSkip.setOnClickListener { advanceStep() }

        view.findViewById<Button>(R.id.btn_send_test)?.setOnClickListener {
            sendTestMessage(view)
        }

        btnNext.setOnClickListener { advanceStep() }

        // Ensure MessageService is running before we need it for Step 5.
        // Request notification permission first on API 33+ so the foreground
        // notification actually shows (keeps the service alive under memory pressure).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMessageServiceIfReady()
        }
    }

    // ── Step 4 — Accessibility ────────────────────────────────────────────────

    private fun showStep4() {
        val view = layoutInflater.inflate(R.layout.onboarding_step3, stepContainer, false)
        stepContainer.addView(view)
        stepView = view

        btnNext.visibility = View.VISIBLE
        btnNext.setText(R.string.btn_open_accessibility_settings)
        btnNext.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btnSkip.visibility = View.GONE

        updateAccessibilityStatus(view)
        if (compatibilityRepo.isAccessibilityServiceEnabled()) {
            Handler(Looper.getMainLooper()).post { advanceStep() }
        }
    }

    private fun checkAccessibilityAndAdvance() {
        val view = stepView ?: return
        if (currentStep != 4) return
        updateAccessibilityStatus(view)
        if (compatibilityRepo.isAccessibilityServiceEnabled()) {
            Handler(Looper.getMainLooper()).postDelayed({ advanceStep() }, 600)
        }
    }

    private fun updateAccessibilityStatus(view: View) {
        val enabled = compatibilityRepo.isAccessibilityServiceEnabled()
        val icon = view.findViewById<ImageView>(R.id.status_icon)
        val text = view.findViewById<TextView>(R.id.status_text)
        if (enabled) {
            icon?.setImageResource(R.drawable.baseline_check_24)
            icon?.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.success))
        } else {
            icon?.setImageResource(R.drawable.baseline_close_24)
            icon?.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error))
        }
        text?.text = if (enabled) getString(R.string.accessibility_status_enabled) else getString(R.string.accessibility_status_not_enabled_tap)
    }

    private fun sendTestMessage(view: View) {
        val device = selectedDevice ?: run {
            Toast.makeText(this, R.string.onboarding_no_watch_go_back, Toast.LENGTH_SHORT).show()
            return
        }
        val resultArea = view.findViewById<LinearLayout>(R.id.test_result)
        val resultIcon = view.findViewById<TextView>(R.id.result_icon)
        val resultText = view.findViewById<TextView>(R.id.result_text)

        try {
            val connectIQ = ConnectIQ.getInstance()
            val app = IQApp(CommConstants.COMM_WATCH_ID)
            connectIQ.sendMessage(device, app, "test") { _, _, status ->
                runOnUiThread {
                    resultArea?.visibility = View.VISIBLE
                    val success = status == com.garmin.android.connectiq.ConnectIQ.IQMessageStatus.SUCCESS
                    resultIcon?.text = if (success) "✓" else "!"
                    resultIcon?.setTextColor(getColor(if (success) R.color.success else R.color.warning))
                    resultText?.text = if (success) getString(R.string.onboarding_test_signal_sent) else getString(R.string.onboarding_test_sent_status, status.name)
                }
                AnalyticsUtils.logOnboardingStep(3, if (status == com.garmin.android.connectiq.ConnectIQ.IQMessageStatus.SUCCESS) "test_sent" else "test_failed")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            resultArea?.visibility = View.VISIBLE
            resultText?.text = getString(R.string.onboarding_test_could_not_send)
        }
    }

    private fun startMessageServiceIfReady() {
        val device = selectedDevice ?: return
        try {
            val serviceIntent = MessageService.createIntent(this, device, CommConstants.COMM_WATCH_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) { /* service may already be running */ }
    }

    // ── Step 5 — Camera Test ──────────────────────────────────────────────────

    private fun showStep5() {
        if (!compatibilityRepo.isAccessibilityServiceEnabled()) {
            Toast.makeText(this, R.string.onboarding_accessibility_required, Toast.LENGTH_LONG).show()
            showStep(4)
            return
        }

        val view = layoutInflater.inflate(R.layout.onboarding_step5, stepContainer, false)
        stepContainer.addView(view)
        stepView = view

        btnNext.visibility = View.VISIBLE

        cameraTestStartTime = System.currentTimeMillis()
        waitingForTrigger = true
        registerTriggerReceiver(view)
        setStep5IdleState(view)
    }

    private fun launchCameraTest(view: View) {
        cameraTestStartTime = System.currentTimeMillis()
        waitingForTrigger = true
        registerTriggerReceiver(view)
        CameraUtils.launchCamera(this)
        showStep5WaitingState(view)
    }

    private fun onResumeStep5() {
        val view = stepView ?: return
        showStep5WaitingState(view)
        Handler(Looper.getMainLooper()).postDelayed({
            checkTriggerResult(view)
        }, 3000)
    }

    private fun registerTriggerReceiver(view: View) {
        unregisterTriggerReceiver()
        triggerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Trigger was received — wait for the service to finish processing (MediaStore observe)
                Handler(Looper.getMainLooper()).postDelayed({
                    checkTriggerResult(view)
                }, 5000)
            }
        }
        try {
            val filter = IntentFilter(CameraAccessibilityService.ACTION_MESSAGE_RECEIVED)
            registerReceiver(triggerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun unregisterTriggerReceiver() {
        try { triggerReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) { /* ignore */ }
        triggerReceiver = null
    }

    private fun checkTriggerResult(view: View) {
        val prefs = getSharedPreferences("CameraClickPrefs", Context.MODE_PRIVATE)
        val lastTriggerTime = prefs.getLong(CameraAccessibilityService.PREF_LAST_TRIGGER_TIME, 0L)
        val lastTriggerSuccess = prefs.getBoolean(CameraAccessibilityService.PREF_LAST_TRIGGER_SUCCESS, false)

        if (lastTriggerTime > cameraTestStartTime) {
            waitingForTrigger = false
            unregisterTriggerReceiver()
            if (lastTriggerSuccess) {
                showStep5SuccessState(view)
            } else {
                showStep5FailureState(view)
            }
        } else if (waitingForTrigger) {
            // Still waiting — show idle state again with message
            showStep5WaitingState(view)
        }
    }

    private fun setStep5IdleState(view: View) {
        view.findViewById<LinearLayout>(R.id.idle_state)?.visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.waiting_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.success_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.failure_state)?.visibility = View.GONE
        btnNext.setText(R.string.btn_open_camera)
        btnNext.setOnClickListener { launchCameraTest(view) }
        btnSkip.visibility = View.VISIBLE
        btnSkip.setText(R.string.btn_skip)
        btnSkip.setOnClickListener { completeOnboarding() }
    }

    private fun showStep5WaitingState(view: View) {
        view.findViewById<LinearLayout>(R.id.idle_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.waiting_state)?.visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.success_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.failure_state)?.visibility = View.GONE
        btnNext.setText(R.string.btn_open_camera_again)
        btnNext.setOnClickListener { launchCameraTest(view) }
        btnSkip.visibility = View.VISIBLE
        btnSkip.setText(R.string.btn_skip)
        btnSkip.setOnClickListener { completeOnboarding() }
    }

    private fun showStep5SuccessState(view: View) {
        view.findViewById<LinearLayout>(R.id.idle_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.waiting_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.success_state)?.visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.failure_state)?.visibility = View.GONE
        btnNext.setText(R.string.btn_finish_setup)
        btnNext.setOnClickListener { completeOnboarding() }
        btnSkip.visibility = View.GONE
        AnalyticsUtils.logOnboardingStep(5, "test_success")
    }

    private fun showStep5FailureState(view: View) {
        view.findViewById<LinearLayout>(R.id.idle_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.waiting_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.success_state)?.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.failure_state)?.visibility = View.VISIBLE

        view.findViewById<TextView>(R.id.failure_message)?.text = getString(R.string.onboarding_step5_failure)

        view.findViewById<Button>(R.id.btn_configure_button)?.setOnClickListener {
            startActivity(Intent(this, ManualShutterButtonSelectionActivity::class.java))
        }

        btnNext.setText(R.string.btn_try_again)
        btnNext.setOnClickListener { launchCameraTest(view) }
        btnSkip.visibility = View.VISIBLE
        btnSkip.setText(R.string.btn_continue_anyway)
        btnSkip.setOnClickListener { completeOnboarding() }
        AnalyticsUtils.logOnboardingStep(5, "test_failed")
    }
}

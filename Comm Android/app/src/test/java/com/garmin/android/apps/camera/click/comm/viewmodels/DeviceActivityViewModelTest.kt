package com.garmin.android.apps.camera.click.comm.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.garmin.android.apps.camera.click.comm.CommConstants
import com.garmin.android.apps.camera.click.comm.detection.ButtonLocationRepository
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

/**
 * Unit tests for DeviceActivityViewModel
 *
 * Tests cover:
 * - Device initialization
 * - LiveData state updates
 * - Auto-launch camera preference
 * - ButtonLocationRepository integration
 * - Message sending logic
 * - App status management
 * - Review and developer note timing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceActivityViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockButtonLocationRepository: ButtonLocationRepository

    @Mock
    private lateinit var mockConnectIQ: ConnectIQ

    @Mock
    private lateinit var mockDevice: IQDevice

    @Mock
    private lateinit var mockApp: IQApp

    private lateinit var viewModel: DeviceActivityViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)

        // Mock application context
        whenever(mockApplication.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }

        // Mock device
        whenever(mockDevice.friendlyName).thenReturn("Garmin Venu")
        whenever(mockDevice.deviceIdentifier).thenReturn(12345)
        whenever(mockDevice.status).thenReturn(IQDevice.IQDeviceStatus.CONNECTED)

        viewModel = DeviceActivityViewModel(mockApplication, mockButtonLocationRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state`() {
        // Verify initial LiveData values
        assert(viewModel.deviceStatus.value == null)
        assert(viewModel.appStatus.value == null)
        assert(viewModel.autoLaunchEnabled.value == null)
        assert(viewModel.messageSendResult.value == null)
        assert(viewModel.showAccessibilityDialog.value == null)
        assert(viewModel.showAppNotInstalledDialog.value == null)
        assert(viewModel.showReviewPrompt.value == null)
        assert(viewModel.showDeveloperNote.value == null)
        assert(viewModel.buttonLocationInfo.value == null)
        assert(viewModel.toastMessage.value == null)
    }

    @Test
    fun `test initializeWithDevice sets device status and loads button info`() {
        // Given: Auto-launch is disabled in preferences
        whenever(mockSharedPreferences.getBoolean("auto_launch_camera", false)).thenReturn(false)

        // And: Button location repository has info
        val mockButtonInfo = ShutterButtonInfo(
            bounds = Rect(400, 900, 600, 1100),
            packageName = "com.android.camera",
            contentDescription = "Shutter"
        )
        whenever(mockButtonLocationRepository.getLastKnownButtonInfo()).thenReturn(mockButtonInfo)

        // When: Initializing with device
        viewModel.initializeWithDevice(mockDevice)

        // Then: Should set device status
        assert(viewModel.deviceStatus.value == IQDevice.IQDeviceStatus.CONNECTED)

        // And: Should set auto-launch state
        assert(viewModel.autoLaunchEnabled.value == false)

        // And: Should set button location info
        assert(viewModel.buttonLocationInfo.value == mockButtonInfo)

        // And: Should set app status to not open
        assert(viewModel.appStatus.value?.isOpen == false)
    }

    @Test
    fun `test initializeWithDevice loads auto-launch preference`() {
        // Given: Auto-launch is enabled in preferences
        whenever(mockSharedPreferences.getBoolean("auto_launch_camera", false)).thenReturn(true)

        // When: Initializing with device
        viewModel.initializeWithDevice(mockDevice)

        // Then: Should set auto-launch state to true
        assert(viewModel.autoLaunchEnabled.value == true)
    }

    @Test
    fun `test setAutoLaunchCamera saves preference and updates LiveData`() {
        // Given: ViewModel is initialized
        viewModel.initializeWithDevice(mockDevice)

        // When: Setting auto-launch to true
        viewModel.setAutoLaunchCamera(true)

        // Then: Should save to preferences
        verify(mockEditor).putBoolean("auto_launch_camera", true)
        verify(mockEditor).apply()

        // And: Should update LiveData
        assert(viewModel.autoLaunchEnabled.value == true)
    }

    @Test
    fun `test setAutoLaunchCamera with false disables auto-launch`() {
        // Given: ViewModel is initialized
        viewModel.initializeWithDevice(mockDevice)

        // When: Setting auto-launch to false
        viewModel.setAutoLaunchCamera(false)

        // Then: Should save to preferences
        verify(mockEditor).putBoolean("auto_launch_camera", false)
        verify(mockEditor).apply()

        // And: Should update LiveData
        assert(viewModel.autoLaunchEnabled.value == false)
    }

    @Test
    fun `test getCurrentDevice returns initialized device`() {
        // Given: Device is initialized
        viewModel.initializeWithDevice(mockDevice)

        // When: Getting current device
        val result = viewModel.getCurrentDevice()

        // Then: Should return the device
        assert(result == mockDevice)
    }

    @Test
    fun `test getCurrentDevice returns null before initialization`() {
        // When: Getting current device before initialization
        val result = viewModel.getCurrentDevice()

        // Then: Should return null
        assert(result == null)
    }

    @Test
    fun `test refreshButtonLocationInfo updates button info`() {
        // Given: ViewModel is initialized
        viewModel.initializeWithDevice(mockDevice)

        // And: Button location repository has new info
        val updatedButtonInfo = ShutterButtonInfo(
            bounds = Rect(500, 1000, 700, 1200),
            packageName = "com.samsung.camera",
            contentDescription = "Capture"
        )
        whenever(mockButtonLocationRepository.getLastKnownButtonInfo()).thenReturn(updatedButtonInfo)

        // When: Refreshing button location info
        viewModel.refreshButtonLocationInfo()

        // Then: Should update LiveData with new info
        assert(viewModel.buttonLocationInfo.value == updatedButtonInfo)
        assert(viewModel.buttonLocationInfo.value?.packageName == "com.samsung.camera")
    }

    @Test
    fun `test onReviewPromptShown clears review prompt flag`() {
        // When: Marking review prompt as shown
        viewModel.onReviewPromptShown()

        // Then: Should clear the flag
        assert(viewModel.showReviewPrompt.value == false)
    }

    @Test
    fun `test onDeveloperNoteShown clears developer note event`() {
        // When: Marking developer note as shown
        viewModel.onDeveloperNoteShown()

        // Then: Should clear the event
        assert(viewModel.showDeveloperNote.value == null)
    }

    @Test
    fun `test onAppNotInstalledDialogShown clears dialog flag`() {
        // When: Marking app not installed dialog as shown
        viewModel.onAppNotInstalledDialogShown()

        // Then: Should clear the flag
        assert(viewModel.showAppNotInstalledDialog.value == false)
    }

    @Test
    fun `test checkWatchAppStatus triggers MessageService start`() {
        // Given: Device is initialized
        viewModel.initializeWithDevice(mockDevice)

        // When: Checking watch app status
        viewModel.checkWatchAppStatus()

        // Then: Should trigger MessageService start
        assert(viewModel.startMessageService.value == mockDevice)
    }

    @Test
    fun `test MessageSendResult data class`() {
        // Test the data class
        val result = DeviceActivityViewModel.MessageSendResult(
            success = true,
            status = "SUCCESS",
            message = "Message sent successfully"
        )

        assert(result.success == true)
        assert(result.status == "SUCCESS")
        assert(result.message == "Message sent successfully")
    }

    @Test
    fun `test AppStatus data class`() {
        // Test the data class
        val status = DeviceActivityViewModel.AppStatus(
            isOpen = true,
            displayText = "App Already Open"
        )

        assert(status.isOpen == true)
        assert(status.displayText == "App Already Open")
    }

    @Test
    fun `test DeveloperNoteEvent data class`() {
        // Test the data class
        val event = DeviceActivityViewModel.DeveloperNoteEvent(
            version = 2,
            isAutomatic = false
        )

        assert(event.version == 2)
        assert(event.isAutomatic == false)
    }

    @Test
    fun `test updateRateAppMenuVisibility based on days since install`() {
        // This would require mocking InAppReviewUtils.getDaysSinceFirstInstall
        // Simplified test to verify the method exists and doesn't crash

        // When: Updating rate app menu visibility
        viewModel.updateRateAppMenuVisibility()

        // Then: Should complete without error
        // Full testing would require dependency injection of InAppReviewUtils
    }

    @Test
    fun `test checkTimingTriggers completes without error`() = runTest {
        // This test would require mocking static methods
        // Simplified version:

        // When: Checking timing triggers
        viewModel.checkTimingTriggers(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: The coroutine completes without error
        // Full testing would require dependency injection
    }

    @Test
    fun `test trackAppLaunch completes without error`() {
        // This test verifies the method exists and doesn't crash

        // When: Tracking app launch
        viewModel.trackAppLaunch(mockContext)

        // Then: Should complete without error
        // Full testing would require mocking InAppReviewUtils
    }

    @Test
    fun `test openWatchApp shows toast message`() {
        // Given: Device is initialized
        viewModel.initializeWithDevice(mockDevice)

        // When: Opening watch app
        viewModel.openWatchApp()

        // Then: Should show "Prompting watch..." toast
        assert(viewModel.toastMessage.value == "Prompting watch...")
    }
}

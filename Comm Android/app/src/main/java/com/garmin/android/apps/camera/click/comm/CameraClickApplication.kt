package com.garmin.android.apps.camera.click.comm

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.garmin.android.apps.camera.click.comm.detection.ButtonLocationRepository
import com.garmin.android.apps.camera.click.comm.utils.AnalyticsUtils
import com.garmin.android.apps.camera.click.comm.utils.CameraAppCandidateStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

private const val TAG = "CameraClickApplication"

@HiltAndroidApp
class CameraClickApplication : Application(), Application.ActivityLifecycleCallbacks {

    @Inject
    lateinit var buttonLocationRepository: ButtonLocationRepository

    private lateinit var analytics: FirebaseAnalytics

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase and Crashlytics early for consistent behavior across processes
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseCrashlytics.getInstance().log("Application onCreate")

        analytics = Firebase.analytics
        analytics.setAnalyticsCollectionEnabled(true)

        // Initialize project Analytics wrapper with the concrete FirebaseAnalytics instance
        AnalyticsUtils.initialize(this, analytics)

        // Initialize CameraAppCandidateStore with ButtonLocationRepository
        CameraAppCandidateStore.initialize(buttonLocationRepository)

        // Register lifecycle callbacks to auto-log screen views on resume
        registerActivityLifecycleCallbacks(this)
        Log.d(TAG, "Application initialized and ActivityLifecycleCallbacks registered")
    }

    override fun onActivityResumed(activity: Activity) {
        try {
            val bundle = android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, activity.javaClass.simpleName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, activity.javaClass.name)
            }
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(t)
        }
    }

    // No-op for other callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

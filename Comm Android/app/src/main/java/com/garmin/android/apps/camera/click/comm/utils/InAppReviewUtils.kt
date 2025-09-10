package com.garmin.android.apps.camera.click.comm.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode

/**
 * Simple utility for handling Google Play In-App Reviews
 * Follows best practices for when and how to show review prompts
 */
object InAppReviewUtils {
    
    private const val PREFS_NAME = "in_app_review_prefs"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LAST_REVIEW_REQUEST = "last_review_request"
    private const val KEY_REVIEW_COMPLETED = "review_completed"
    
    // Best practice thresholds
    private const val MIN_LAUNCHES_BEFORE_REVIEW = 5
    private const val DAYS_BETWEEN_REVIEW_REQUESTS = 30
    
    /**
     * Call this method when your app launches to track usage
     */
    fun trackAppLaunch(context: Context) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        prefs.edit().putInt(KEY_LAUNCH_COUNT, currentCount + 1).apply()
    }
    
    /**
     * Check if we should show the review prompt based on best practices
     */
    fun shouldShowReviewPrompt(context: Context): Boolean {
        val prefs = getPrefs(context)
        
        // Don't show if user already completed a review
        if (prefs.getBoolean(KEY_REVIEW_COMPLETED, false)) {
            return false
        }
        
        // Check minimum launch count
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        if (launchCount < MIN_LAUNCHES_BEFORE_REVIEW) {
            return false
        }
        
        // Check time since last request
        val lastRequest = prefs.getLong(KEY_LAST_REVIEW_REQUEST, 0)
        val daysSinceLastRequest = (System.currentTimeMillis() - lastRequest) / (1000 * 60 * 60 * 24)
        
        return daysSinceLastRequest >= DAYS_BETWEEN_REVIEW_REQUESTS
    }
    
    /**
     * Launch the in-app review flow
     * Call this at a natural break in your app flow (after completing an action)
     */
    fun launchInAppReview(activity: Activity, onComplete: ((Boolean) -> Unit)? = null) {
        val reviewManager = ReviewManagerFactory.create(activity)
        val request = reviewManager.requestReviewFlow()
        
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                
                flow.addOnCompleteListener { flowTask ->
                    // Update preferences
                    val prefs = getPrefs(activity)
                    prefs.edit()
                        .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
                        .putBoolean(KEY_REVIEW_COMPLETED, true)
                        .apply()
                    
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not. Continue your app's normal flow.
                    onComplete?.invoke(true)
                }
            } else {
                // There was some problem, log it but don't tell the user
                val errorCode = (task.exception as? com.google.android.play.core.review.ReviewException)?.errorCode
                when (errorCode) {
                    ReviewErrorCode.NO_ERROR -> { /* Success */ }
                    ReviewErrorCode.PLAY_STORE_NOT_FOUND -> { /* Play Store not available */ }
                    ReviewErrorCode.INTERNAL_ERROR -> { /* Internal error */ }
                    else -> { /* Other error */ }
                }
                
                // Continue your app's normal flow
                onComplete?.invoke(false)
            }
        }
    }
    
    /**
     * Reset review state (useful for testing)
     */
    fun resetReviewState(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    
    /**
     * Get current launch count (useful for debugging)
     */
    fun getLaunchCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAUNCH_COUNT, 0)
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
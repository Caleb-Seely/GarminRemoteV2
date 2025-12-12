package com.garmin.android.apps.camera.click.comm.helpers

import android.content.Context
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.garmin.android.apps.camera.click.comm.R

private const val TAG = "DeviceActivityAnimation"

/**
 * DeviceActivityAnimationHelper - Centralized animation management
 *
 * This class manages all animations for DeviceActivity, providing consistent
 * animation behavior across all interactive elements.
 *
 * Features:
 * - Press/release button animations
 * - Enter animations for cards
 * - Consistent animation timing
 * - Error handling for animation failures
 *
 * Benefits:
 * - Eliminates code duplication
 * - Consistent user experience
 * - Easy to modify animation behavior globally
 * - Testable animation logic
 */
class DeviceActivityAnimationHelper(private val context: Context) {

    /**
     * Animate a card click with press and release animations
     *
     * Plays a premium press animation, then a release animation, then executes the action.
     * This creates a satisfying tactile feel for button interactions.
     *
     * @param view The view to animate
     * @param action The action to execute after animations complete
     */
    fun animateCardClick(view: View, action: () -> Unit) {
        val pressAnimation = AnimationUtils.loadAnimation(context, R.anim.premium_button_press)
        val releaseAnimation = AnimationUtils.loadAnimation(context, R.anim.premium_button_release)

        view.startAnimation(pressAnimation)
        pressAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                view.startAnimation(releaseAnimation)
                action()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }

    /**
     * Animate camera button launch
     *
     * Similar to card click but designed specifically for camera launch buttons.
     * Animation plays non-blocking to not delay camera launch.
     *
     * @param view The view to animate
     * @param action The action to execute (typically camera launch)
     */
    fun animateCameraLaunch(view: View?, action: () -> Unit) {
        // Execute action immediately (don't block on animation)
        action()

        // Play animation non-blocking
        view?.let {
            try {
                val pressAnimation = AnimationUtils.loadAnimation(context, R.anim.premium_button_press)
                val releaseAnimation = AnimationUtils.loadAnimation(context, R.anim.premium_button_release)

                it.startAnimation(pressAnimation)
                pressAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}

                    override fun onAnimationEnd(animation: Animation?) {
                        it.startAnimation(releaseAnimation)
                    }

                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            } catch (e: Exception) {
                // If animations fail, don't let it affect camera launch
                Log.w(TAG, "Animation failed but camera should still launch", e)
            }
        }
    }

    /**
     * Setup enter animations for all main UI sections
     *
     * Makes all views visible with their alpha set to 1.0 and visibility set to VISIBLE.
     * This replaced more complex fade-in animations that were causing issues.
     *
     * @param views The views helper containing all view references
     */
    fun setupEnterAnimations(views: DeviceActivityViews) {
        try {
            Log.d(TAG, "Initializing premium animations")

            // Ensure all views are visible first
            views.deviceInfoSection.visibility = View.VISIBLE
            views.deviceInfoSection.alpha = 1f

            views.mainCard.visibility = View.VISIBLE
            views.mainCard.alpha = 1f

            views.autoLaunchSection.visibility = View.VISIBLE
            views.autoLaunchSection.alpha = 1f

            views.cameraButton.visibility = View.VISIBLE
            views.cameraButton.alpha = 1f

            Log.d(TAG, "All views set to visible")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up animations", e)
            // Fallback: make sure all views are visible
            views.deviceInfoSection.visibility = View.VISIBLE
            views.deviceInfoSection.alpha = 1f
            views.mainCard.visibility = View.VISIBLE
            views.mainCard.alpha = 1f
            views.autoLaunchSection.visibility = View.VISIBLE
            views.autoLaunchSection.alpha = 1f
            views.cameraButton.visibility = View.VISIBLE
            views.cameraButton.alpha = 1f
        }
    }

    /**
     * Animate the "Open App" button
     *
     * Specialized animation for the open app button that executes the action
     * after the press animation completes.
     *
     * @param view The open app button view
     * @param action The action to execute after animation
     */
    fun animateOpenAppButton(view: View, action: () -> Unit) {
        val pressAnimation = AnimationUtils.loadAnimation(context, R.anim.premium_button_press)
        val releaseAnimation = AnimationUtils.loadAnimation(context, R.anim.premium_button_release)

        view.startAnimation(pressAnimation)
        pressAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                view.startAnimation(releaseAnimation)
                action()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }
}

package com.garmin.android.apps.camera.click.comm.service.handlers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.garmin.android.apps.camera.click.comm.service.MessageService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WatchCommunicationHandler"

/**
 * Handles communication with the Garmin watch.
 * This class is responsible for sending messages back to the watch
 * through the MessageService with retry logic and error recovery.
 */
@Singleton
class WatchCommunicationHandler @Inject constructor(
    private val coroutineScope: CoroutineScope
) {

    /**
     * Sends a message to the watch (blocking version for backward compatibility).
     *
     * @param context The application context
     * @param messageType The type of message to send (e.g., SHUTTER_SUCCESS, SHUTTER_FAILED)
     * @param details Optional details about the message
     * @param maxRetries Maximum number of retry attempts (default: 2)
     * @return true if the message was sent successfully, false otherwise
     */
    fun sendMessageToWatch(
        context: Context,
        messageType: String,
        details: String? = null,
        maxRetries: Int = 2
    ): Boolean {
        return runBlocking {
            sendMessageToWatchAsync(context, messageType, details, maxRetries)
        }
    }

    /**
     * Async version: Sends a message to the watch with non-blocking retry logic.
     *
     * @param context The application context
     * @param messageType The type of message to send (e.g., SHUTTER_SUCCESS, SHUTTER_FAILED)
     * @param details Optional details about the message
     * @param maxRetries Maximum number of retry attempts (default: 2)
     * @return true if the message was sent successfully, false otherwise
     */
    suspend fun sendMessageToWatchAsync(
        context: Context,
        messageType: String,
        details: String? = null,
        maxRetries: Int = 2
    ): Boolean {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= maxRetries) {
            try {
                Log.d(TAG, "Sending message to watch: type=$messageType, details=$details (attempt ${attempt + 1}/${maxRetries + 1})")

                val intent = Intent(context, MessageService::class.java).apply {
                    action = "SEND_MESSAGE"
                    putExtra("message_type", messageType)
                    putExtra("message_details", details)
                }

                context.startService(intent)

                if (attempt > 0) {
                    Log.d(TAG, "Message sent successfully on retry attempt $attempt")
                    FirebaseCrashlytics.getInstance().log("Watch message sent on retry $attempt: $messageType")
                } else {
                    Log.d(TAG, "Message sent successfully")
                }
                return true

            } catch (e: SecurityException) {
                lastException = e
                Log.e(TAG, "Security exception sending message to watch (attempt $attempt): $messageType", e)
                // Don't retry security exceptions
                FirebaseCrashlytics.getInstance().recordException(e)
                return false

            } catch (e: IllegalStateException) {
                lastException = e
                Log.e(TAG, "Illegal state sending message to watch (attempt $attempt): $messageType", e)

                // Retry with exponential backoff for state issues
                if (attempt < maxRetries) {
                    val delayMs = (100L * (1 shl attempt)) // 100ms, 200ms
                    delay(delayMs) // NON-BLOCKING delay
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Watch message send cancelled")
                throw e // Re-throw cancellation

            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Error sending message to watch (attempt $attempt): type=$messageType", e)

                // Retry with exponential backoff for other exceptions
                if (attempt < maxRetries) {
                    val delayMs = (100L * (1 shl attempt))
                    delay(delayMs) // NON-BLOCKING delay
                }
            }
            attempt++
        }

        // All retries exhausted
        Log.e(TAG, "Failed to send message to watch after $maxRetries retries: $messageType")
        if (lastException != null) {
            FirebaseCrashlytics.getInstance().recordException(lastException)
        }
        return false
    }

    /**
     * Sends a success message to the watch (blocking version).
     *
     * @param context The application context
     * @param details Optional success details
     */
    fun sendSuccessMessage(context: Context, details: String = "Success!") {
        sendMessageToWatch(context, MessageService.MESSAGE_TYPE_SHUTTER_SUCCESS, details)
    }

    /**
     * Sends a failure message to the watch (blocking version).
     *
     * @param context The application context
     * @param reason The reason for failure
     */
    fun sendFailureMessage(context: Context, reason: String) {
        sendMessageToWatch(context, MessageService.MESSAGE_TYPE_SHUTTER_FAILED, reason)
    }

    /**
     * Async version: Sends a success message to the watch.
     *
     * @param context The application context
     * @param details Optional success details
     */
    suspend fun sendSuccessMessageAsync(context: Context, details: String = "Success!") {
        sendMessageToWatchAsync(context, MessageService.MESSAGE_TYPE_SHUTTER_SUCCESS, details)
    }

    /**
     * Async version: Sends a failure message to the watch.
     *
     * @param context The application context
     * @param reason The reason for failure
     */
    suspend fun sendFailureMessageAsync(context: Context, reason: String) {
        sendMessageToWatchAsync(context, MessageService.MESSAGE_TYPE_SHUTTER_FAILED, reason)
    }
}

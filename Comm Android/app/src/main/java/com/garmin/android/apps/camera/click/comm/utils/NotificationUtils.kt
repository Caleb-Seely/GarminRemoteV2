package com.garmin.android.apps.camera.click.comm.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.activities.DeviceActivity
import com.garmin.android.apps.camera.click.comm.service.MessageService
import com.garmin.android.connectiq.IQDevice

/**
 * Utility class to handle all notification-related functionality for the Garmin Camera Remote app.
 */
object NotificationUtils {
    private const val TAG = "NotificationUtils"
    private const val NOTIFICATION_CHANNEL_ID = "GarminMessageChannel"
    const val NOTIFICATION_ID = 1001

    /**
     * Creates and initializes the notification channel for Android O and above.
     * @param context The context to use for creating the channel
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel")
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Garmin Message Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps connection to Garmin device active"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created with ID: $NOTIFICATION_CHANNEL_ID")
            
            // Verify channel was created
            val createdChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            Log.d(TAG, "Verified channel exists with importance: ${createdChannel?.importance}")
        }
    }

    /**
     * Creates a foreground service notification for the Garmin Camera Remote app.
     * @param context The context to use for creating the notification
     * @param device The connected Garmin device
     * @return A non-dismissible foreground service notification
     */
    fun createForegroundNotification(context: Context, device: IQDevice): Notification {
        // Create an intent for the activity
        val activityIntent = Intent(context, DeviceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Garmin Camera Remote Active")
            .setContentText("Connected to ${device.friendlyName}")
            .setSmallIcon(R.drawable.ic_launcher)
            .setColor(Color.BLUE)
            .setColorized(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingActivityIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(true)
            .build()

        // Make the notification non-dismissible
        notification.flags = notification.flags or (
            Notification.FLAG_NO_CLEAR or 
            Notification.FLAG_ONGOING_EVENT or 
            Notification.FLAG_FOREGROUND_SERVICE
        )

        return notification
    }

    /**
     * Starts the message service with a foreground notification.
     * @param context The context to use for starting the service
     * @param device The connected Garmin device
     * @param appId The ID of the app to receive messages from
     */
    fun startService(context: Context, device: IQDevice, appId: String) {
        val intent = MessageService.createIntent(context, device, appId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Log.d(TAG, "Started message service")
    }

    /**
     * Stops the message service and removes the notification.
     * @param context The context to use for stopping the service
     * @param device The connected Garmin device
     * @param appId The ID of the app
     */
    fun stopService(context: Context, device: IQDevice, appId: String) {
        val intent = MessageService.createIntent(context, device, appId)
        context.stopService(intent)
        Log.d(TAG, "Stopped message service")
    }
} 
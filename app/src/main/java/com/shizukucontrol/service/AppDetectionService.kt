package com.shizukucontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.shizukucontrol.MainActivity

/**
 * AccessibilityService that detects foreground app switches in real-time.
 * Sends package name changes via a global callback.
 */
class AppDetectionService : AccessibilityService() {

    companion object {
        private const val TAG = "AppDetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "app_detection"
        private const val NOTIFICATION_ID = 1001

        /** Global callback for foreground app changes. Set from MainViewModel. */
        var onForegroundAppChanged: ((String?) -> Unit)? = null

        /** Whether the service is currently running. */
        var isRunning: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "AccessibilityService connected")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 0 // Real-time: no batching delay
        }

        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrEmpty()) {
                Log.d(TAG, "Foreground app changed: $packageName")
                onForegroundAppChanged?.invoke(packageName)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "AccessibilityService destroyed")
    }

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "传感器控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "前台应用检测服务运行中"
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("传感器控制")
            .setContentText("正在监测前台应用切换...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}

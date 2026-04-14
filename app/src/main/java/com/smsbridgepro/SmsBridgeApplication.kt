package com.smsbridgepro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * SmsBridgeApplication
 * First thing that runs on app start.
 * Creates the notification channel required by Android 8+ for foreground services.
 */
class SmsBridgeApplication : Application() {
    companion object {
        const val CHANNEL_ID   = "sms_gateway_channel"
        const val CHANNEL_NAME = "SMS Gateway Server"
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the SMS Bridge server running in the background"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

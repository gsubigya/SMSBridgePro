package com.smsbridgepro.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsbridgepro.SmsBridgeApplication.Companion.CHANNEL_ID
import com.smsbridgepro.model.SessionCredentials
import com.smsbridgepro.network.KtorServerEngine
import com.smsbridgepro.network.SmsDispatcher
import com.smsbridgepro.security.SecureIdentityGenerator
import com.smsbridgepro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SmsGatewayService
 * ════════════════════════════════════════════════════════════
 * Foreground Service that keeps the Ktor/Netty HTTP server alive
 * even when the app is backgrounded.
 *
 * Why foreground?
 *   Android kills background services to save battery. A foreground
 *   service shows a persistent notification, signalling to Android
 *   that this process is doing important work and should stay alive.
 *
 * Lifecycle:
 *   MainActivity → startForegroundService() → onStartCommand()
 *     → generate credentials → start Ktor server
 *   MainActivity → stopService() → onDestroy() → stop Ktor server
 *
 * Binding:
 *   MainActivity binds to this service via LocalBinder to read
 *   live credentials and server state.
 * ════════════════════════════════════════════════════════════
 */
class SmsGatewayService : Service() {

    companion object {
        private const val TAG      = "SmsGatewayService"
        private const val NOTIF_ID = 1001
        const val SERVER_PORT      = 8080
        const val ACTION_STOP      = "com.smsbridgepro.STOP_SERVER"
    }

    // Coroutine scope — lives as long as the service
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var credentials : SessionCredentials
    private lateinit var smsDispatcher: SmsDispatcher
    private lateinit var ktorEngine  : KtorServerEngine

    // ── Binder allows MainActivity to access this service directly ──
    inner class LocalBinder : Binder() {
        fun getService(): SmsGatewayService = this@SmsGatewayService
    }
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        smsDispatcher = SmsDispatcher(applicationContext)
        Log.d(TAG, "Service created")
    }

    /**
     * onStartCommand — called each time startForegroundService() fires.
     * Generates new credentials and starts the Ktor server.
     * Returns START_STICKY so Android restarts the service if killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startServerForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (::ktorEngine.isInitialized) ktorEngine.stop()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    // ── Public API (via LocalBinder) ────────────────────────────
    fun getCredentials(): SessionCredentials? = if (::credentials.isInitialized) credentials else null
    fun isServerRunning(): Boolean = ::ktorEngine.isInitialized && ktorEngine.isRunning()

    // ── Private ──────────────────────────────────────────────────
    private fun startServerForeground() {
        // Must call startForeground() within 5 s of onStartCommand on Android 8+
        startForeground(NOTIF_ID, buildNotification("Server starting…"))

        // Spec: "Every time a server instance is started, SecureIdentityGenerator triggers"
        credentials = SecureIdentityGenerator.generate()
        Log.d(TAG, "Credentials generated: user=${credentials.username}")

        scope.launch {
            try {
                ktorEngine = KtorServerEngine(credentials, smsDispatcher)
                ktorEngine.start(SERVER_PORT)
                updateNotification("Active on port $SERVER_PORT")
                Log.d(TAG, "Server running on :$SERVER_PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                updateNotification("Server error — tap to open")
            }
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, SmsGatewayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Bridge Pro")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }
}

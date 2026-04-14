package com.smsbridgepro.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.NetworkInterface

/**
 * NetworkUtils
 * ════════════════════════════════════════════════════════════
 * Helpers for:
 *   1. getLocalIpAddress() — device LAN IP for LOCAL mode
 *   2. getNgrokTunnelUrl() — public URL from Ngrok agent API for GLOBAL mode
 * ════════════════════════════════════════════════════════════
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"
    private val http = OkHttpClient()

    /**
     * Returns the device's Wi-Fi IPv4 address (e.g. "192.168.1.42"),
     * or null if not on Wi-Fi / no address found.
     */
    @Suppress("DEPRECATION")
    fun getLocalIpAddress(ctx: Context): String? = try {
        val wm  = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip  = wm.connectionInfo?.ipAddress ?: 0
        if (ip != 0) {
            // WifiManager returns little-endian int → convert to dotted decimal
            "%d.%d.%d.%d".format(ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
                .also { Log.d(TAG, "LAN IP: $it") }
        } else {
            // Fallback: iterate NetworkInterfaces
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && !it.hostAddress.contains(':') }
                ?.hostAddress
        }
    } catch (e: Exception) { Log.e(TAG, "getLocalIpAddress error", e); null }

    /**
     * Queries the local Ngrok agent API (always on 127.0.0.1:4040) to
     * find the active public HTTPS tunnel URL.
     * Must be called from a coroutine — uses IO dispatcher.
     * Returns null if Ngrok is not running or no tunnel found.
     */
    suspend fun getNgrokTunnelUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val req  = Request.Builder().url("http://127.0.0.1:4040/api/tunnels").build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val json = JSONObject(resp.body?.string() ?: return@withContext null)
            val arr  = json.getJSONArray("tunnels")
            for (i in 0 until arr.length()) {
                val url = arr.getJSONObject(i).optString("public_url", "")
                if (url.startsWith("https://")) { Log.d(TAG, "Ngrok: $url"); return@withContext url }
            }
            null
        } catch (e: Exception) { Log.d(TAG, "Ngrok not available: ${e.message}"); null }
    }
}

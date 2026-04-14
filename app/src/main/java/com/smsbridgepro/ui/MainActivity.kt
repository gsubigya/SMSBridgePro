package com.smsbridgepro.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smsbridgepro.R
import com.smsbridgepro.data.SmsLogRepository
import com.smsbridgepro.databinding.ActivityMainBinding
import com.smsbridgepro.model.GatewayMode
import com.smsbridgepro.network.NetworkUtils
import com.smsbridgepro.service.SmsGatewayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity
 * ════════════════════════════════════════════════════════════
 * The single Activity for SMS Bridge Pro.
 *
 * Responsibilities:
 *   • Request SEND_SMS permission at runtime
 *   • Start / stop SmsGatewayService via toggle switch
 *   • Bind to the service to read live credentials + state
 *   • Display credentials in tap-to-reveal cards
 *   • Show correct server URL in LOCAL or GLOBAL mode
 *   • Display SMS logs in a dedicated tab
 * ════════════════════════════════════════════════════════════
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var svc: SmsGatewayService? = null
    private var bound = false
    private var mode  = GatewayMode.LOCAL
    private val logAdapter = SmsLogAdapter()

    // ── Service connection ──────────────────────────────────────
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            svc   = (b as? SmsGatewayService.LocalBinder)?.getService()
            bound = true
            refreshCreds()
        }
        override fun onServiceDisconnected(n: ComponentName?) { svc = null; bound = false }
    }

    // ── Runtime permission launcher ─────────────────────────────
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.SEND_SMS] == true) startServer()
        else {
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_LONG).show()
            binding.serverToggle.isChecked = false
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupMainTabs()
        setupModeTabs()
        setupToggle()
        setupCredCards()
        setupLogsList()

        binding.btnCopyUrl.setOnClickListener {
            val url = binding.tvServerUrl.text.toString()
            if (url.isNotBlank() && url != getString(R.string.url_placeholder)) copy(url, "Server URL")
        }

        // Observe logs
        lifecycleScope.launch {
            SmsLogRepository.logs.collectLatest { logs ->
                logAdapter.submitList(logs)
            }
        }
    }

    override fun onStart() { super.onStart(); tryBind() }
    override fun onStop()  {
        super.onStop()
        if (bound) { unbindService(conn); bound = false }
    }

    // ── Setup helpers ───────────────────────────────────────────
    private fun setupMainTabs() {
        binding.btnTabServer.isSelected = true
        binding.btnTabServer.setOnClickListener {
            binding.btnTabServer.isSelected = true
            binding.btnTabLogs.isSelected = false
            binding.layoutServerContent.visibility = View.VISIBLE
            binding.layoutLogsContent.visibility = View.GONE
        }
        binding.btnTabLogs.setOnClickListener {
            binding.btnTabServer.isSelected = false
            binding.btnTabLogs.isSelected = true
            binding.layoutServerContent.visibility = View.GONE
            binding.layoutLogsContent.visibility = View.VISIBLE
        }
    }

    private fun setupModeTabs() {
        binding.btnModeLocal.isSelected = true
        binding.btnModeLocal.setOnClickListener {
            mode = GatewayMode.LOCAL
            binding.btnModeLocal.isSelected  = true
            binding.btnModeGlobal.isSelected = false
            updateUrl()
        }
        binding.btnModeGlobal.setOnClickListener {
            mode = GatewayMode.GLOBAL
            binding.btnModeLocal.isSelected  = false
            binding.btnModeGlobal.isSelected = true
            updateUrl()
        }
    }

    private fun setupToggle() {
        binding.serverToggle.setOnCheckedChangeListener { _, on ->
            if (on) checkPermAndStart() else stopServer()
        }
    }

    private fun setupCredCards() {
        // Tap each card → copy its credential value to clipboard
        binding.cardUsername.setOnClickListener { copy(binding.tvUsername.text.toString(), "SMS-Username") }
        binding.cardPassword.setOnClickListener { copy(binding.tvPassword.text.toString(), "SMS-Password") }
        binding.cardXHeader.setOnClickListener  { copy(binding.tvXHeader.text.toString(),  "X-SMS-Auth-Key") }
    }

    private fun setupLogsList() {
        binding.rvSmsLogs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }
    }

    // ── Server start / stop ─────────────────────────────────────
    private fun checkPermAndStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                      PackageManager.PERMISSION_GRANTED
        if (granted) startServer() else permLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
    }

    private fun startServer() {
        val intent = Intent(this, SmsGatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        tryBind()
        showStarting()
        // Give Ktor ~1.5 s to fully initialise, then refresh the UI
        lifecycleScope.launch {
            delay(1500)
            refreshCreds()
            updateUrl()
        }
    }

    private fun stopServer() {
        stopService(Intent(this, SmsGatewayService::class.java))
        showStopped()
    }

    private fun tryBind() {
        bindService(Intent(this, SmsGatewayService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    // ── UI state ────────────────────────────────────────────────
    private fun showStarting() {
        binding.tvServerStatus.text = "STARTING…"
        binding.tvServerStatus.setTextColor(getColor(R.color.accent_cyan))
        binding.credentialsGroup.visibility = View.GONE
    }

    private fun showStopped() {
        binding.tvServerStatus.text = "OFFLINE"
        binding.tvServerStatus.setTextColor(getColor(R.color.text_secondary))
        binding.credentialsGroup.visibility = View.GONE
        binding.tvServerUrl.text = getString(R.string.url_placeholder)
    }

    private fun refreshCreds() {
        val c = svc?.getCredentials() ?: return
        binding.tvUsername.text = c.username
        binding.tvPassword.text = c.password
        binding.tvXHeader.text  = c.xAuthHeader
        binding.credentialsGroup.visibility = View.VISIBLE
        binding.tvServerStatus.text = "ACTIVE"
        binding.tvServerStatus.setTextColor(getColor(R.color.accent_cyan))
    }

    private fun updateUrl() {
        if (svc?.isServerRunning() != true) return
        when (mode) {
            GatewayMode.LOCAL -> {
                val ip = NetworkUtils.getLocalIpAddress(this)
                binding.tvServerUrl.text =
                    if (ip != null) "http://$ip:${SmsGatewayService.SERVER_PORT}"
                    else "Server running (no Wi-Fi IP found)"
            }
            GatewayMode.GLOBAL -> {
                binding.tvServerUrl.text = "Checking Ngrok…"
                lifecycleScope.launch {
                    binding.tvServerUrl.text = NetworkUtils.getNgrokTunnelUrl()
                        ?: "Ngrok not detected.\nRun: ngrok http ${SmsGatewayService.SERVER_PORT}"
                }
            }
        }
    }

    // ── Clipboard ───────────────────────────────────────────────
    private fun copy(text: String, label: String) {
        if (text.isBlank()) return
        val cb   = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label copied!", Toast.LENGTH_SHORT).show()
    }
}

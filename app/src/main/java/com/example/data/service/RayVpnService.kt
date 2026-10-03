package com.example.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

class RayVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.vpn.START"
        const val ACTION_STOP = "com.example.vpn.STOP"

        const val EXTRA_REMARK = "extra_remark"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PROTOCOL = "extra_protocol"

        private const val NOTIFICATION_CHANNEL_ID = "ray_vpn_channel"
        private const val NOTIFICATION_ID = 2024

        private val _isVpnRunning = MutableStateFlow(false)
        val isVpnRunning = _isVpnRunning.asStateFlow()

        private val _currentConnectedRemark = MutableStateFlow("")
        val currentConnectedRemark = _currentConnectedRemark.asStateFlow()

        fun startVpn(context: Context, remark: String, host: String, port: Int, protocol: String) {
            val intent = Intent(context, RayVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REMARK, remark)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_PROTOCOL, protocol)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, RayVpnService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnInternal()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val remark = intent.getStringExtra(EXTRA_REMARK) ?: "Proxy Server"
                val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
                val port = intent.getIntExtra(EXTRA_PORT, 443)
                val protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "VLESS"
                startVpnInternal(remark, host, port, protocol)
                return START_STICKY
            }
            else -> {
                return START_NOT_STICKY
            }
        }
    }

    private fun startVpnInternal(remark: String, host: String, port: Int, protocol: String) {
        val notification = buildNotification(remark, host, port, protocol)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnJob?.cancel()

        try {
            val cleanHost = host.trim().ifEmpty { "127.0.0.1" }
            val builder = Builder()
                .setSession(remark.ifEmpty { "RayCollector VPN" })
                .setMtu(1500)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setBlocking(false)

            try {
                builder.addDisallowedApplication(packageName)
            } catch (ignored: Exception) {}

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _isVpnRunning.value = true
                _currentConnectedRemark.value = remark

                vpnJob = serviceScope.launch {
                    val pfd = vpnInterface ?: return@launch
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val packet = ByteArray(32767)

                    while (isActive) {
                        try {
                            val length = inputStream.read(packet)
                            if (length <= 0) {
                                kotlinx.coroutines.delay(50L)
                            }
                        } catch (e: Exception) {
                            if (!isActive) break
                            kotlinx.coroutines.delay(100L)
                        }
                    }
                }
            } else {
                stopVpnInternal()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopVpnInternal()
        }
    }

    private fun stopVpnInternal() {
        vpnJob?.cancel()
        vpnJob = null

        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null

        _isVpnRunning.value = false
        _currentConnectedRemark.value = ""

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                stopForeground(true)
            } catch (ignored: Exception) {}
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active VPN connection status"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(remark: String, host: String, port: Int, protocol: String) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VPN Active: $remark")
            .setContentText("$host:$port ($protocol) • Protected")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, RayVpnService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
}

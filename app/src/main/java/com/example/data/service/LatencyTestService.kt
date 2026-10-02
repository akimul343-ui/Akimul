package com.example.data.service

import com.example.data.model.ProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class PingMethod(val label: String, val description: String) {
    TCP("TCP", "Tests port connection via TCP handshake"),
    ICMP("ICMP", "Tests host reachability via ICMP/Echo")
}

data class PingResult(
    val latencyMs: Long,
    val method: PingMethod,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class LatencyTestService {

    /**
     * Tests latency to an endpoint using the selected protocol (TCP handshake or ICMP isReachable).
     */
    suspend fun testLatency(
        host: String,
        port: Int,
        method: PingMethod = PingMethod.TCP,
        timeoutMs: Int = 2500
    ): PingResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val safePort = if (port in 1..65535) port else 443

        if (cleanHost.isEmpty()) {
            return@withContext PingResult(-1L, method, false, "Invalid host")
        }

        when (method) {
            PingMethod.TCP -> pingTcp(cleanHost, safePort, timeoutMs)
            PingMethod.ICMP -> pingIcmp(cleanHost, timeoutMs)
        }
    }

    suspend fun testConfig(
        config: ProxyConfig,
        method: PingMethod = PingMethod.TCP,
        timeoutMs: Int = 2500
    ): PingResult {
        return testLatency(config.host, config.port, method, timeoutMs)
    }

    private fun pingTcp(host: String, port: Int, timeoutMs: Int): PingResult {
        val start = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            val address = InetSocketAddress(host, port)
            socket.connect(address, timeoutMs)
            val elapsed = System.currentTimeMillis() - start
            PingResult(latencyMs = elapsed, method = PingMethod.TCP, isSuccess = true)
        } catch (e: Exception) {
            PingResult(latencyMs = -1L, method = PingMethod.TCP, isSuccess = false, errorMessage = e.message ?: "Connection timed out")
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun pingIcmp(host: String, timeoutMs: Int): PingResult {
        val start = System.currentTimeMillis()
        return try {
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(timeoutMs)
            val elapsed = System.currentTimeMillis() - start
            if (reachable) {
                PingResult(latencyMs = elapsed.coerceAtLeast(1L), method = PingMethod.ICMP, isSuccess = true)
            } else {
                PingResult(latencyMs = -1L, method = PingMethod.ICMP, isSuccess = false, errorMessage = "Host unreachable")
            }
        } catch (e: Exception) {
            PingResult(latencyMs = -1L, method = PingMethod.ICMP, isSuccess = false, errorMessage = e.message ?: "ICMP check failed")
        }
    }
}

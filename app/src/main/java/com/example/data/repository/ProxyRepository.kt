package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.data.db.ProxyConfigDao
import com.example.data.db.SubscriptionDao
import com.example.data.model.ProxyConfig
import com.example.data.model.SubscriptionSource
import com.example.data.parser.V2RayParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ProxyRepository(
    private val subscriptionDao: SubscriptionDao,
    private val proxyConfigDao: ProxyConfigDao
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val allSubscriptions: Flow<List<SubscriptionSource>> = subscriptionDao.getAllSubscriptions()
    val allConfigs: Flow<List<ProxyConfig>> = proxyConfigDao.getAllConfigs()
    val favoriteConfigs: Flow<List<ProxyConfig>> = proxyConfigDao.getFavoriteConfigs()

    suspend fun insertSubscription(name: String, url: String): Long {
        return withContext(Dispatchers.IO) {
            subscriptionDao.insertSubscription(
                SubscriptionSource(
                    name = name.trim(),
                    url = url.trim()
                )
            )
        }
    }

    suspend fun updateSubscription(subscription: SubscriptionSource) {
        withContext(Dispatchers.IO) {
            subscriptionDao.updateSubscription(subscription)
        }
    }

    suspend fun deleteSubscription(subscription: SubscriptionSource) {
        withContext(Dispatchers.IO) {
            subscriptionDao.deleteSubscription(subscription)
            proxyConfigDao.deleteConfigsBySource(subscription.url)
        }
    }

    suspend fun toggleFavorite(id: Long, isFav: Boolean) {
        withContext(Dispatchers.IO) {
            proxyConfigDao.setFavorite(id, isFav)
        }
    }

    suspend fun deleteConfig(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            proxyConfigDao.deleteConfig(config)
        }
    }

    suspend fun clearAllConfigs() {
        withContext(Dispatchers.IO) {
            proxyConfigDao.deleteAllConfigs()
        }
    }

    suspend fun fetchSingleSource(source: SubscriptionSource): Pair<SubscriptionSource, List<ProxyConfig>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "v2rayNG/1.8.12 (Android; Linux)")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val updated = source.copy(
                        lastUpdated = System.currentTimeMillis(),
                        lastStatus = "HTTP Error ${response.code}"
                    )
                    subscriptionDao.updateSubscription(updated)
                    return@withContext Pair(updated, emptyList())
                }

                val body = response.body?.string().orEmpty()
                val parsedConfigs = V2RayParser.parseSubscriptionContent(body, source.url, source.name)

                val updated = source.copy(
                    lastUpdated = System.currentTimeMillis(),
                    configCount = parsedConfigs.size,
                    lastStatus = "Success (${parsedConfigs.size} configs)"
                )
                subscriptionDao.updateSubscription(updated)
                Pair(updated, parsedConfigs)
            } catch (e: Exception) {
                val updated = source.copy(
                    lastUpdated = System.currentTimeMillis(),
                    lastStatus = "Failed: ${e.message?.take(30) ?: "Unknown"}"
                )
                subscriptionDao.updateSubscription(updated)
                Pair(updated, emptyList())
            }
        }
    }

    suspend fun syncAllEnabledSources(onProgress: (current: Int, total: Int, currentSourceName: String) -> Unit): Int {
        return withContext(Dispatchers.IO) {
            val enabledSources = subscriptionDao.getEnabledSubscriptions()
            if (enabledSources.isEmpty()) return@withContext 0

            var completedCount = 0
            val total = enabledSources.size

            // Fetch concurrently with coroutines, mirroring asyncio.gather in Python
            val results = coroutineScope {
                enabledSources.map { source ->
                    async {
                        val result = fetchSingleSource(source)
                        synchronized(this@ProxyRepository) {
                            completedCount++
                            onProgress(completedCount, total, source.name)
                        }
                        result.second
                    }
                }.awaitAll()
            }

            // Deduplicate across all sources
            val mergedMap = mutableMapOf<String, ProxyConfig>()
            for (list in results) {
                for (config in list) {
                    // Deduplicate key: protocol + host + port + path or rawUri
                    val key = "${config.protocol}://${config.host}:${config.port}/${config.path}"
                    if (!mergedMap.containsKey(key)) {
                        mergedMap[key] = config
                    }
                }
            }

            val finalConfigs = mergedMap.values.toList()

            // Save to DB
            proxyConfigDao.deleteAllConfigs()
            proxyConfigDao.insertConfigs(finalConfigs)

            finalConfigs.size
        }
    }

    suspend fun pingConfig(config: ProxyConfig): Long {
        return withContext(Dispatchers.IO) {
            val host = config.host
            val port = if (config.port in 1..65535) config.port else 443

            val start = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.soTimeout = 2500
                val address = InetSocketAddress(host, port)
                socket.connect(address, 2500)
                val latency = System.currentTimeMillis() - start
                proxyConfigDao.updateLatency(config.id, latency)
                latency
            } catch (e: Exception) {
                proxyConfigDao.updateLatency(config.id, -1L)
                -1L
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun generateMergedText(configs: List<ProxyConfig>): String {
        return configs.joinToString("\n") { it.rawUri }
    }

    fun generateBase64Subscription(configs: List<ProxyConfig>): String {
        val raw = generateMergedText(configs)
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

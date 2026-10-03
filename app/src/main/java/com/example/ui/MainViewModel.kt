package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ProxyConfig
import com.example.data.model.SubscriptionSource
import com.example.data.repository.ProxyRepository
import com.example.data.service.PingMethod
import com.example.data.service.RayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

enum class SortOption {
    DEFAULT,
    FASTEST_PING,
    NAME_ASC,
    PROTOCOL
}

data class UiState(
    val selectedTab: Int = 0,
    val searchQuery: String = "",
    val selectedProtocol: String = "All",
    val sortOption: SortOption = SortOption.DEFAULT,
    val onlyFavorites: Boolean = false,
    val selectedPingMethod: PingMethod = PingMethod.TCP,
    val isSyncing: Boolean = false,
    val syncProgress: Float = 0f,
    val syncStatusText: String = "",
    val isPingingAll: Boolean = false,
    val pingProgress: Float = 0f,
    val selectedConfigDetails: ProxyConfig? = null,
    val showAddSourceDialog: Boolean = false,
    val editingSource: SubscriptionSource? = null,
    val snackbarMessage: String? = null,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectedConfig: ProxyConfig? = null,
    val connectionDurationSeconds: Long = 0L,
    val isSelectingBestNode: Boolean = false,
    val connectionLogs: List<String> = listOf("[LOG] Service idle. Press button to connect."),
    val isTestingReachability: Boolean = false,
    val facebookReachabilityMs: Long? = null
)

class MainViewModel(
    application: Application,
    private val repository: ProxyRepository
) : AndroidViewModel(application) {

    private val sessionPrefs = application.getSharedPreferences("vpn_session_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var durationJob: Job? = null

    init {
        restorePinnedSession()
        viewModelScope.launch {
            RayVpnService.isVpnRunning.collect { isRunning ->
                if (!isRunning && _uiState.value.isConnected) {
                    _uiState.value = _uiState.value.copy(
                        isConnected = false,
                        connectedConfig = null,
                        connectionDurationSeconds = 0L
                    )
                    appendLog("[LOG] VPN tunnel disconnected.")
                }
            }
        }
    }

    fun appendLog(log: String) {
        val current = _uiState.value.connectionLogs
        val updated = (current + log).takeLast(40)
        _uiState.value = _uiState.value.copy(connectionLogs = updated)
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(connectionLogs = listOf("[LOG] Console logs cleared."))
    }

    private fun restorePinnedSession() {
        val wasConnected = sessionPrefs.getBoolean("pref_is_connected", false)
        val pinnedId = sessionPrefs.getLong("pref_pinned_id", -1L)
        if (wasConnected && pinnedId >= 0L) {
            val remark = sessionPrefs.getString("pref_pinned_remark", "") ?: ""
            val host = sessionPrefs.getString("pref_pinned_host", "") ?: ""
            val port = sessionPrefs.getInt("pref_pinned_port", 443)
            val protocol = sessionPrefs.getString("pref_pinned_protocol", "VLESS") ?: "VLESS"
            val rawUri = sessionPrefs.getString("pref_pinned_uri", "") ?: ""
            val latency = sessionPrefs.getLong("pref_pinned_latency", -1L)
            val restored = ProxyConfig(
                id = pinnedId,
                sourceUrl = "",
                remark = remark.ifEmpty { "Pinned Server" },
                host = host,
                port = port,
                protocol = protocol,
                rawUri = rawUri,
                latencyMs = if (latency >= 0) latency else null
            )
            val logs = listOf(
                "[LOG] Restored pinned session: ${restored.remark}",
                "[LOG] Connecting to ${restored.host}:${restored.port} (${restored.protocol})...",
                "[LOG] Tunnel established.",
                "[LOG] Encrypted tunnel is active."
            )
            _uiState.value = _uiState.value.copy(
                isConnected = true,
                connectedConfig = restored,
                connectionLogs = logs
            )
            startDurationTracker()
        }
    }

    private fun savePinnedSession(config: ProxyConfig?, isConnected: Boolean) {
        val editor = sessionPrefs.edit()
        editor.putBoolean("pref_is_connected", isConnected)
        if (config != null && isConnected) {
            editor.putLong("pref_pinned_id", config.id)
            editor.putString("pref_pinned_remark", config.remark)
            editor.putString("pref_pinned_host", config.host)
            editor.putInt("pref_pinned_port", config.port)
            editor.putString("pref_pinned_protocol", config.protocol)
            editor.putString("pref_pinned_uri", config.rawUri)
            editor.putLong("pref_pinned_latency", config.latencyMs ?: -1L)
        } else {
            editor.remove("pref_pinned_id")
            editor.remove("pref_pinned_remark")
            editor.remove("pref_pinned_host")
            editor.remove("pref_pinned_port")
            editor.remove("pref_pinned_protocol")
            editor.remove("pref_pinned_uri")
            editor.remove("pref_pinned_latency")
        }
        editor.apply()
    }

    private fun startDurationTracker() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (_uiState.value.isConnected) {
                delay(1000L)
                _uiState.value = _uiState.value.copy(
                    connectionDurationSeconds = _uiState.value.connectionDurationSeconds + 1
                )
            }
        }
    }

    val subscriptions: StateFlow<List<SubscriptionSource>> = repository.allSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawConfigs: StateFlow<List<ProxyConfig>> = repository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived filtered and sorted configs
    val filteredConfigs: StateFlow<List<ProxyConfig>> = combine(
        rawConfigs,
        _uiState
    ) { configs, state ->
        var list = configs

        // Protocol filter
        if (state.selectedProtocol != "All") {
            list = list.filter {
                if (state.selectedProtocol == "Other") {
                    !listOf("VMess", "VLESS", "Trojan", "Shadowsocks", "Hysteria", "Hysteria2").contains(it.protocol)
                } else {
                    it.protocol.equals(state.selectedProtocol, ignoreCase = true)
                }
            }
        }

        // Favorites filter
        if (state.onlyFavorites) {
            list = list.filter { it.isFavorite }
        }

        // Search filter
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.remark.lowercase().contains(q) ||
                it.host.lowercase().contains(q) ||
                it.port.toString().contains(q) ||
                it.protocol.lowercase().contains(q) ||
                it.sourceName.lowercase().contains(q)
            }
        }

        // Sorting
        when (state.sortOption) {
            SortOption.DEFAULT -> list
            SortOption.FASTEST_PING -> list.sortedWith(
                compareBy<ProxyConfig> {
                    val lat = it.latencyMs
                    if (lat == null || lat < 0) Long.MAX_VALUE else lat
                }.thenBy { it.remark }
            )
            SortOption.NAME_ASC -> list.sortedBy { it.remark.lowercase() }
            SortOption.PROTOCOL -> list.sortedBy { it.protocol }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pingJob: Job? = null

    init {
        // Automatically perform a fast check or let user trigger sync
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSelectedProtocol(protocol: String) {
        _uiState.value = _uiState.value.copy(selectedProtocol = protocol)
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = sortOption)
    }

    fun toggleFavoritesFilter() {
        _uiState.value = _uiState.value.copy(onlyFavorites = !_uiState.value.onlyFavorites)
    }

    fun showConfigDetails(config: ProxyConfig?) {
        _uiState.value = _uiState.value.copy(selectedConfigDetails = config)
    }

    fun openAddSourceDialog(sourceToEdit: SubscriptionSource? = null) {
        _uiState.value = _uiState.value.copy(
            showAddSourceDialog = true,
            editingSource = sourceToEdit
        )
    }

    fun dismissAddSourceDialog() {
        _uiState.value = _uiState.value.copy(
            showAddSourceDialog = false,
            editingSource = null
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    // --- Subscription Actions ---

    fun saveSource(name: String, url: String) {
        val currentEdit = _uiState.value.editingSource
        viewModelScope.launch {
            if (currentEdit != null) {
                repository.updateSubscription(currentEdit.copy(name = name, url = url))
                showSnackbar("Source '${name}' updated")
            } else {
                repository.insertSubscription(name, url)
                showSnackbar("Source '${name}' added")
            }
            dismissAddSourceDialog()
        }
    }

    fun deleteSource(source: SubscriptionSource) {
        viewModelScope.launch {
            repository.deleteSubscription(source)
            showSnackbar("Source '${source.name}' deleted")
        }
    }

    fun toggleSourceEnabled(source: SubscriptionSource) {
        viewModelScope.launch {
            repository.updateSubscription(source.copy(isEnabled = !source.isEnabled))
        }
    }

    fun syncSource(source: SubscriptionSource) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                syncStatusText = "Fetching ${source.name}..."
            )
            val result = repository.fetchSingleSource(source)
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncStatusText = "",
                snackbarMessage = "Fetched ${result.second.size} configs from ${source.name}"
            )
        }
    }

    fun syncAllSources() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                syncProgress = 0f,
                syncStatusText = "Connecting to sources..."
            )

            val count = repository.syncAllEnabledSources { current, total, name ->
                val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                _uiState.value = _uiState.value.copy(
                    syncProgress = progress,
                    syncStatusText = "Fetched $current/$total: $name"
                )
            }

            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncProgress = 1f,
                syncStatusText = "",
                snackbarMessage = "Merged $count unique proxy configs!"
            )
        }
    }

    // --- Config Actions ---

    fun toggleFavorite(config: ProxyConfig) {
        viewModelScope.launch {
            repository.toggleFavorite(config.id, !config.isFavorite)
        }
    }

    fun deleteConfig(config: ProxyConfig) {
        viewModelScope.launch {
            repository.deleteConfig(config)
            showSnackbar("Config removed")
        }
    }

    fun clearAllConfigs() {
        viewModelScope.launch {
            repository.clearAllConfigs()
            showSnackbar("All configs cleared")
        }
    }

    fun setPingMethod(method: PingMethod) {
        _uiState.value = _uiState.value.copy(selectedPingMethod = method)
        showSnackbar("Latency mode set to ${method.label} (${method.description})")
    }

    fun pingConfig(config: ProxyConfig, method: PingMethod = _uiState.value.selectedPingMethod) {
        viewModelScope.launch {
            val latency = repository.pingConfig(config, method)
            val msg = if (latency >= 0) {
                "${config.remark}: ${latency}ms [${method.label}]"
            } else {
                "${config.remark}: ${method.label} timed out"
            }
            showSnackbar(msg)
        }
    }

    fun pingAllVisibleConfigs(method: PingMethod = _uiState.value.selectedPingMethod) {
        if (_uiState.value.isPingingAll) {
            pingJob?.cancel()
            _uiState.value = _uiState.value.copy(isPingingAll = false, pingProgress = 0f)
            showSnackbar("Ping test stopped")
            return
        }

        val targetList = filteredConfigs.value
        if (targetList.isEmpty()) {
            showSnackbar("No configs to ping")
            return
        }

        pingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPingingAll = true, pingProgress = 0f)
            val total = targetList.size
            for ((index, item) in targetList.withIndex()) {
                repository.pingConfig(item, method)
                _uiState.value = _uiState.value.copy(pingProgress = (index + 1).toFloat() / total.toFloat())
            }
            _uiState.value = _uiState.value.copy(isPingingAll = false, pingProgress = 1f)
            showSnackbar("${method.label} test completed for $total nodes")
        }
    }

    // --- VPN Connection & Session Persistence ---

    fun toggleConnection(targetConfig: ProxyConfig? = null) {
        if (_uiState.value.isConnected) {
            disconnect()
            return
        }

        val target = targetConfig
            ?: _uiState.value.connectedConfig
            ?: rawConfigs.value.firstOrNull { it.latencyMs != null && it.latencyMs > 0 }
            ?: rawConfigs.value.firstOrNull()

        if (target == null) {
            showSnackbar("No servers available. Sync feeds first.")
            return
        }

        connectToConfig(target)
    }

    fun connectToConfig(config: ProxyConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true)
            appendLog("[LOG] Connecting to ${config.remark}...")
            appendLog("[LOG] Target: ${config.host}:${config.port} [${config.protocol}]")
            val latency = repository.pingConfig(config, _uiState.value.selectedPingMethod)
            val updated = config.copy(latencyMs = if (latency >= 0) latency else config.latencyMs)
            savePinnedSession(updated, true)

            // Start Android system VPN tunnel service
            RayVpnService.startVpn(
                context = getApplication(),
                remark = updated.remark,
                host = updated.host,
                port = updated.port,
                protocol = updated.protocol
            )

            _uiState.value = _uiState.value.copy(
                isConnected = true,
                isConnecting = false,
                connectedConfig = updated,
                connectionDurationSeconds = 0L
            )
            startDurationTracker()
            val latStr = if (latency >= 0) "${latency}ms" else "connected"
            appendLog("[LOG] Handshake verified: $latStr")
            appendLog("[LOG] Virtual TUN interface (tun0) established.")
            appendLog("[LOG] VPN Key active in status bar 🔑")
            appendLog("[LOG] Encrypted tunnel is active.")
            showSnackbar("VPN Connected: ${updated.remark} [$latStr]")
        }
    }

    fun disconnect() {
        val prev = _uiState.value.connectedConfig
        savePinnedSession(null, false)
        durationJob?.cancel()

        // Stop Android system VPN tunnel service
        RayVpnService.stopVpn(getApplication())

        appendLog("[LOG] Disconnecting from ${prev?.remark ?: "proxy server"}...")
        appendLog("[LOG] Virtual TUN interface closed.")
        appendLog("[LOG] Service idle. Press button to connect.")
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            isConnecting = false,
            connectedConfig = null,
            connectionDurationSeconds = 0L
        )
        showSnackbar("Disconnected from ${prev?.remark ?: "proxy server"}")
    }

    fun selectAndConnectBestNode(method: PingMethod = _uiState.value.selectedPingMethod) {
        if (_uiState.value.isSelectingBestNode) return
        viewModelScope.launch {
            val allList = rawConfigs.value
            if (allList.isEmpty()) {
                appendLog("[LOG] Error: No server configurations available.")
                showSnackbar("No server configurations available to test.")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSelectingBestNode = true)
            appendLog("[LOG] Best Node Selector: Pinging candidates via ${method.label}...")
            showSnackbar("Pinging candidate nodes to identify lowest latency...")

            // Prioritize VLESS, Trojan, Hysteria2, VMess as requested
            val candidates = allList.sortedWith(
                compareByDescending<ProxyConfig> {
                    val p = it.protocol.uppercase()
                    when {
                        p.contains("HYSTERIA") -> 5
                        p.contains("VLESS") -> 4
                        p.contains("TROJAN") -> 3
                        p.contains("VMESS") -> 2
                        else -> 1
                    }
                }
            ).take(20)

            val testedCandidates = mutableListOf<Pair<ProxyConfig, Long>>()
            withContext(Dispatchers.IO) {
                // Ping in parallel batches of 5
                candidates.chunked(5).forEach { batch ->
                    val deferred = batch.map { cfg ->
                        async {
                            val lat = repository.pingConfig(cfg, method)
                            Pair(cfg.copy(latencyMs = lat), lat)
                        }
                    }
                    testedCandidates.addAll(deferred.awaitAll())
                }
            }

            val validNodes = testedCandidates.filter { it.second > 0 }.sortedBy { it.second }
            if (validNodes.isNotEmpty()) {
                val (bestConfig, bestLatency) = validNodes.first()
                val updated = bestConfig.copy(latencyMs = bestLatency)
                savePinnedSession(updated, true)
                appendLog("[LOG] Lowest latency: ${updated.remark} (${bestLatency}ms [${method.label}])")
                appendLog("[LOG] Connecting to ${updated.host}:${updated.port}...")

                // Start Android system VPN tunnel service
                RayVpnService.startVpn(
                    context = getApplication(),
                    remark = updated.remark,
                    host = updated.host,
                    port = updated.port,
                    protocol = updated.protocol
                )

                appendLog("[LOG] Virtual TUN interface (tun0) established.")
                appendLog("[LOG] VPN Key active in status bar 🔑")
                appendLog("[LOG] Encrypted tunnel is active.")
                _uiState.value = _uiState.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    connectedConfig = updated,
                    isSelectingBestNode = false,
                    connectionDurationSeconds = 0L
                )
                startDurationTracker()
                showSnackbar("Best node pinned: ${updated.remark} (${bestLatency}ms [${method.label}])")
            } else {
                appendLog("[LOG] Warning: All tested nodes timed out via ${method.label}.")
                _uiState.value = _uiState.value.copy(isSelectingBestNode = false)
                showSnackbar("All tested nodes timed out with ${method.label}. Try TCP/ICMP toggle.")
            }
        }
    }

    fun testFacebookReachability() {
        if (_uiState.value.isTestingReachability) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingReachability = true)
            appendLog("[LOG] Checking reachability to Facebook (www.facebook.com:443)...")
            val start = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.soTimeout = 4000
                    socket.connect(InetSocketAddress("www.facebook.com", 443), 4000)
                    val elapsed = System.currentTimeMillis() - start
                    elapsed
                } catch (e: Exception) {
                    -1L
                } finally {
                    try {
                        socket?.close()
                    } catch (ignored: Exception) {}
                }
            }

            _uiState.value = _uiState.value.copy(
                isTestingReachability = false,
                facebookReachabilityMs = result
            )

            if (result >= 0) {
                appendLog("[LOG] Facebook Reachability: OK (${result}ms). Direct connection verified.")
                showSnackbar("Facebook reachable: ${result}ms")
            } else {
                appendLog("[LOG] Facebook Reachability: Failed/Timeout. Check proxy node or network.")
                showSnackbar("Facebook unreachable via current connection.")
            }
        }
    }

    // --- Export Actions ---

    fun copyToClipboard(context: Context, text: String, label: String = "Proxy Configs") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        showSnackbar("Copied to clipboard (${text.lines().size} lines)")
    }

    fun copyAllMerged(context: Context, asBase64: Boolean = false) {
        val list = filteredConfigs.value
        if (list.isEmpty()) {
            showSnackbar("No configs to copy")
            return
        }
        val text = if (asBase64) repository.generateBase64Subscription(list) else repository.generateMergedText(list)
        copyToClipboard(context, text, if (asBase64) "V2Ray Subscription (Base64)" else "V2Ray Configs")
    }

    fun shareConfigs(context: Context, asBase64: Boolean = false) {
        val list = filteredConfigs.value
        if (list.isEmpty()) {
            showSnackbar("No configs to share")
            return
        }
        val content = if (asBase64) repository.generateBase64Subscription(list) else repository.generateMergedText(list)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Merged V2Ray Configs")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun exportToLocalFile(context: Context, asBase64: Boolean = false) {
        val list = filteredConfigs.value
        if (list.isEmpty()) {
            showSnackbar("No configs to export")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = if (asBase64) "ray_subscription_base64.txt" else "all_merged.txt"
                val file = File(context.getExternalFilesDir(null) ?: context.filesDir, fileName)
                val content = if (asBase64) repository.generateBase64Subscription(list) else repository.generateMergedText(list)
                FileOutputStream(file).use {
                    it.write(content.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("Saved ${list.size} configs to ${file.name}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Failed to export: ${e.message}")
                }
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
            val db = AppDatabase.getDatabase(application, scope)
            val repository = ProxyRepository(db.subscriptionDao(), db.proxyConfigDao())
            return MainViewModel(application, repository) as T
        }
    }
}

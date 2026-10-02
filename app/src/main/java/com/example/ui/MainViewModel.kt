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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val isSyncing: Boolean = false,
    val syncProgress: Float = 0f,
    val syncStatusText: String = "",
    val isPingingAll: Boolean = false,
    val pingProgress: Float = 0f,
    val selectedConfigDetails: ProxyConfig? = null,
    val showAddSourceDialog: Boolean = false,
    val editingSource: SubscriptionSource? = null,
    val snackbarMessage: String? = null
)

class MainViewModel(
    application: Application,
    private val repository: ProxyRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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

    fun pingConfig(config: ProxyConfig) {
        viewModelScope.launch {
            val latency = repository.pingConfig(config)
            val msg = if (latency >= 0) "${config.remark}: ${latency}ms" else "${config.remark}: Connection timed out"
            showSnackbar(msg)
        }
    }

    fun pingAllVisibleConfigs() {
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
                repository.pingConfig(item)
                _uiState.value = _uiState.value.copy(pingProgress = (index + 1).toFloat() / total.toFloat())
            }
            _uiState.value = _uiState.value.copy(isPingingAll = false, pingProgress = 1f)
            showSnackbar("Ping test completed for $total nodes")
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

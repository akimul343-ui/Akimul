package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.AddSourceDialog
import com.example.ui.components.ConfigDetailDialog
import com.example.ui.screens.ConfigsScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.PingScreen
import com.example.ui.screens.SourcesScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.RayCollectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RayCollectorTheme {
                val context = LocalContext.current
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(context.applicationContext as Application)
                )
                RayCollectorApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RayCollectorApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val filteredConfigs by viewModel.filteredConfigs.collectAsStateWithLifecycle()
    val rawConfigs by viewModel.rawConfigs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Spin animation for sync button when syncing
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin_rotation"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RayCollector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Pill badge with count
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${rawConfigs.size} nodes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncAllSources() },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.testTag("appbar_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync All Feeds",
                            tint = if (uiState.isSyncing) CyberCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = if (uiState.isSyncing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                // Tab 0: Configs
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        BadgedBox(badge = {
                            if (filteredConfigs.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = if (filteredConfigs.size > 999) "999+" else "${filteredConfigs.size}",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = "Configs")
                        }
                    },
                    label = { Text("Nodes", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_configs")
                )

                // Tab 1: Sources
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        Icon(imageVector = Icons.Default.RssFeed, contentDescription = "Feeds")
                    },
                    label = { Text("Feeds", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_feeds")
                )

                // Tab 2: Speed / Ping
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = "Ping Test")
                    },
                    label = { Text("Speed", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_speed")
                )

                // Tab 3: Export & Tools
                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Export")
                    },
                    label = { Text("Export", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_export")
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                0 -> ConfigsScreen(
                    configs = filteredConfigs,
                    uiState = uiState,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onProtocolSelect = { viewModel.setSelectedProtocol(it) },
                    onSortSelect = { viewModel.setSortOption(it) },
                    onToggleFavorites = { viewModel.toggleFavoritesFilter() },
                    onConfigClick = { viewModel.showConfigDetails(it) },
                    onPingConfig = { viewModel.pingConfig(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onCopyConfig = { viewModel.copyToClipboard(context, it) },
                    onSyncAll = { viewModel.syncAllSources() }
                )
                1 -> SourcesScreen(
                    sources = subscriptions,
                    isSyncing = uiState.isSyncing,
                    onAddSource = { viewModel.openAddSourceDialog() },
                    onEditSource = { viewModel.openAddSourceDialog(it) },
                    onDeleteSource = { viewModel.deleteSource(it) },
                    onToggleSource = { viewModel.toggleSourceEnabled(it) },
                    onSyncSource = { viewModel.syncSource(it) },
                    onSyncAll = { viewModel.syncAllSources() }
                )
                2 -> PingScreen(
                    configs = rawConfigs,
                    isPingingAll = uiState.isPingingAll,
                    pingProgress = uiState.pingProgress,
                    onPingAll = { viewModel.pingAllVisibleConfigs() },
                    onPingSingle = { viewModel.pingConfig(it) },
                    onCopySingle = { viewModel.copyToClipboard(context, it) },
                    onCopyFastest = { fastestList ->
                        val text = fastestList.joinToString("\n") { it.rawUri }
                        viewModel.copyToClipboard(context, text, "Top 10 Fastest V2Ray Nodes")
                    }
                )
                3 -> ExportScreen(
                    configs = rawConfigs,
                    onCopyAll = { asBase64 -> viewModel.copyAllMerged(context, asBase64) },
                    onShareAll = { asBase64 -> viewModel.shareConfigs(context, asBase64) },
                    onExportFile = { asBase64 -> viewModel.exportToLocalFile(context, asBase64) },
                    onClearAllConfigs = { viewModel.clearAllConfigs() }
                )
            }
        }
    }

    // Detail Dialog
    uiState.selectedConfigDetails?.let { config ->
        ConfigDetailDialog(
            config = config,
            onDismiss = { viewModel.showConfigDetails(null) },
            onPing = { viewModel.pingConfig(it) },
            onCopy = { viewModel.copyToClipboard(context, it) }
        )
    }

    // Add / Edit Source Dialog
    if (uiState.showAddSourceDialog) {
        AddSourceDialog(
            sourceToEdit = uiState.editingSource,
            onDismiss = { viewModel.dismissAddSourceDialog() },
            onSave = { name, url -> viewModel.saveSource(name, url) }
        )
    }
}

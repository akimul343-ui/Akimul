package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProxyConfig
import com.example.data.service.PingMethod
import com.example.ui.components.ConnectionStatusCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberViolet

@Composable
fun PingScreen(
    configs: List<ProxyConfig>,
    isPingingAll: Boolean,
    pingProgress: Float,
    selectedPingMethod: PingMethod = PingMethod.TCP,
    onSelectPingMethod: (PingMethod) -> Unit = {},
    isConnected: Boolean = false,
    isConnecting: Boolean = false,
    connectedConfig: ProxyConfig? = null,
    connectionDurationSeconds: Long = 0L,
    isSelectingBestNode: Boolean = false,
    onToggleConnection: () -> Unit = {},
    onSelectBestNode: () -> Unit = {},
    onPingAll: () -> Unit,
    onPingSingle: (ProxyConfig) -> Unit,
    onCopySingle: (String) -> Unit,
    onCopyFastest: (List<ProxyConfig>) -> Unit
) {
    val testedConfigs = configs.filter { it.latencyMs != null }
    val onlineConfigs = configs.filter { it.latencyMs != null && it.latencyMs > 0 }
        .sortedBy { it.latencyMs }
    val timeoutConfigs = configs.filter { it.latencyMs != null && it.latencyMs < 0 }
    val untestedConfigs = configs.filter { it.latencyMs == null }

    val fastestLatency = onlineConfigs.firstOrNull()?.latencyMs
    val avgLatency = if (onlineConfigs.isNotEmpty()) {
        onlineConfigs.map { it.latencyMs!! }.average().toLong()
    } else null

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("ping_screen")
    ) {
        // Connection & Session Status Card
        item {
            ConnectionStatusCard(
                isConnected = isConnected,
                isConnecting = isConnecting,
                connectedConfig = connectedConfig,
                connectionDurationSeconds = connectionDurationSeconds,
                isSelectingBestNode = isSelectingBestNode,
                onToggleConnection = onToggleConnection,
                onSelectBestNode = onSelectBestNode
            )
        }

        // Main Ping Control Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Socket Latency Tester",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Active Mode: ${selectedPingMethod.label} - ${selectedPingMethod.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ping Method Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Protocol:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PingMethod.values().forEach { method ->
                            FilterChip(
                                selected = selectedPingMethod == method,
                                onClick = { onSelectPingMethod(method) },
                                label = { Text(method.label, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = CyberCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isPingingAll) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Testing connectivity...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(pingProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { pingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    Button(
                        onClick = onPingAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ping_all_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (isPingingAll) {
                            ButtonDefaults.buttonColors(containerColor = CyberRed)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        }
                    ) {
                        Icon(
                            imageVector = if (isPingingAll) Icons.Default.Stop else Icons.Default.NetworkCheck,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPingingAll) "Stop Latency Test" else "Test All Available Servers")
                    }
                }
            }
        }

        // Latency Telemetry Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Online Nodes",
                    value = "${onlineConfigs.size}",
                    subtitle = if (configs.isNotEmpty()) "${(onlineConfigs.size * 100 / configs.size)}% online" else "0%",
                    color = CyberGreen,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Fastest Ping",
                    value = if (fastestLatency != null) "${fastestLatency}ms" else "--",
                    subtitle = if (avgLatency != null) "Avg ${avgLatency}ms" else "Untested",
                    color = CyberCyan,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Timeout / Dead",
                    value = "${timeoutConfigs.size}",
                    subtitle = "${untestedConfigs.size} untested",
                    color = CyberRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Leaderboard header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fastest Ranked Nodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (onlineConfigs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onCopyFastest(onlineConfigs.take(10)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("copy_top10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Top 10", fontSize = 11.sp)
                    }
                }
            }
        }

        if (onlineConfigs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (configs.isEmpty()) "No configs available to test. Please sync feeds first."
                        else "No live nodes tested yet. Tap 'Test All Available Servers' to rank the fastest nodes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            itemsIndexed(onlineConfigs.take(20), key = { _, it -> it.id }) { index, config ->
                RankedNodeCard(
                    rank = index + 1,
                    config = config,
                    onPing = { onPingSingle(config) },
                    onCopy = { onCopySingle(config.rawUri) }
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RankedNodeCard(
    rank: Int,
    config: ProxyConfig,
    onPing: () -> Unit,
    onCopy: () -> Unit
) {
    val rankBadgeColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> CyberCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(rankBadgeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    color = rankBadgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.remark,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${config.protocol} • ${config.host}:${config.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Latency
            Text(
                text = "${config.latencyMs ?: 0} ms",
                fontWeight = FontWeight.Bold,
                color = CyberGreen,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

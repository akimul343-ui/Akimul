package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProxyConfig
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed

@Composable
fun ProxyConnectionScreen(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedConfig: ProxyConfig?,
    allConfigs: List<ProxyConfig>,
    logs: List<String>,
    isSelectingBestNode: Boolean,
    onToggleConnection: () -> Unit,
    onSelectNode: (ProxyConfig) -> Unit,
    onSelectBestNode: () -> Unit,
    onClearLogs: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Pulsing animation for active connection
    val infiniteTransition = rememberInfiniteTransition(label = "btn_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentServerDisplay = when {
        connectedConfig != null -> {
            val latText = if (connectedConfig.latencyMs != null && connectedConfig.latencyMs > 0) {
                " (${connectedConfig.latencyMs} ms)"
            } else ""
            "${connectedConfig.remark}$latText"
        }
        allConfigs.isNotEmpty() -> {
            val first = allConfigs.first()
            val latText = if (first.latencyMs != null && first.latencyMs > 0) " (${first.latencyMs} ms)" else ""
            "${first.remark}$latText"
        }
        else -> "Singapore Server 01 (120 ms)"
    }

    val consoleScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("proxy_connection_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ১. সার্ভার সিলেক্ট করার কার্ড (Server Selector Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("server_selection_card"),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDropdownExpanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading Icon
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Server",
                    tint = CyberCyan,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "সিলেক্ট করা সার্ভার",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = currentServerDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Trailing Dropdown Icon
                Box {
                    IconButton(onClick = { isDropdownExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Server",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        if (allConfigs.isNotEmpty()) {
                            allConfigs.take(20).forEach { cfg ->
                                val latText = if (cfg.latencyMs != null && cfg.latencyMs > 0) " (${cfg.latencyMs} ms)" else ""
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "${cfg.remark}$latText",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${cfg.protocol} • ${cfg.host}:${cfg.port}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectNode(cfg)
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        } else {
                            // Default fallback items from Flutter template
                            val defaultServers = listOf(
                                "Singapore Server 01 (120 ms)",
                                "Japan Server 02 (180 ms)",
                                "Germany Server 01 (210 ms)"
                            )
                            defaultServers.forEach { serverName ->
                                DropdownMenuItem(
                                    text = { Text(serverName) },
                                    onClick = { isDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Best Node Selector Button
        OutlinedButton(
            onClick = onSelectBestNode,
            enabled = !isConnecting && !isSelectingBestNode,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_best_node_btn"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
        ) {
            if (isSelectingBestNode) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = CyberCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finding & Connecting Lowest Latency Node...")
            } else {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Best Node Selector (Lowest Latency)",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ২. মেইন কানেক্ট বাটন (Main Circular Connect Button with Pulse)
        val buttonBgColor by animateColorAsState(
            targetValue = if (isConnected) Color(0xFF2E7D32) /* Green */ else Color(0xFFC62828) /* Red */,
            label = "btn_bg_color"
        )

        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(if (isConnected) pulseScale else 1f)
                .clip(CircleShape)
                .background(buttonBgColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(buttonBgColor)
                    .clickable(
                        enabled = !isConnecting && !isSelectingBestNode,
                        onClick = onToggleConnection
                    )
                    .testTag("main_circle_connect_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(46.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                            contentDescription = if (isConnected) "Connected" else "Disconnected",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isConnected) "CONNECTED" else "DISCONNECT",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ৩. স্ট্যাটাস কনসোল বক্স (Status Console Box)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_console_box"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xDE111116)
            ),
            border = BorderStroke(1.dp, Color(0xFF2C2C35))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Header row of terminal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STATUS CONSOLE LOG",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888)
                        )
                    }

                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear logs",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Console Text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .verticalScroll(consoleScrollState)
                ) {
                    val logsText = if (logs.isNotEmpty()) {
                        logs.joinToString("\n")
                    } else if (isConnected) {
                        "[LOG] Connecting to $currentServerDisplay...\n[LOG] Tunnel established.\n[LOG] Encrypted tunnel is active."
                    } else {
                        "[LOG] Service idle. Press button to connect."
                    }

                    Text(
                        text = logsText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF69F0AE), // GreenAccent
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

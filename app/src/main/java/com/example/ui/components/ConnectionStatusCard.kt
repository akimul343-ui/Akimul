package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProxyConfig
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberViolet

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    connectedConfig: ProxyConfig?,
    connectionDurationSeconds: Long,
    isSelectingBestNode: Boolean,
    onToggleConnection: () -> Unit,
    onSelectBestNode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse animation for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val toggleBorderColor by animateColorAsState(
        targetValue = when {
            isConnected -> CyberGreen
            isConnecting -> CyberCyan
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "border_color"
    )

    val bgGradient = if (isConnected) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                CyberGreen.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_status_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, toggleBorderColor.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isConnected) 4.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgGradient)
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row: Status label and Pinned Session tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsing Dot indicator
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(if (isConnected) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isConnected -> CyberGreen
                                        isConnecting -> CyberCyan
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isConnecting -> "CONNECTING..."
                                isConnected -> "CONNECTED"
                                else -> "DISCONNECTED"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isConnected -> CyberGreen
                                isConnecting -> CyberCyan
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (isConnected) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyberGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = CyberGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PINNED SESSION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberGreen
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "READY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Center area: Server Information & Big Single Toggle Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Server info details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (connectedConfig != null) {
                            Text(
                                text = connectedConfig.remark,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${connectedConfig.host}:${connectedConfig.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Protocol Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = connectedConfig.protocol,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Latency in ms Badge
                                val lat = connectedConfig.latencyMs
                                val (latColor, latText) = when {
                                    lat == null -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "-- ms")
                                    lat < 0 -> Pair(CyberRed, "Timeout")
                                    lat < 300 -> Pair(CyberGreen, "${lat} ms")
                                    lat < 800 -> Pair(CyberOrange, "${lat} ms")
                                    else -> Pair(CyberRed, "${lat} ms")
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = latColor.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = latColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = latText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = latColor
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "No Node Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap connect or auto-select lowest latency node",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Single Toggle Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected -> CyberGreen.copy(alpha = 0.2f)
                                    isConnecting -> CyberCyan.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable(
                                enabled = !isConnecting && !isSelectingBestNode,
                                onClick = onToggleConnection
                            )
                            .testTag("vpn_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = CyberCyan,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isConnected -> CyberGreen
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = if (isConnected) "Disconnect" else "Connect",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Best Node Selector Quick Action Button
                OutlinedButton(
                    onClick = onSelectBestNode,
                    enabled = !isConnecting && !isSelectingBestNode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("best_node_selector_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyberCyan
                    ),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
                ) {
                    if (isSelectingBestNode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing & Selecting Lowest Latency Node...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Best Node Selector (Auto Connect Fastest)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

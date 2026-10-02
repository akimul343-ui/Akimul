package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_configs")
data class ProxyConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUrl: String,
    val sourceName: String = "",
    val rawUri: String,
    val protocol: String, // VMess, VLESS, Trojan, Shadowsocks, Hysteria, etc.
    val remark: String,
    val host: String,
    val port: Int,
    val security: String = "none", // tls, reality, none
    val network: String = "tcp", // tcp, ws, grpc, kcp
    val path: String = "",
    val uuid: String = "",
    val latencyMs: Long? = null, // null = untested, -1 = timeout/error, >0 = ping ms
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

package com.example.data.parser

import android.util.Base64
import com.example.data.model.ProxyConfig
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object V2RayParser {

    fun parseSubscriptionContent(content: String, sourceUrl: String, sourceName: String = ""): List<ProxyConfig> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Check if the entire payload is a Base64 encoded string
        val decodedText = tryDecodeBase64(trimmed) ?: trimmed

        val lines = decodedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("#") }

        val configs = mutableListOf<ProxyConfig>()
        val seenUris = mutableSetOf<String>()

        for (line in lines) {
            if (seenUris.contains(line)) continue
            seenUris.add(line)

            val parsed = parseSingleConfig(line, sourceUrl, sourceName)
            if (parsed != null) {
                configs.add(parsed)
            }
        }

        return configs
    }

    fun parseSingleConfig(raw: String, sourceUrl: String = "", sourceName: String = ""): ProxyConfig? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVMess(trimmed, sourceUrl, sourceName)
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVLess(trimmed, sourceUrl, sourceName)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed, sourceUrl, sourceName)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed, sourceUrl, sourceName)
            trimmed.startsWith("hysteria2://", ignoreCase = true) || trimmed.startsWith("hy2://", ignoreCase = true) ->
                parseHysteria(trimmed, "Hysteria2", sourceUrl, sourceName)
            trimmed.startsWith("hysteria://", ignoreCase = true) ->
                parseHysteria(trimmed, "Hysteria", sourceUrl, sourceName)
            trimmed.startsWith("tuic://", ignoreCase = true) -> parseTuic(trimmed, sourceUrl, sourceName)
            trimmed.startsWith("wireguard://", ignoreCase = true) -> parseGeneric(trimmed, "WireGuard", sourceUrl, sourceName)
            trimmed.startsWith("socks5://", ignoreCase = true) || trimmed.startsWith("socks://", ignoreCase = true) ->
                parseGeneric(trimmed, "Socks5", sourceUrl, sourceName)
            else -> null
        }
    }

    private fun parseVMess(raw: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val base64Part = raw.substringAfter("vmess://").trim()
            val decodedJson = decodeBase64String(base64Part) ?: return parseGeneric(raw, "VMess", sourceUrl, sourceName)
            val json = JSONObject(decodedJson)

            val remark = json.optString("ps", "VMess Node").trim()
            val host = json.optString("add", "127.0.0.1").trim()
            val port = json.optInt("port", 443)
            val uuid = json.optString("id", "")
            val net = json.optString("net", "tcp")
            val tls = json.optString("tls", "none")
            val path = json.optString("path", "")

            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = "VMess",
                remark = if (remark.isNotEmpty()) remark else "VMess-$host:$port",
                host = host,
                port = port,
                security = if (tls.isNotEmpty()) tls else "none",
                network = if (net.isNotEmpty()) net else "tcp",
                path = path,
                uuid = uuid
            )
        } catch (e: Exception) {
            parseGeneric(raw, "VMess", sourceUrl, sourceName)
        }
    }

    private fun parseVLess(raw: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val uri = URI(raw)
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val uuid = uri.userInfo ?: ""
            val remark = decodeUrlFragment(uri.rawFragment, "VLESS-$host:$port")

            val queryParams = parseQueryParams(uri.rawQuery)
            val security = queryParams["security"] ?: "none"
            val network = queryParams["type"] ?: "tcp"
            val path = queryParams["path"] ?: ""

            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = "VLESS",
                remark = remark,
                host = host,
                port = port,
                security = security,
                network = network,
                path = path,
                uuid = uuid
            )
        } catch (e: Exception) {
            parseGeneric(raw, "VLESS", sourceUrl, sourceName)
        }
    }

    private fun parseTrojan(raw: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val uri = URI(raw)
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val password = uri.userInfo ?: ""
            val remark = decodeUrlFragment(uri.rawFragment, "Trojan-$host:$port")

            val queryParams = parseQueryParams(uri.rawQuery)
            val security = queryParams["security"] ?: "tls"
            val network = queryParams["type"] ?: "tcp"

            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = "Trojan",
                remark = remark,
                host = host,
                port = port,
                security = security,
                network = network,
                uuid = password
            )
        } catch (e: Exception) {
            parseGeneric(raw, "Trojan", sourceUrl, sourceName)
        }
    }

    private fun parseShadowsocks(raw: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val fragment = raw.substringAfter("#", "")
            val remark = if (fragment.isNotEmpty()) decodeUrl(fragment) else "Shadowsocks"
            val withoutFragment = raw.substringBefore("#").substringAfter("ss://")

            var host = ""
            var port = 8388

            if (withoutFragment.contains("@")) {
                val hostPart = withoutFragment.substringAfter("@")
                val hostPort = hostPart.substringBefore("/").substringBefore("?")
                val parts = hostPort.split(":")
                host = parts.firstOrNull() ?: ""
                port = parts.getOrNull(1)?.toIntOrNull() ?: 8388
            } else {
                // Entire string before fragment might be base64 encoded
                val decoded = decodeBase64String(withoutFragment)
                if (decoded != null && decoded.contains("@")) {
                    val hostPart = decoded.substringAfter("@")
                    val parts = hostPart.split(":")
                    host = parts.firstOrNull() ?: ""
                    port = parts.getOrNull(1)?.toIntOrNull() ?: 8388
                }
            }

            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = "Shadowsocks",
                remark = if (remark.isNotBlank()) remark else "SS-$host:$port",
                host = host.ifEmpty { "127.0.0.1" },
                port = port,
                security = "chacha/aes",
                network = "tcp"
            )
        } catch (e: Exception) {
            parseGeneric(raw, "Shadowsocks", sourceUrl, sourceName)
        }
    }

    private fun parseHysteria(raw: String, protocolName: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val uri = URI(raw)
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val remark = decodeUrlFragment(uri.rawFragment, "$protocolName-$host:$port")
            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = protocolName,
                remark = remark,
                host = host,
                port = port,
                security = "tls/udp",
                network = "udp"
            )
        } catch (e: Exception) {
            parseGeneric(raw, protocolName, sourceUrl, sourceName)
        }
    }

    private fun parseTuic(raw: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val uri = URI(raw)
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val remark = decodeUrlFragment(uri.rawFragment, "TUIC-$host:$port")
            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = "TUIC",
                remark = remark,
                host = host,
                port = port,
                security = "quic",
                network = "udp"
            )
        } catch (e: Exception) {
            parseGeneric(raw, "TUIC", sourceUrl, sourceName)
        }
    }

    private fun parseGeneric(raw: String, protocolName: String, sourceUrl: String, sourceName: String): ProxyConfig? {
        return try {
            val fragment = raw.substringAfter("#", "")
            val remark = if (fragment.isNotEmpty()) decodeUrl(fragment) else "$protocolName Node"
            val uriStr = raw.substringBefore("#")
            val colonSlash = uriStr.indexOf("://")
            val afterProto = if (colonSlash != -1) uriStr.substring(colonSlash + 3) else uriStr

            // Try to find host and port
            var host = "127.0.0.1"
            var port = 443
            val atIdx = afterProto.indexOf("@")
            val targetPart = if (atIdx != -1) afterProto.substring(atIdx + 1) else afterProto
            val hostPortStr = targetPart.substringBefore("/").substringBefore("?")
            if (hostPortStr.contains(":")) {
                val parts = hostPortStr.split(":")
                host = parts[0]
                port = parts.getOrNull(1)?.toIntOrNull() ?: 443
            } else if (hostPortStr.isNotEmpty()) {
                host = hostPortStr
            }

            ProxyConfig(
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                rawUri = raw,
                protocol = protocolName,
                remark = remark,
                host = host,
                port = port
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun tryDecodeBase64(str: String): String? {
        if (str.startsWith("vmess://") || str.startsWith("vless://") || str.startsWith("trojan://")) {
            return null
        }
        return decodeBase64String(str)
    }

    private fun decodeBase64String(base64Str: String): String? {
        val sanitized = base64Str.trim().replace("\r", "").replace("\n", "")
        return try {
            val bytes = Base64.decode(sanitized, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE)
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeUrlFragment(fragment: String?, fallback: String): String {
        if (fragment.isNullOrBlank()) return fallback
        return decodeUrl(fragment)
    }

    private fun decodeUrl(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            value
        }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = decodeUrl(pair.substring(0, idx))
                val value = decodeUrl(pair.substring(idx + 1))
                result[key] = value
            }
        }
        return result
    }
}

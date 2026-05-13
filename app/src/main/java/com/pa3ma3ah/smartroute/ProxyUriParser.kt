package com.pa3ma3ah.smartroute

import android.util.Base64
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ProxyUriParser {
    fun configFromInput(input: String): SmartRouteConfig {
        val links = allSupportedProxyLinks(input)

        if (links.isEmpty()) {
            throw IllegalArgumentException("No supported proxy links found")
        }

        val nodes = mutableListOf<SmartRouteNode>()
        val usedTags = mutableSetOf<String>()

        for (link in links) {
            try {
                val node = when {
                    link.startsWith("vless://", ignoreCase = true) -> parseVless(link)
                    else -> {
                        SmartRouteLogStore.add(
                            "Skipped unsupported proxy link: ${link.substringBefore("://")}"
                        )
                        null
                    }
                }

                if (node != null) {
                    val unique = node.copy(tag = uniqueTag(node.tag, usedTags))
                    usedTags.add(unique.tag)
                    nodes.add(unique)
                }
            } catch (e: Throwable) {
                SmartRouteLogStore.add("Skipped broken proxy link: ${e.message}")
            }
        }

        if (nodes.isEmpty()) {
            throw IllegalArgumentException("No supported nodes imported. Currently Android parser supports vless://")
        }

        SmartRouteLogStore.add("Imported nodes: ${nodes.size}")

        return SmartRouteConfig(
            general = SmartRouteGeneral(
                mode = "socks",
                listen = "127.0.0.1",
                listenPort = 1081,
                finalOutbound = nodes.first().tag,
                autoFix = false
            ),
            nodes = nodes,
            chains = emptyList(),
            rules = emptyList(),
            appRules = emptyList()
        )
    }

    private fun allSupportedProxyLinks(input: String): List<String> {
        val variants = mutableListOf(input.trim())

        tryDecodeBase64(input.trim())?.let {
            variants.add(it)
        }

        return variants
            .flatMap { extractLinks(it) }
            .distinct()
    }

    private fun extractLinks(text: String): List<String> {
        val normalized = text
            .replace("\r", "\n")
            .replace(" ", "\n")
            .replace("\t", "\n")

        return normalized
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter {
                it.startsWith("vless://", ignoreCase = true) ||
                        it.startsWith("vmess://", ignoreCase = true) ||
                        it.startsWith("trojan://", ignoreCase = true) ||
                        it.startsWith("ss://", ignoreCase = true) ||
                        it.startsWith("hysteria2://", ignoreCase = true) ||
                        it.startsWith("hy2://", ignoreCase = true) ||
                        it.startsWith("tuic://", ignoreCase = true)
            }
    }

    private fun tryDecodeBase64(text: String): String? {
        val compact = text
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        if (compact.isBlank()) return null

        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)

        val flags = listOf(
            Base64.DEFAULT,
            Base64.URL_SAFE,
            Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_WRAP
        )

        for (flag in flags) {
            try {
                val bytes = Base64.decode(padded, flag)
                val decoded = String(bytes, StandardCharsets.UTF_8)

                if (decoded.contains("://")) {
                    return decoded
                }
            } catch (_: Throwable) {
            }
        }

        return null
    }

    private fun parseVless(link: String): SmartRouteNode {
        val uri = URI(link)

        val uuid = uri.userInfo
            ?: throw IllegalArgumentException("VLESS link has no UUID")

        val server = uri.host
            ?: throw IllegalArgumentException("VLESS link has no server")

        val port = if (uri.port > 0) uri.port else 443
        val query = parseQuery(uri.rawQuery ?: "")

        val tag = decode(uri.rawFragment)
            .ifBlank { sanitizeTag(server) }

        val security = query["security"] ?: query["tls"] ?: "tls"
        val serverName = query["sni"]
            ?: query["serverName"]
            ?: query["servername"]
            ?: query["host"]

        val fp = query["fp"] ?: query["fingerprint"] ?: "chrome"
        val publicKey = query["pbk"] ?: query["publicKey"] ?: query["realityPublicKey"]
        val shortId = query["sid"] ?: query["shortId"] ?: ""
        val flow = query["flow"]

        return SmartRouteNode(
            tag = sanitizeTag(tag),
            type = "vless",
            server = server,
            port = port,
            uuid = uuid,
            flow = flow,
            security = security,
            serverName = serverName,
            utlsFingerprint = fp,
            realityPublicKey = publicKey,
            realityShortId = shortId
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()

        return rawQuery.split("&")
            .mapNotNull { part ->
                val eq = part.indexOf("=")
                if (eq <= 0) return@mapNotNull null

                val key = decode(part.substring(0, eq))
                val value = decode(part.substring(eq + 1))

                key to value
            }
            .toMap()
    }

    private fun decode(value: String?): String {
        if (value == null) return ""
        return URLDecoder.decode(value, "UTF-8")
    }

    private fun sanitizeTag(value: String): String {
        val sanitized = value
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')

        return sanitized.ifBlank { "proxy" }
    }

    private fun uniqueTag(base: String, used: Set<String>): String {
        if (base !in used) return base

        var i = 2
        while ("$base-$i" in used) {
            i++
        }

        return "$base-$i"
    }
}
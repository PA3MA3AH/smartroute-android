package com.pa3ma3ah.smartroute

import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigGenerator {
    fun generate(config: SmartRouteConfig): String {
        val root = JSONObject()

        root.put(
            "log",
            JSONObject()
                .put("level", "warn")
                .put("timestamp", true)
        )

        root.put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "udp")
                            .put("tag", "cloudflare")
                            .put("server", "1.1.1.1")
                    )
                )
                .put("final", "cloudflare")
        )

        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("type", "tun")
                    .put("tag", "tun-in")
                    .put("address", JSONArray().put("172.19.0.1/30"))
                    .put("auto_route", true)
                    .put("strict_route", false)
                    .put("stack", "mixed")
            )
        )

        val outbounds = JSONArray()

        outbounds.put(
            JSONObject()
                .put("type", "direct")
                .put("tag", "direct")
        )

        for (node in config.nodes) {
            outbounds.put(nodeToOutbound(node))
        }

        for (chain in config.chains) {
            for (outbound in chainToOutbounds(chain, config)) {
                outbounds.put(outbound)
            }
        }

        root.put("outbounds", outbounds)

        val route = JSONObject()
        val rules = JSONArray()

        for (rule in config.appRules) {
            val generated = appRuleToSingBoxRule(rule)
            if (generated != null) {
                rules.put(generated)
            }
        }

        for (rule in config.rules) {
            val generated = siteRuleToSingBoxRule(rule)
            if (generated != null) {
                rules.put(generated)
            }
        }

        if (rules.length() > 0) {
            route.put("rules", rules)
        }

        route.put("final", config.general.finalOutbound.ifBlank { "direct" })
        root.put("route", route)

        return root.toString(2)
    }

    private fun nodeToOutbound(
        node: SmartRouteNode,
        overrideTag: String? = null,
        detour: String? = null
    ): JSONObject {
        return when (node.type.lowercase()) {
            "vless" -> vlessOutbound(node, overrideTag, detour)

            "direct" -> JSONObject()
                .put("type", "direct")
                .put("tag", overrideTag ?: node.tag)

            else -> throw IllegalArgumentException("Unsupported node type on Android: ${node.type}")
        }
    }

    private fun vlessOutbound(
        node: SmartRouteNode,
        overrideTag: String? = null,
        detour: String? = null
    ): JSONObject {
        val uuid = node.uuid
            ?: throw IllegalArgumentException("VLESS node '${node.tag}' requires uuid")

        val outbound = JSONObject()
            .put("type", "vless")
            .put("tag", overrideTag ?: node.tag)
            .put("server", node.server)
            .put("server_port", node.port)
            .put("uuid", uuid)

        if (!node.flow.isNullOrBlank()) {
            outbound.put("flow", node.flow)
        }

        if (!detour.isNullOrBlank()) {
            outbound.put("detour", detour)
        }

        val security = node.security?.lowercase()

        if (security == "tls" || security == "reality") {
            val tls = JSONObject()
                .put("enabled", true)

            if (!node.serverName.isNullOrBlank()) {
                tls.put("server_name", node.serverName)
            }

            if (!node.utlsFingerprint.isNullOrBlank()) {
                tls.put(
                    "utls",
                    JSONObject()
                        .put("enabled", true)
                        .put("fingerprint", node.utlsFingerprint)
                )
            }

            if (security == "reality") {
                val publicKey = node.realityPublicKey
                    ?: throw IllegalArgumentException(
                        "VLESS Reality node '${node.tag}' requires reality_public_key"
                    )

                tls.put(
                    "reality",
                    JSONObject()
                        .put("enabled", true)
                        .put("public_key", publicKey)
                        .put("short_id", node.realityShortId ?: "")
                )
            }

            outbound.put("tls", tls)
        }

        return outbound
    }

    private fun chainToOutbounds(
        chain: SmartRouteChain,
        config: SmartRouteConfig
    ): List<JSONObject> {
        val hops = chain.outbounds.filter { it.isNotBlank() }
        if (hops.isEmpty()) return emptyList()

        if (hops.first() == "direct") {
            return listOf(
                JSONObject()
                    .put("type", "direct")
                    .put("tag", chain.tag)
            )
        }

        val generated = mutableListOf<JSONObject>()

        for (index in hops.indices) {
            val hop = hops[index]

            if (hop == "direct") {
                continue
            }

            val node = config.nodes.firstOrNull { it.tag == hop }
                ?: continue

            val currentTag = if (index == 0) {
                chain.tag
            } else {
                "${chain.tag}-hop-$index"
            }

            val next = hops.getOrNull(index + 1)

            val detour = when {
                next == null -> null
                next == "direct" -> "direct"
                else -> "${chain.tag}-hop-${index + 1}"
            }

            generated.add(
                nodeToOutbound(
                    node = node,
                    overrideTag = currentTag,
                    detour = detour
                )
            )
        }

        return generated
    }

    private fun siteRuleToSingBoxRule(rule: SmartRouteRule): JSONObject? {
        val obj = JSONObject()
            .put("outbound", rule.outbound)

        when (rule.type) {
            "domain" -> obj.put("domain", JSONArray().put(rule.value))
            "domain_suffix" -> obj.put("domain_suffix", JSONArray().put(rule.value))
            "domain_keyword" -> obj.put("domain_keyword", JSONArray().put(rule.value))
            "ip_cidr" -> obj.put("ip_cidr", JSONArray().put(rule.value))
            "geoip" -> obj.put("geoip", JSONArray().put(rule.value))
            "geosite" -> obj.put("geosite", JSONArray().put(rule.value))
            else -> return null
        }

        return obj
    }

    private fun appRuleToSingBoxRule(rule: SmartRouteAppRule): JSONObject? {
        if (rule.packageName.isBlank()) return null

        return JSONObject()
            .put("package_name", JSONArray().put(rule.packageName))
            .put("outbound", rule.outbound)
    }
}
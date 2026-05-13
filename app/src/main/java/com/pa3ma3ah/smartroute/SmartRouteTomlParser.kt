package com.pa3ma3ah.smartroute

object SmartRouteTomlParser {
    fun parse(text: String): SmartRouteConfig {
        val general = linkedMapOf<String, String>()
        val nodes = mutableListOf<LinkedHashMap<String, String>>()
        val chains = mutableListOf<LinkedHashMap<String, String>>()
        val rules = mutableListOf<LinkedHashMap<String, String>>()
        val appRules = mutableListOf<LinkedHashMap<String, String>>()

        var currentSection = ""
        var currentMap: LinkedHashMap<String, String>? = null

        for (rawLine in text.lines()) {
            val line = rawLine.substringBefore("#").trim()
            if (line.isEmpty()) continue

            when (line) {
                "[general]" -> {
                    currentSection = "general"
                    currentMap = general
                    continue
                }

                "[[nodes]]" -> {
                    currentSection = "nodes"
                    val map = linkedMapOf<String, String>()
                    nodes.add(map)
                    currentMap = map
                    continue
                }

                "[[chains]]" -> {
                    currentSection = "chains"
                    val map = linkedMapOf<String, String>()
                    chains.add(map)
                    currentMap = map
                    continue
                }

                "[[rules]]" -> {
                    currentSection = "rules"
                    val map = linkedMapOf<String, String>()
                    rules.add(map)
                    currentMap = map
                    continue
                }

                "[[app_rules]]" -> {
                    currentSection = "app_rules"
                    val map = linkedMapOf<String, String>()
                    appRules.add(map)
                    currentMap = map
                    continue
                }
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = ""
                currentMap = null
                continue
            }

            val eq = line.indexOf("=")
            if (eq <= 0) continue

            val key = line.substring(0, eq).trim()
            val value = parseValue(line.substring(eq + 1).trim())

            when (currentSection) {
                "general", "nodes", "chains", "rules", "app_rules" -> currentMap?.set(key, value)
            }
        }

        val parsedGeneral = SmartRouteGeneral(
            mode = general["mode"] ?: "socks",
            listen = general["listen"] ?: "127.0.0.1",
            listenPort = general["listen_port"]?.toIntOrNull() ?: 1081,
            finalOutbound = general["final_outbound"] ?: "direct",
            autoFix = parseBool(general["auto_fix"])
        )

        val parsedNodes = nodes.mapNotNull { map ->
            val tag = map["tag"] ?: return@mapNotNull null
            val type = map["type"] ?: return@mapNotNull null
            val server = map["server"] ?: return@mapNotNull null
            val port = map["port"]?.toIntOrNull() ?: return@mapNotNull null

            SmartRouteNode(
                tag = tag,
                type = type,
                server = server,
                port = port,

                uuid = map["uuid"],
                flow = map["flow"],

                security = map["security"],
                serverName = map["server_name"],
                utlsFingerprint = map["utls_fingerprint"],
                realityPublicKey = map["reality_public_key"],
                realityShortId = map["reality_short_id"],

                password = map["password"],
                method = map["method"],
                obfs = map["obfs"],
                obfsPassword = map["obfs_password"]
            )
        }

        val parsedChains = chains.mapNotNull { map ->
            val tag = map["tag"] ?: return@mapNotNull null
            val outbounds = parseStringArray(map["outbounds"])

            if (outbounds.isEmpty()) return@mapNotNull null

            SmartRouteChain(
                tag = tag,
                outbounds = outbounds
            )
        }

        val parsedRules = rules.mapNotNull { map ->
            val type = map["type"] ?: map["rule_type"] ?: return@mapNotNull null
            val value = map["value"] ?: return@mapNotNull null
            val outbound = map["outbound"] ?: return@mapNotNull null

            SmartRouteRule(
                type = type,
                value = value,
                outbound = outbound,
                autoFix = parseBool(map["auto_fix"])
            )
        }

        val parsedAppRules = appRules.mapNotNull { map ->
            val packageName = map["package"] ?: map["package_name"] ?: return@mapNotNull null
            val name = map["name"] ?: packageName
            val outbound = map["outbound"] ?: return@mapNotNull null

            SmartRouteAppRule(
                packageName = packageName,
                name = name,
                outbound = outbound,
                autoFix = parseBool(map["auto_fix"])
            )
        }

        return SmartRouteConfig(
            general = parsedGeneral,
            nodes = parsedNodes,
            chains = parsedChains,
            rules = parsedRules,
            appRules = parsedAppRules
        )
    }

    private fun parseValue(raw: String): String {
        val trimmed = raw.trim().trimEnd(',')

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        return trimmed
    }

    private fun parseBool(value: String?): Boolean {
        return value?.trim()?.lowercase() == "true"
    }

    private fun parseStringArray(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()

        val trimmed = value.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return emptyList()
        }

        return trimmed
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim() }
            .map {
                if (it.startsWith("\"") && it.endsWith("\"") && it.length >= 2) {
                    it.substring(1, it.length - 1)
                } else {
                    it
                }
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
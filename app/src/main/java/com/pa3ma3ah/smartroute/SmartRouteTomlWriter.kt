package com.pa3ma3ah.smartroute

object SmartRouteTomlWriter {
    fun write(config: SmartRouteConfig): String {
        val out = StringBuilder()

        out.appendLine("[general]")
        out.appendLine("mode = ${q(config.general.mode)}")
        out.appendLine("listen = ${q(config.general.listen)}")
        out.appendLine("listen_port = ${config.general.listenPort}")
        out.appendLine("final_outbound = ${q(config.general.finalOutbound)}")
        out.appendLine("auto_fix = ${config.general.autoFix}")
        out.appendLine()

        for (node in config.nodes) {
            out.appendLine("[[nodes]]")
            out.appendLine("tag = ${q(node.tag)}")
            out.appendLine("type = ${q(node.type)}")
            out.appendLine("server = ${q(node.server)}")
            out.appendLine("port = ${node.port}")

            node.uuid?.let { out.appendLine("uuid = ${q(it)}") }
            node.flow?.let { out.appendLine("flow = ${q(it)}") }

            node.security?.let { out.appendLine("security = ${q(it)}") }
            node.serverName?.let { out.appendLine("server_name = ${q(it)}") }
            node.utlsFingerprint?.let { out.appendLine("utls_fingerprint = ${q(it)}") }
            node.realityPublicKey?.let { out.appendLine("reality_public_key = ${q(it)}") }
            node.realityShortId?.let { out.appendLine("reality_short_id = ${q(it)}") }

            node.password?.let { out.appendLine("password = ${q(it)}") }
            node.method?.let { out.appendLine("method = ${q(it)}") }
            node.obfs?.let { out.appendLine("obfs = ${q(it)}") }
            node.obfsPassword?.let { out.appendLine("obfs_password = ${q(it)}") }

            out.appendLine()
        }

        for (chain in config.chains) {
            out.appendLine("[[chains]]")
            out.appendLine("tag = ${q(chain.tag)}")
            out.appendLine("outbounds = ${array(chain.outbounds)}")
            out.appendLine()
        }

        for (rule in config.rules) {
            out.appendLine("[[rules]]")
            out.appendLine("type = ${q(rule.type)}")
            out.appendLine("value = ${q(rule.value)}")
            out.appendLine("outbound = ${q(rule.outbound)}")
            out.appendLine("auto_fix = ${rule.autoFix}")
            out.appendLine()
        }

        for (rule in config.appRules) {
            out.appendLine("[[app_rules]]")
            out.appendLine("package = ${q(rule.packageName)}")
            out.appendLine("name = ${q(rule.name)}")
            out.appendLine("outbound = ${q(rule.outbound)}")
            out.appendLine("auto_fix = ${rule.autoFix}")
            out.appendLine()
        }

        return out.toString().trimEnd() + "\n"
    }

    private fun q(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }

    private fun array(values: List<String>): String {
        return values.joinToString(prefix = "[", postfix = "]") { q(it) }
    }
}
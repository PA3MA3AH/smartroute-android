package com.pa3ma3ah.smartroute

data class SmartRouteConfig(
    val general: SmartRouteGeneral = SmartRouteGeneral(),
    val nodes: List<SmartRouteNode> = emptyList(),
    val chains: List<SmartRouteChain> = emptyList(),
    val rules: List<SmartRouteRule> = emptyList(),
    val appRules: List<SmartRouteAppRule> = emptyList()
)

data class SmartRouteGeneral(
    val mode: String = "socks",
    val listen: String = "127.0.0.1",
    val listenPort: Int = 1081,
    val finalOutbound: String = "direct",
    val autoFix: Boolean = false
)

data class SmartRouteNode(
    val tag: String,
    val type: String,
    val server: String,
    val port: Int,

    val uuid: String? = null,
    val flow: String? = null,

    val security: String? = null,
    val serverName: String? = null,
    val utlsFingerprint: String? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,

    val password: String? = null,
    val method: String? = null,
    val obfs: String? = null,
    val obfsPassword: String? = null
)

data class SmartRouteChain(
    val tag: String,
    val outbounds: List<String>
)

data class SmartRouteRule(
    val type: String,
    val value: String,
    val outbound: String,
    val autoFix: Boolean = false
)

data class SmartRouteAppRule(
    val packageName: String,
    val name: String,
    val outbound: String,
    val autoFix: Boolean = false
)
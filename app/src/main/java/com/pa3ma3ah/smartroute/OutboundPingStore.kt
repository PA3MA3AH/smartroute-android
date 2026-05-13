package com.pa3ma3ah.smartroute

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

enum class PingState {
    UNKNOWN,
    CHECKING,
    OK,
    FAILED
}

data class OutboundPingResult(
    val state: PingState = PingState.UNKNOWN,
    val latencyMs: Long? = null,
    val error: String? = null
) {
    fun label(): String {
        return when (state) {
            PingState.UNKNOWN -> "ping: —"
            PingState.CHECKING -> "ping: ..."
            PingState.OK -> "ping: ${latencyMs ?: 0}ms"
            PingState.FAILED -> "ping: fail"
        }
    }

    fun isOk(): Boolean {
        return state == PingState.OK
    }
}

object OutboundPingStore {
    private val main = Handler(Looper.getMainLooper())

    val results = mutableStateMapOf<String, OutboundPingResult>()

    fun pingAll(
        config: SmartRouteConfig,
        timeoutMs: Int = 2500,
        onDone: ((Map<String, OutboundPingResult>) -> Unit)? = null
    ) {
        val tags = availableOutboundTags(config)

        for (tag in tags) {
            results[tag] = OutboundPingResult(PingState.CHECKING)
        }

        Thread {
            val localResults = linkedMapOf<String, OutboundPingResult>()

            for (tag in tags) {
                val result = pingOutbound(tag, config, timeoutMs)
                localResults[tag] = result

                main.post {
                    results[tag] = result
                }
            }

            if (onDone != null) {
                main.post {
                    onDone(localResults)
                }
            }
        }.start()
    }

    fun availableOutboundTags(config: SmartRouteConfig): List<String> {
        return (listOf("direct") + config.nodes.map { it.tag } + config.chains.map { it.tag })
            .distinct()
    }

    private fun pingOutbound(
        tag: String,
        config: SmartRouteConfig,
        timeoutMs: Int
    ): OutboundPingResult {
        val target = resolvePingTarget(tag, config, mutableSetOf())
            ?: return OutboundPingResult(PingState.FAILED, error = "no target")

        return try {
            var elapsed: Long

            Socket().use { socket ->
                elapsed = measureTimeMillis {
                    socket.connect(
                        InetSocketAddress(target.host, target.port),
                        timeoutMs
                    )
                }
            }

            OutboundPingResult(PingState.OK, latencyMs = elapsed)
        } catch (e: Throwable) {
            OutboundPingResult(
                state = PingState.FAILED,
                error = e.message ?: e::class.java.simpleName
            )
        }
    }

    private fun resolvePingTarget(
        tag: String,
        config: SmartRouteConfig,
        visited: MutableSet<String>
    ): PingTarget? {
        if (!visited.add(tag)) return null

        if (tag == "direct") {
            return PingTarget("google.com", 443)
        }

        val node = config.nodes.firstOrNull { it.tag == tag }
        if (node != null) {
            return PingTarget(node.server, node.port)
        }

        val chain = config.chains.firstOrNull { it.tag == tag }
        if (chain != null) {
            val first = chain.outbounds.firstOrNull() ?: return null
            return resolvePingTarget(first, config, visited)
        }

        return null
    }

    private data class PingTarget(
        val host: String,
        val port: Int
    )
}
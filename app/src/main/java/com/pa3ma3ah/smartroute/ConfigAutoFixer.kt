package com.pa3ma3ah.smartroute

object ConfigAutoFixer {
    fun fixConfig(
        config: SmartRouteConfig,
        pings: Map<String, OutboundPingResult>
    ): SmartRouteConfig {
        val best = bestAvailableOutbound(config, pings)

        val fixedGeneral = if (
            config.general.autoFix &&
            !isUsable(config.general.finalOutbound, pings) &&
            best != null
        ) {
            SmartRouteLogStore.add(
                "Auto-fix global: ${config.general.finalOutbound} -> $best"
            )
            config.general.copy(finalOutbound = best)
        } else {
            config.general
        }

        val fixedRules = config.rules.map { rule ->
            if (rule.autoFix && !isUsable(rule.outbound, pings) && best != null) {
                SmartRouteLogStore.add(
                    "Auto-fix site rule ${rule.value}: ${rule.outbound} -> $best"
                )
                rule.copy(outbound = best)
            } else {
                rule
            }
        }

        val fixedAppRules = config.appRules.map { rule ->
            if (rule.autoFix && !isUsable(rule.outbound, pings) && best != null) {
                SmartRouteLogStore.add(
                    "Auto-fix app rule ${rule.name}: ${rule.outbound} -> $best"
                )
                rule.copy(outbound = best)
            } else {
                rule
            }
        }

        return config.copy(
            general = fixedGeneral,
            rules = fixedRules,
            appRules = fixedAppRules
        )
    }

    private fun bestAvailableOutbound(
        config: SmartRouteConfig,
        pings: Map<String, OutboundPingResult>
    ): String? {
        val tags = OutboundPingStore.availableOutboundTags(config)

        return tags
            .filter { tag -> pings[tag]?.isOk() == true }
            .minByOrNull { tag -> pings[tag]?.latencyMs ?: Long.MAX_VALUE }
    }

    private fun isUsable(
        outbound: String,
        pings: Map<String, OutboundPingResult>
    ): Boolean {
        val result = pings[outbound] ?: return true
        return result.isOk()
    }
}
package com.pa3ma3ah.smartroute

import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.SystemProxyStatus

class AndroidCommandServerHandler : CommandServerHandler {
    override fun getSystemProxyStatus(): SystemProxyStatus {
        SmartRouteLogStore.add("CommandServerHandler.getSystemProxyStatus")
        return SystemProxyStatus()
    }

    override fun serviceReload() {
        SmartRouteLogStore.add("CommandServerHandler.serviceReload")
    }

    override fun serviceStop() {
        SmartRouteLogStore.add("CommandServerHandler.serviceStop")
        SmartRouteEngine.stop()
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        SmartRouteLogStore.add("CommandServerHandler.setSystemProxyEnabled: $enabled")
    }

    override fun writeDebugMessage(message: String?) {
        SmartRouteLogStore.add("libbox debug: ${message ?: ""}")
    }
}
package com.pa3ma3ah.smartroute

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File

object SmartRouteEngine {
    private var running: Boolean = false
    private var currentConfigPath: String = "unknown"

    @Synchronized
    fun start(
        context: Context,
        configPath: String,
        vpnInterface: ParcelFileDescriptor?
    ) {
        if (running) {
            SmartRouteLogStore.add("Engine already running")
            return
        }

        currentConfigPath = configPath
        running = true

        SmartRouteLogStore.add("Engine starting")
        SmartRouteLogStore.add("Config path: $configPath")

        val configFile = File(configPath)

        if (configFile.exists()) {
            SmartRouteLogStore.add("Config found: ${configFile.length()} bytes")
        } else {
            SmartRouteLogStore.add("Warning: config file does not exist")
        }

        if (vpnInterface != null) {
            SmartRouteLogStore.add("VPN interface fd opened")
        } else {
            SmartRouteLogStore.add("Warning: VPN interface is null")
        }

        /*
         * Пока это заглушка.
         *
         * Следующий этап:
         * - положим sing-box binary/core в Android-проект;
         * - сгенерируем sing-box JSON config;
         * - передадим VPN fd / tun параметры в engine;
         * - запустим реальный routing.
         */
        SmartRouteLogStore.add("Engine stub started")
    }

    @Synchronized
    fun stop() {
        if (!running) {
            SmartRouteLogStore.add("Engine already stopped")
            return
        }

        running = false
        SmartRouteLogStore.add("Engine stopped")
    }

    @Synchronized
    fun isRunning(): Boolean {
        return running
    }

    @Synchronized
    fun configPath(): String {
        return currentConfigPath
    }
}
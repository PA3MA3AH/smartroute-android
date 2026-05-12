package com.pa3ma3ah.smartroute

import android.content.Context
import android.os.ParcelFileDescriptor
import io.nekohasekai.libbox.Libbox
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
        SmartRouteLogStore.add("SmartRoute config path: $configPath")

        try {
            SmartRouteLogStore.add("libbox version: ${Libbox.version()}")
        } catch (e: Throwable) {
            SmartRouteLogStore.add("ERROR: failed to read libbox version: ${e.message}")
        }

        val smartRouteConfig = File(configPath)

        if (smartRouteConfig.exists()) {
            SmartRouteLogStore.add("SmartRoute config found: ${smartRouteConfig.length()} bytes")
        } else {
            SmartRouteLogStore.add("Warning: SmartRoute config file does not exist")
        }

        if (vpnInterface != null) {
            SmartRouteLogStore.add("VPN interface fd opened")
        } else {
            SmartRouteLogStore.add("Warning: VPN interface is null")
        }

        val singBoxConfig = generateTestSingBoxJson(context)

        SmartRouteLogStore.add("Generated sing-box config: ${singBoxConfig.absolutePath}")
        SmartRouteLogStore.add("Generated sing-box config size: ${singBoxConfig.length()} bytes")

        checkSingBoxConfig(singBoxConfig)

        SmartRouteLogStore.add("Engine validation layer started")
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

    private fun generateTestSingBoxJson(context: Context): File {
        val file = File(context.filesDir, "sing-box-generated.json")

        val json = """
{
  "log": {
    "level": "debug",
    "timestamp": true
  },
  "inbounds": [
    {
      "type": "mixed",
      "tag": "mixed-in",
      "listen": "127.0.0.1",
      "listen_port": 2080
    }
  ],
  "outbounds": [
    {
      "type": "direct",
      "tag": "direct"
    }
  ],
  "route": {
    "final": "direct"
  }
}
""".trimIndent()

        file.writeText(json)
        return file
    }

    private fun checkSingBoxConfig(configFile: File) {
        try {
            val json = configFile.readText()
            Libbox.checkConfig(json)
            SmartRouteLogStore.add("Libbox.checkConfig(json) OK")
        } catch (error: Throwable) {
            SmartRouteLogStore.add("Libbox.checkConfig(json) failed: ${error.message}")
            SmartRouteLogStore.add("ERROR: generated sing-box config is invalid")
        }
    }
}
package com.pa3ma3ah.smartroute

import android.net.VpnService
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import java.util.UUID

object SmartRouteEngine {
    private var running: Boolean = false
    private var setupDone: Boolean = false
    private var currentConfigPath: String = "unknown"
    private var commandServer: CommandServer? = null

    @Synchronized
    fun start(
        context: VpnService,
        configPath: String
    ) {
        if (running) {
            SmartRouteLogStore.add("Engine already running")
            return
        }

        currentConfigPath = configPath

        SmartRouteLogStore.add("Engine starting")
        SmartRouteLogStore.add("SmartRoute config path: $configPath")

        try {
            SmartRouteLogStore.add("libbox version: ${Libbox.version()}")
        } catch (e: Throwable) {
            SmartRouteLogStore.add("ERROR: failed to read libbox version: ${e.message}")
            return
        }

        val smartRouteConfig = File(configPath)

        if (smartRouteConfig.exists()) {
            SmartRouteLogStore.add("SmartRoute config found: ${smartRouteConfig.length()} bytes")
        } else {
            SmartRouteLogStore.add("Warning: SmartRoute config file does not exist")
        }

        val singBoxConfig = generateTunTestSingBoxJson(context)
        val configJson = singBoxConfig.readText()

        SmartRouteLogStore.add("Generated TUN sing-box config: ${singBoxConfig.absolutePath}")
        SmartRouteLogStore.add("Generated TUN config size: ${singBoxConfig.length()} bytes")

        if (!checkSingBoxConfig(configJson)) {
            SmartRouteLogStore.add("Engine start aborted: invalid config")
            return
        }

        try {
            setupLibbox(context)

            val platform = AndroidLibboxPlatform(context)
            val handler = AndroidCommandServerHandler()
            val server = CommandServer(handler, platform)

            commandServer = server

            SmartRouteLogStore.add("CommandServer created")

            server.start()
            SmartRouteLogStore.add("CommandServer started")

            server.startOrReloadService(configJson, OverrideOptions())
            SmartRouteLogStore.add("libbox TUN service started/reloaded")

            running = true

            SmartRouteLogStore.add("Engine started with libbox TUN")
        } catch (e: Throwable) {
            SmartRouteLogStore.add(
                "ERROR: failed to start libbox TUN service: ${e::class.java.simpleName}: ${e.message}"
            )

            try {
                commandServer?.close()
            } catch (_: Throwable) {
            }

            commandServer = null
            running = false
        }
    }

    @Synchronized
    fun stop() {
        if (!running && commandServer == null) {
            SmartRouteLogStore.add("Engine already stopped")
            return
        }

        SmartRouteLogStore.add("Engine stopping")

        try {
            commandServer?.closeService()
            SmartRouteLogStore.add("libbox service closed")
        } catch (e: Throwable) {
            SmartRouteLogStore.add("Warning: closeService failed: ${e.message}")
        }

        try {
            commandServer?.close()
            SmartRouteLogStore.add("CommandServer closed")
        } catch (e: Throwable) {
            SmartRouteLogStore.add("Warning: CommandServer close failed: ${e.message}")
        }

        commandServer = null
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

    private fun setupLibbox(context: VpnService) {
        if (setupDone) {
            SmartRouteLogStore.add("Libbox.setup already done")
            return
        }

        val baseDir = File(context.filesDir, "libbox")
        val workDir = File(baseDir, "work")
        val tempDir = File(context.cacheDir, "libbox-temp")

        baseDir.mkdirs()
        workDir.mkdirs()
        tempDir.mkdirs()

        val options = SetupOptions().apply {
            basePath = baseDir.absolutePath
            workingPath = workDir.absolutePath
            tempPath = tempDir.absolutePath
            fixAndroidStack = true
            commandServerListenPort = 0
            commandServerSecret = UUID.randomUUID().toString()
            logMaxLines = 300
            debug = true
        }

        Libbox.setup(options)
        setupDone = true

        SmartRouteLogStore.add("Libbox.setup OK")
        SmartRouteLogStore.add("Libbox basePath: ${baseDir.absolutePath}")
        SmartRouteLogStore.add("Libbox workingPath: ${workDir.absolutePath}")
    }

    private fun generateTunTestSingBoxJson(context: VpnService): File {
        val file = File(context.filesDir, "sing-box-generated-tun.json")

        val json = """
{
  "log": {
    "level": "debug",
    "timestamp": true
  },
  "dns": {
    "servers": [
      {
        "type": "udp",
        "tag": "cloudflare",
        "server": "1.1.1.1"
      }
    ],
    "final": "cloudflare"
  },
  "inbounds": [
    {
      "type": "tun",
      "tag": "tun-in",
      "address": [
        "172.19.0.1/30"
      ],
      "auto_route": true,
      "strict_route": false,
      "stack": "mixed"
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

    private fun checkSingBoxConfig(configJson: String): Boolean {
        return try {
            Libbox.checkConfig(configJson)
            SmartRouteLogStore.add("Libbox.checkConfig(json) OK")
            true
        } catch (error: Throwable) {
            SmartRouteLogStore.add("Libbox.checkConfig(json) failed: ${error.message}")
            SmartRouteLogStore.add("ERROR: generated sing-box config is invalid")
            false
        }
    }
}
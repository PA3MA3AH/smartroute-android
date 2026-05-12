package com.pa3ma3ah.smartroute

import android.content.Context
import android.os.ParcelFileDescriptor
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import java.util.UUID

object SmartRouteEngine {
    private var running: Boolean = false
    private var currentConfigPath: String = "unknown"
    private var commandServer: CommandServer? = null

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

        if (vpnInterface != null) {
            SmartRouteLogStore.add("VPN interface fd opened")
        } else {
            SmartRouteLogStore.add("Warning: VPN interface is null")
        }

        val singBoxConfig = generateTestSingBoxJson(context)
        val configJson = singBoxConfig.readText()

        SmartRouteLogStore.add("Generated sing-box config: ${singBoxConfig.absolutePath}")
        SmartRouteLogStore.add("Generated sing-box config size: ${singBoxConfig.length()} bytes")

        if (!checkSingBoxConfig(configJson)) {
            SmartRouteLogStore.add("Engine start aborted: invalid config")
            return
        }

        try {
            setupLibbox(context)

            val platform = AndroidLibboxPlatform(context.applicationContext) { vpnInterface }
            val handler = AndroidCommandServerHandler()
            val server = CommandServer(handler, platform)

            SmartRouteLogStore.add("CommandServer created")

            server.start()
            SmartRouteLogStore.add("CommandServer started")

            server.startOrReloadService(configJson, OverrideOptions())
            SmartRouteLogStore.add("libbox service started/reloaded")

            commandServer = server
            running = true

            SmartRouteLogStore.add("Engine started with libbox CommandServer")
        } catch (e: Throwable) {
            SmartRouteLogStore.add("ERROR: failed to start libbox service: ${e::class.java.simpleName}: ${e.message}")

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

    private fun setupLibbox(context: Context) {
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

        SmartRouteLogStore.add("Libbox.setup OK")
        SmartRouteLogStore.add("Libbox basePath: ${baseDir.absolutePath}")
        SmartRouteLogStore.add("Libbox workingPath: ${workDir.absolutePath}")
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
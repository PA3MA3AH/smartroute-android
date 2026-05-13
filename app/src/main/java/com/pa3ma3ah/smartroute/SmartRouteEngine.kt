package com.pa3ma3ah.smartroute

import android.content.Context
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

        val configJson = try {
            generateJsonFromSmartRouteConfig(context, configPath)
        } catch (e: Throwable) {
            SmartRouteLogStore.add("ERROR: failed to generate sing-box config: ${e.message}")
            return
        }

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
            SmartRouteLogStore.add("libbox service started")

            running = true

            SmartRouteLogStore.add("Engine started with SmartRoute config")
        } catch (e: Throwable) {
            SmartRouteLogStore.add(
                "ERROR: failed to start libbox service: ${e::class.java.simpleName}: ${e.message}"
            )

            try {
                commandServer?.closeService()
            } catch (_: Throwable) {
            }

            try {
                commandServer?.close()
            } catch (_: Throwable) {
            }

            commandServer = null
            running = false
        }
    }

    @Synchronized
    fun reloadFromConfig(context: Context, configPath: String): Boolean {
        SmartRouteLogStore.add("Config saved")

        val configJson = try {
            generateJsonFromSmartRouteConfig(context, configPath)
        } catch (e: Throwable) {
            SmartRouteLogStore.add("ERROR: saved config generation failed: ${e.message}")
            return false
        }

        if (!checkSingBoxConfig(configJson)) {
            SmartRouteLogStore.add("Saved config is invalid")
            return false
        }

        if (running) {
            SmartRouteLogStore.add("Live reload disabled for Android TUN stability")
            SmartRouteLogStore.add("Apply changes manually: Stop VPN -> Start VPN")
            return false
        }

        SmartRouteLogStore.add("VPN is not running; config will be used on next start")
        return false
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

    private fun generateJsonFromSmartRouteConfig(context: Context, configPath: String): String {
        val smartRouteConfigFile = File(configPath)

        if (!smartRouteConfigFile.exists()) {
            throw IllegalArgumentException("SmartRoute config file does not exist")
        }

        SmartRouteLogStore.add("SmartRoute config found: ${smartRouteConfigFile.length()} bytes")

        val toml = smartRouteConfigFile.readText()
        val parsed = SmartRouteTomlParser.parse(toml)

        SmartRouteLogStore.add("Parsed nodes: ${parsed.nodes.size}")
        SmartRouteLogStore.add("Parsed site rules: ${parsed.rules.size}")
        SmartRouteLogStore.add("Parsed app rules: ${parsed.appRules.size}")
        SmartRouteLogStore.add("Final outbound: ${parsed.general.finalOutbound}")

        val json = SingBoxConfigGenerator.generate(parsed)
        val generatedFile = File(context.filesDir, "sing-box-generated-proxy.json")
        generatedFile.writeText(json)

        SmartRouteLogStore.add("Generated sing-box config: ${generatedFile.absolutePath}")
        SmartRouteLogStore.add("Generated config size: ${generatedFile.length()} bytes")

        return json
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

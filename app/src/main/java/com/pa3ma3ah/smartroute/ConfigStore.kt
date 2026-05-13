package com.pa3ma3ah.smartroute

import android.content.Context
import java.io.File

object ConfigStore {
    private const val CONFIG_FILE_NAME = "config.toml"

    fun configFile(context: Context): File {
        return File(context.filesDir, CONFIG_FILE_NAME)
    }

    fun configPath(context: Context): String {
        return configFile(context).absolutePath
    }

    fun hasUsableConfig(context: Context): Boolean {
        val file = configFile(context)
        return file.exists() && file.length() > 0
    }

    fun loadConfig(context: Context): String {
        val file = configFile(context)

        if (!file.exists()) {
            val example = exampleConfig()
            file.writeText(example)
            return example
        }

        return file.readText()
    }

    fun saveConfig(context: Context, text: String) {
        configFile(context).writeText(text)
    }

    fun saveConfig(context: Context, config: SmartRouteConfig) {
        saveConfig(context, SmartRouteTomlWriter.write(config))
    }

    fun exampleConfig(): String {
        return """
[general]
mode = "socks"
listen = "127.0.0.1"
listen_port = 1081
final_outbound = "direct"
""".trimIndent()
    }

    fun vlessExampleConfig(): String {
        return """
[general]
mode = "socks"
listen = "127.0.0.1"
listen_port = 1081
final_outbound = "my-proxy"

[[nodes]]
tag = "my-proxy"
type = "vless"
server = "example.com"
port = 443
uuid = "your-uuid"
security = "reality"
server_name = "example.com"
reality_public_key = "your-public-key"
reality_short_id = ""
utls_fingerprint = "chrome"

[[rules]]
type = "domain_suffix"
value = "youtube.com"
outbound = "my-proxy"
""".trimIndent()
    }
}
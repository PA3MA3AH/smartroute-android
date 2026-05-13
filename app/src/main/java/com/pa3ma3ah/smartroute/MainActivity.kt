package com.pa3ma3ah.smartroute

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.pa3ma3ah.smartroute.ui.theme.SmartRouteAndroidTheme
import java.net.URL

enum class AppScreen {
    FirstRun,
    Main,
    ConfigSettings
}

class MainActivity : ComponentActivity() {
    private var statusState: MutableState<String>? = null
    private var screenState: MutableState<AppScreen>? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                SmartRouteLogStore.add("VPN permission granted")
                startVpnService()
                statusState?.value = "Running"
            } else {
                SmartRouteLogStore.add("VPN permission denied")
                statusState?.value = "Stopped"
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val configFilePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                SmartRouteLogStore.add("Config file picker cancelled")
                return@registerForActivityResult
            }

            try {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw IllegalArgumentException("Cannot read selected file")

                ConfigStore.saveConfig(this, text)
                SmartRouteLogStore.add("Config imported from file")
                screenState?.value = AppScreen.Main
                Toast.makeText(this, "Config imported", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                SmartRouteLogStore.add("ERROR: failed to import config file: ${e.message}")
                Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activity = this

        SmartRouteLogStore.add("MainActivity created")

        setContent {
            SmartRouteAndroidTheme {
                val status = remember {
                    mutableStateOf(
                        if (SmartRouteEngine.isRunning()) "Running" else "Stopped"
                    )
                }

                val screen = remember {
                    mutableStateOf(
                        if (ConfigStore.hasUsableConfig(activity)) {
                            AppScreen.Main
                        } else {
                            AppScreen.FirstRun
                        }
                    )
                }

                statusState = status
                screenState = screen

                when (screen.value) {
                    AppScreen.FirstRun -> FirstRunScreen(
                        onUseExample = {
                            ConfigStore.saveConfig(activity, ConfigStore.exampleConfig())
                            SmartRouteLogStore.add("Direct example config created")
                            screen.value = AppScreen.Main
                        },
                        onPickFile = {
                            configFilePicker.launch("*/*")
                        },
                        onImportLink = { input ->
                            importConfigFromLinkOrSubscription(input, screen)
                        }
                    )

                    AppScreen.Main -> MainScreen(
                        activity = activity,
                        status = status.value,
                        onStart = {
                            ConfigStore.loadConfig(activity)
                            SmartRouteLogStore.add("Start VPN clicked")
                            status.value = "Starting..."
                            requestVpnPermission()
                        },
                        onStop = {
                            SmartRouteLogStore.add("Stop VPN clicked")
                            stopVpnService()
                            status.value = "Stopped"
                        },
                        onOpenSettings = {
                            screen.value = AppScreen.ConfigSettings
                        },
                        onResetConfig = {
                            screen.value = AppScreen.FirstRun
                        }
                    )

                    AppScreen.ConfigSettings -> ConfigSettingsScreen(
                        activity = activity,
                        onBack = {
                            screen.value = AppScreen.Main
                        }
                    )
                }
            }
        }
    }

    private fun importConfigFromLinkOrSubscription(
        input: String,
        screen: MutableState<AppScreen>
    ) {
        val trimmed = input.trim()

        if (trimmed.isBlank()) {
            Toast.makeText(this, "Empty input", Toast.LENGTH_SHORT).show()
            return
        }

        SmartRouteLogStore.add("Import requested")

        Thread {
            try {
                val content = if (
                    trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    SmartRouteLogStore.add("Downloading subscription URL")
                    URL(trimmed).openConnection().apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }.getInputStream().bufferedReader().use { it.readText() }
                } else {
                    trimmed
                }

                val config = ProxyUriParser.configFromInput(content)

                runOnUiThread {
                    SmartRouteLogStore.add("Imported nodes count: ${config.nodes.size}")
                    SmartRouteLogStore.add("Default outbound: ${config.general.finalOutbound}")
                    ConfigStore.saveConfig(this, config)
                    SmartRouteLogStore.add("Config created from proxy link/subscription")
                    screen.value = AppScreen.Main
                    Toast.makeText(this, "Config created", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    SmartRouteLogStore.add("ERROR: import failed: ${e.message}")
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)

        if (intent != null) {
            SmartRouteLogStore.add("Requesting VPN permission")
            vpnPermissionLauncher.launch(intent)
        } else {
            SmartRouteLogStore.add("VPN permission already granted")
            startVpnService()
            statusState?.value = "Running"
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, SmartRouteVpnService::class.java).apply {
            putExtra(
                SmartRouteVpnService.EXTRA_CONFIG_PATH,
                ConfigStore.configPath(this@MainActivity)
            )
        }

        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, SmartRouteVpnService::class.java).apply {
            action = SmartRouteVpnService.ACTION_STOP
        }

        startService(intent)
    }
}

@Composable
private fun FirstRunScreen(
    onUseExample: () -> Unit,
    onPickFile: () -> Unit,
    onImportLink: (String) -> Unit
) {
    val input = remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("SmartRoute Android", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(12.dp))

            Text("Первый запуск: выбери config.toml или вставь proxy/subscription ссылку.")

            Spacer(modifier = Modifier.height(16.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = onPickFile) {
                Text("Выбрать config.toml")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = onUseExample) {
                Text("Создать direct config")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                value = input.value,
                onValueChange = { input.value = it },
                label = { Text("vless://... или subscription URL") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onImportLink(input.value) }
            ) {
                Text("Создать конфиг по ссылке")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LogsBlock()
        }
    }
}

@Composable
private fun MainScreen(
    activity: Activity,
    status: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onResetConfig: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("SmartRoute Android", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Status: $status", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Config path:")

            SelectionContainer {
                Text(
                    text = ConfigStore.configPath(activity),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = onStart) {
                    Text("Start VPN")
                }

                Button(modifier = Modifier.weight(1f), onClick = onStop) {
                    Text("Stop VPN")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenSettings) {
                Text("Настройки конфига")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = onResetConfig) {
                Text("Импортировать другой конфиг")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LogsBlock()
        }
    }
}

@Composable
private fun ConfigSettingsScreen(
    activity: Activity,
    onBack: () -> Unit
) {
    val initialText = remember { ConfigStore.loadConfig(activity) }
    val initialConfig = remember { SmartRouteTomlParser.parse(initialText) }
    val installedApps = remember {
        InstalledAppsStore.load(activity.applicationContext).also {
            SmartRouteLogStore.add("Installed apps loaded: ${it.size}")
        }
    }

    val finalOutbound = remember { mutableStateOf(initialConfig.general.finalOutbound) }
    val globalAutoFix = remember { mutableStateOf(initialConfig.general.autoFix) }

    val chains = remember { mutableStateListOf(*initialConfig.chains.toTypedArray()) }
    val siteRules = remember { mutableStateListOf(*initialConfig.rules.toTypedArray()) }
    val appRules = remember { mutableStateListOf(*initialConfig.appRules.toTypedArray()) }

    val newSiteValue = remember { mutableStateOf("") }
    val newSiteOutbound = remember { mutableStateOf(initialConfig.general.finalOutbound) }
    val newSiteAutoFix = remember { mutableStateOf(false) }

    val newAppPackage = remember { mutableStateOf("") }
    val newAppName = remember { mutableStateOf("") }
    val newAppOutbound = remember { mutableStateOf(initialConfig.general.finalOutbound) }
    val newAppAutoFix = remember { mutableStateOf(false) }

    val newChainTag = remember { mutableStateOf("") }
    val newChainSelectedHop = remember { mutableStateOf("direct") }
    val newChainHops = remember { mutableStateListOf<String>() }

    fun currentConfig(): SmartRouteConfig {
        return initialConfig.copy(
            general = initialConfig.general.copy(
                finalOutbound = finalOutbound.value.ifBlank { "direct" },
                autoFix = globalAutoFix.value
            ),
            chains = chains.toList(),
            rules = siteRules.toList(),
            appRules = appRules.toList()
        )
    }

    fun saveConfig(config: SmartRouteConfig) {
        ConfigStore.saveConfig(activity, config)
        SmartRouteLogStore.add("Config settings saved")

        val reloaded = SmartRouteEngine.reloadFromConfig(
            context = activity,
            configPath = ConfigStore.configPath(activity)
        )

        if (reloaded) {
            Toast.makeText(activity, "Config applied live", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Config saved", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyConfigToUi(config: SmartRouteConfig) {
        finalOutbound.value = config.general.finalOutbound
        globalAutoFix.value = config.general.autoFix

        chains.clear()
        chains.addAll(config.chains)

        siteRules.clear()
        siteRules.addAll(config.rules)

        appRules.clear()
        appRules.addAll(config.appRules)
    }

    val outboundTags = OutboundPingStore.availableOutboundTags(currentConfig())

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text("Настройки конфига", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(12.dp))

            Text("Серверы: ${initialConfig.nodes.size}")
            for (node in initialConfig.nodes) {
                Text(
                    text = "• ${node.tag} (${node.type}) ${node.server}:${node.port}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        OutboundPingStore.pingAll(currentConfig())
                    }
                ) {
                    Text("Проверить ping")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        OutboundPingStore.pingAll(currentConfig()) { pings ->
                            val fixed = ConfigAutoFixer.fixConfig(currentConfig(), pings)
                            applyConfigToUi(fixed)
                            saveConfig(fixed)
                        }
                    }
                ) {
                    Text("Автоисправить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Global / final_outbound", style = MaterialTheme.typography.titleMedium)
            OutboundPicker(
                tags = outboundTags,
                selected = finalOutbound.value,
                onSelect = { finalOutbound.value = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToggleButton(
                text = "Auto-fix global",
                enabled = globalAutoFix.value,
                onToggle = { globalAutoFix.value = !globalAutoFix.value }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Chains", style = MaterialTheme.typography.titleMedium)

            if (chains.isEmpty()) {
                Text("Нет chains")
            } else {
                chains.forEachIndexed { index, chain ->
                    RuleRow(
                        title = "${chain.tag} = ${chain.outbounds.joinToString(" → ")}",
                        onDelete = { chains.removeAt(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newChainTag.value,
                onValueChange = { newChainTag.value = it },
                label = { Text("Название chain, например tg-chain") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Добавить hop в chain:")
            OutboundPicker(
                tags = outboundTags.filter { it != newChainTag.value },
                selected = newChainSelectedHop.value,
                onSelect = { newChainSelectedHop.value = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Текущий chain: ${newChainHops.joinToString(" → ").ifBlank { "(пусто)" }}",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (newChainSelectedHop.value.isNotBlank()) {
                            newChainHops.add(newChainSelectedHop.value)
                        }
                    }
                ) {
                    Text("Добавить hop")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { newChainHops.clear() }
                ) {
                    Text("Очистить")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val tag = newChainTag.value.trim()

                    if (tag.isNotBlank() && newChainHops.isNotEmpty()) {
                        chains.removeAll { it.tag == tag }
                        chains.add(
                            SmartRouteChain(
                                tag = tag,
                                outbounds = newChainHops.toList()
                            )
                        )
                        newChainTag.value = ""
                        newChainHops.clear()
                    }
                }
            ) {
                Text("Создать chain")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Site rules", style = MaterialTheme.typography.titleMedium)

            if (siteRules.isEmpty()) {
                Text("Нет правил сайтов")
            } else {
                siteRules.forEachIndexed { index, rule ->
                    RuleRow(
                        title = "${rule.value} (${rule.type}) → ${rule.outbound} | auto-fix: ${rule.autoFix}",
                        onDelete = { siteRules.removeAt(index) },
                        onToggle = {
                            siteRules[index] = rule.copy(autoFix = !rule.autoFix)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newSiteValue.value,
                onValueChange = { newSiteValue.value = it },
                label = { Text("Домен, например youtube.com") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Outbound для сайта:")
            OutboundPicker(
                tags = outboundTags,
                selected = newSiteOutbound.value,
                onSelect = { newSiteOutbound.value = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToggleButton(
                text = "Auto-fix для нового site rule",
                enabled = newSiteAutoFix.value,
                onToggle = { newSiteAutoFix.value = !newSiteAutoFix.value }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val domain = newSiteValue.value.trim()
                    val outbound = newSiteOutbound.value.trim().ifBlank { "direct" }

                    if (domain.isNotBlank()) {
                        siteRules.add(
                            SmartRouteRule(
                                type = "domain_suffix",
                                value = domain,
                                outbound = outbound,
                                autoFix = newSiteAutoFix.value
                            )
                        )
                        newSiteValue.value = ""
                    }
                }
            ) {
                Text("Добавить правило сайта")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("App rules", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Выбери приложение из списка. Package будет подставлен автоматически.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (appRules.isEmpty()) {
                Text("Нет правил приложений")
            } else {
                appRules.forEachIndexed { index, rule ->
                    RuleRow(
                        title = "${rule.name} / ${rule.packageName} → ${rule.outbound} | auto-fix: ${rule.autoFix}",
                        onDelete = { appRules.removeAt(index) },
                        onToggle = {
                            appRules[index] = rule.copy(autoFix = !rule.autoFix)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AppPicker(
                apps = installedApps,
                selectedPackage = newAppPackage.value,
                selectedName = newAppName.value,
                onSelect = { app ->
                    newAppPackage.value = app.packageName
                    newAppName.value = app.label
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (newAppPackage.value.isNotBlank()) {
                Text(
                    text = "Выбрано: ${newAppName.value} / ${newAppPackage.value}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Outbound для приложения:")
            OutboundPicker(
                tags = outboundTags,
                selected = newAppOutbound.value,
                onSelect = { newAppOutbound.value = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ToggleButton(
                text = "Auto-fix для нового app rule",
                enabled = newAppAutoFix.value,
                onToggle = { newAppAutoFix.value = !newAppAutoFix.value }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val packageName = newAppPackage.value.trim()
                    val name = newAppName.value.trim().ifBlank { packageName }
                    val outbound = newAppOutbound.value.trim().ifBlank { "direct" }

                    if (packageName.isNotBlank()) {
                        appRules.add(
                            SmartRouteAppRule(
                                packageName = packageName,
                                name = name,
                                outbound = outbound,
                                autoFix = newAppAutoFix.value
                            )
                        )
                        newAppPackage.value = ""
                        newAppName.value = ""
                    }
                }
            ) {
                Text("Добавить правило приложения")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { saveConfig(currentConfig()) }
            ) {
                Text("Сохранить и применить на лету")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack
            ) {
                Text("Назад")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LogsBlock()
        }
    }
}

@Composable
private fun OutboundPicker(
    tags: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    OutboundDropdownField(
        label = "Outbound",
        tags = tags,
        selected = selected,
        onSelect = onSelect
    )
}

@Composable
private fun OutboundDropdownField(
    label: String,
    tags: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    val uniqueTags = sortOutboundTagsByPing(tags)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = {
                    Text(if (expanded.value) "▲" else "▼")
                }
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        expanded.value = true
                    }
            )
        }

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = {
                expanded.value = false
            }
        ) {
            for (tag in uniqueTags) {
                val ping = OutboundPingStore.results[tag]?.label() ?: "ping: —"
                val prefix = if (tag == selected) "✓ " else ""

                DropdownMenuItem(
                    text = {
                        Text("$prefix$tag   $ping")
                    },
                    onClick = {
                        onSelect(tag)
                        expanded.value = false
                    }
                )
            }
        }
    }
}

private fun sortOutboundTagsByPing(tags: List<String>): List<String> {
    return tags
        .distinct()
        .sortedWith(
            compareBy<String> { tag ->
                when (OutboundPingStore.results[tag]?.state ?: PingState.UNKNOWN) {
                    PingState.OK -> 0
                    PingState.CHECKING -> 1
                    PingState.UNKNOWN -> 2
                    PingState.FAILED -> 3
                }
            }
                .thenBy { tag ->
                    OutboundPingStore.results[tag]?.latencyMs ?: Long.MAX_VALUE
                }
                .thenBy { tag ->
                    tag
                }
        )
}

@Composable
private fun AppPicker(
    apps: List<InstalledApp>,
    selectedPackage: String,
    selectedName: String,
    onSelect: (InstalledApp) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    val search = remember { mutableStateOf("") }

    val displayValue = when {
        selectedName.isNotBlank() && selectedPackage.isNotBlank() -> "$selectedName / $selectedPackage"
        selectedPackage.isNotBlank() -> selectedPackage
        else -> ""
    }

    val filtered = apps
        .filter { app ->
            val q = search.value.trim().lowercase()
            q.isBlank() ||
                    app.label.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q)
        }
        .take(60)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = displayValue,
                onValueChange = {},
                readOnly = true,
                label = { Text("Приложение") },
                trailingIcon = {
                    Text(if (expanded.value) "▲" else "▼")
                }
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        expanded.value = true
                    }
            )
        }

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = {
                expanded.value = false
            }
        ) {
            DropdownMenuItem(
                text = {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = search.value,
                        onValueChange = { search.value = it },
                        label = { Text("Поиск приложения") }
                    )
                },
                onClick = {}
            )

            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text("Ничего не найдено")
                    },
                    onClick = {}
                )
            } else {
                for (app in filtered) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Column {
                                    Text(app.label)
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(app)
                            expanded.value = false
                        }
                    )
                }
            }

            if (apps.size > 60) {
                DropdownMenuItem(
                    text = {
                        Text("Используй поиск, если приложения нет в первых 60")
                    },
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(
    text: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Text("$text: ${if (enabled) "ON" else "OFF"}")
    }
}

@Composable
private fun RuleRow(
    title: String,
    onDelete: () -> Unit,
    onToggle: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onToggle != null) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onToggle
                ) {
                    Text("Auto-fix")
                }
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = onDelete
            ) {
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun LogsBlock() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Logs",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = {
                SmartRouteLogStore.clear()
                SmartRouteLogStore.add("Logs cleared")
            }
        ) {
            Text("Clear")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    SelectionContainer {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = if (SmartRouteLogStore.logs.isEmpty()) {
                "No logs yet"
            } else {
                SmartRouteLogStore.logs.joinToString("\n")
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
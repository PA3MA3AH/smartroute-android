package com.pa3ma3ah.smartroute

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pa3ma3ah.smartroute.ui.theme.SmartRouteAndroidTheme

class MainActivity : ComponentActivity() {
    private var statusState: MutableState<String>? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startVpnService()
                statusState?.value = "Running"
            } else {
                statusState?.value = "Stopped"
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activity = this

        setContent {
            SmartRouteAndroidTheme {
                val status = remember { mutableStateOf("Stopped") }
                val configText = remember { mutableStateOf(ConfigStore.loadConfig(activity)) }
                val configPath = ConfigStore.configPath(activity)

                statusState = status

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
                        Text(
                            text = "SmartRoute Android",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Status: ${status.value}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Config path:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = configPath,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    ConfigStore.saveConfig(activity, configText.value)
                                    Toast.makeText(activity, "Config saved", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Save")
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    configText.value = ConfigStore.exampleConfig()
                                    ConfigStore.saveConfig(activity, configText.value)
                                    Toast.makeText(activity, "Example loaded", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Example")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    ConfigStore.saveConfig(activity, configText.value)
                                    status.value = "Starting..."
                                    requestVpnPermission()
                                }
                            ) {
                                Text("Start VPN")
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    stopVpnService()
                                    status.value = "Stopped"
                                }
                            ) {
                                Text("Stop VPN")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                            value = configText.value,
                            onValueChange = { configText.value = it },
                            label = { Text("config.toml") },
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)

        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
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
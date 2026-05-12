package com.pa3ma3ah.smartroute

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pa3ma3ah.smartroute.ui.theme.SmartRouteAndroidTheme

class MainActivity : ComponentActivity() {
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startVpnService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmartRouteAndroidTheme {
                val status = remember { mutableStateOf("Stopped") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "SmartRoute Android",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = "Status: ${status.value}",
                            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                        )

                        Button(
                            onClick = {
                                requestVpnPermission()
                                status.value = "Starting..."
                            }
                        ) {
                            Text("Start VPN")
                        }

                        Button(
                            modifier = Modifier.padding(top = 12.dp),
                            onClick = {
                                stopVpnService()
                                status.value = "Stopped"
                            }
                        ) {
                            Text("Stop VPN")
                        }
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
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, SmartRouteVpnService::class.java)
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, SmartRouteVpnService::class.java).apply {
            action = SmartRouteVpnService.ACTION_STOP
        }

        startService(intent)
    }
}
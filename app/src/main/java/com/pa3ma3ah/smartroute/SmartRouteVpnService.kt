package com.pa3ma3ah.smartroute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

class SmartRouteVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var currentConfigPath: String = "unknown"

    override fun onCreate() {
        super.onCreate()
        SmartRouteLogStore.add("VPN service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SmartRouteLogStore.add("Stop action received")
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        currentConfigPath = intent?.getStringExtra(EXTRA_CONFIG_PATH) ?: "unknown"

        SmartRouteLogStore.add("Start action received")
        SmartRouteLogStore.add("Service config path: $currentConfigPath")

        startForeground(1, createNotification())
        startVpn()

        return START_STICKY
    }

    override fun onDestroy() {
        SmartRouteLogStore.add("VPN service destroyed")
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        if (vpnInterface != null) {
            SmartRouteLogStore.add("VPN interface already established")
            SmartRouteEngine.start(this, currentConfigPath, vpnInterface)
            return
        }

        val builder = Builder()
            .setSession("SmartRoute")
            .addAddress("10.10.0.2", 32)
            .addDnsServer("1.1.1.1")

        /*
         * ВАЖНО:
         * Пока НЕ добавляем:
         *
         *   addRoute("0.0.0.0", 0)
         *
         * Потому что engine пока не читает пакеты из VPN-интерфейса.
         * Если добавить default route сейчас, интернет на телефоне может пропасть.
         */

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            SmartRouteLogStore.add("Failed to establish VPN interface")
            return
        }

        SmartRouteLogStore.add("VPN interface established")
        SmartRouteEngine.start(this, currentConfigPath, vpnInterface)
    }

    private fun stopVpn() {
        SmartRouteEngine.stop()

        vpnInterface?.close()
        vpnInterface = null

        SmartRouteLogStore.add("VPN interface closed")
    }

    private fun createNotification(): Notification {
        val channelId = "smartroute_vpn"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SmartRoute VPN",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("SmartRoute")
            .setContentText("VPN service is running")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.pa3ma3ah.smartroute.STOP"
        const val EXTRA_CONFIG_PATH = "com.pa3ma3ah.smartroute.CONFIG_PATH"
    }
}
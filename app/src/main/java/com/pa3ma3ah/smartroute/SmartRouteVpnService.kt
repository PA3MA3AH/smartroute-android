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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("SmartRoute")
            .addAddress("10.10.0.2", 32)
            .addDnsServer("1.1.1.1")

        // ВАЖНО:
        // Пока НЕ добавляем addRoute("0.0.0.0", 0),
        // иначе весь интернет телефона уйдёт в VPN-интерфейс,
        // а мы ещё не обрабатываем пакеты.
        vpnInterface = builder.establish()
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
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
            .setContentText("SmartRoute VPN service is running")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.pa3ma3ah.smartroute.STOP"
    }
}
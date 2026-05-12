package com.pa3ma3ah.smartroute

import android.net.VpnService
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

class AndroidLibboxPlatform(
    private val vpnService: VpnService
) : PlatformInterface {
    override fun autoDetectInterfaceControl(fd: Int) {
        SmartRouteLogStore.add("Platform.autoDetectInterfaceControl fd=$fd")

        val protected = vpnService.protect(fd)

        if (!protected) {
            throw Exception("VpnService.protect($fd) failed")
        }

        SmartRouteLogStore.add("Platform.protect($fd) OK")
    }

    override fun clearDNSCache() {
        SmartRouteLogStore.add("Platform.clearDNSCache")
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        SmartRouteLogStore.add("Platform.closeDefaultInterfaceMonitor")
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int
    ): ConnectionOwner {
        throw Exception("findConnectionOwner is not implemented yet")
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        SmartRouteLogStore.add("Platform.getInterfaces")
        return EmptyNetworkInterfaceIterator()
    }

    override fun includeAllNetworks(): Boolean {
        return false
    }

    override fun localDNSTransport(): LocalDNSTransport? {
        return null
    }

    override fun openTun(options: TunOptions?): Int {
        SmartRouteLogStore.add("Platform.openTun called")

        val mtu = options?.mtu?.takeIf { it > 0 } ?: 1500

        val builder = vpnService.Builder()
            .setSession("SmartRoute")
            .setMtu(mtu)
            .addAddress("172.19.0.1", 30)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            builder.addDisallowedApplication(vpnService.packageName)
            SmartRouteLogStore.add("Platform.openTun: app excluded from VPN loop")
        } catch (e: Throwable) {
            SmartRouteLogStore.add("Platform.openTun: failed to exclude app: ${e.message}")
        }

        val tun = builder.establish()
            ?: throw Exception("VpnService.Builder.establish() returned null")

        val rawFd = tun.detachFd()

        SmartRouteLogStore.add("Platform.openTun returned fd=$rawFd mtu=$mtu")

        return rawFd
    }

    override fun readWIFIState(): WIFIState {
        SmartRouteLogStore.add("Platform.readWIFIState")
        return WIFIState("", "")
    }

    override fun sendNotification(notification: Notification?) {
        SmartRouteLogStore.add("Platform.sendNotification")
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        SmartRouteLogStore.add("Platform.startDefaultInterfaceMonitor")
    }

    override fun systemCertificates(): StringIterator {
        return EmptyStringIterator()
    }

    override fun underNetworkExtension(): Boolean {
        return false
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean {
        return false
    }

    override fun useProcFS(): Boolean {
        return false
    }
}
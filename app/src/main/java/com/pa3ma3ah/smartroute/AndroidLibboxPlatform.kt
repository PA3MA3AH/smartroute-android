package com.pa3ma3ah.smartroute

import android.content.Context
import android.os.ParcelFileDescriptor
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
    private val context: Context,
    private val vpnInterfaceProvider: () -> ParcelFileDescriptor?
) : PlatformInterface {
    override fun autoDetectInterfaceControl(fd: Int) {
        SmartRouteLogStore.add("Platform.autoDetectInterfaceControl fd=$fd")
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

        val fd = vpnInterfaceProvider()
            ?: throw Exception("VPN interface is not established")

        /*
         * ВАЖНО:
         * detachFd() отдаёт владение FD libbox.
         * После этого закрывать ParcelFileDescriptor вручную уже нельзя.
         *
         * На текущем тестовом mixed/direct config openTun вызываться не должен.
         * Когда перейдём к tun inbound, доработаем жизненный цикл fd.
         */
        val rawFd = fd.detachFd()

        SmartRouteLogStore.add("Platform.openTun returned fd=$rawFd")
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
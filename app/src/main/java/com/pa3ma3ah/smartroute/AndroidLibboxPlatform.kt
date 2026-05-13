package com.pa3ma3ah.smartroute

import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InetSocketAddress

class AndroidLibboxPlatform(
    private val vpnService: VpnService
) : PlatformInterface {
    private var lastFindOwnerErrorLogMs: Long = 0L
    private val loggedOwnerKeys = mutableSetOf<String>()

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
        val emptyOwner = emptyConnectionOwner()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            logFindOwnerErrorOnce("requires Android 10+")
            return emptyOwner
        }

        if (sourceAddress.isNullOrBlank() || destinationAddress.isNullOrBlank()) {
            return emptyOwner
        }

        if (ipProtocol != OsConstants.IPPROTO_TCP && ipProtocol != OsConstants.IPPROTO_UDP) {
            return emptyOwner
        }

        val connectivityManager = try {
            vpnService.getSystemService(ConnectivityManager::class.java)
        } catch (e: Throwable) {
            logFindOwnerErrorOnce("ConnectivityManager unavailable: ${e.message}")
            return emptyOwner
        }

        val localAddress = try {
            InetSocketAddress(cleanIpAddress(sourceAddress), sourcePort)
        } catch (e: Throwable) {
            logFindOwnerErrorOnce("bad source address $sourceAddress:$sourcePort: ${e.message}")
            return emptyOwner
        }

        val remoteAddress = try {
            InetSocketAddress(cleanIpAddress(destinationAddress), destinationPort)
        } catch (e: Throwable) {
            logFindOwnerErrorOnce(
                "bad destination address $destinationAddress:$destinationPort: ${e.message}"
            )
            return emptyOwner
        }

        val uid = try {
            connectivityManager.getConnectionOwnerUid(
                ipProtocol,
                localAddress,
                remoteAddress
            )
        } catch (e: SecurityException) {
            logFindOwnerErrorOnce("getConnectionOwnerUid security error: ${e.message}")
            return emptyOwner
        } catch (e: Throwable) {
            logFindOwnerErrorOnce("getConnectionOwnerUid failed: ${e.message}")
            return emptyOwner
        }

        if (uid < 0) {
            return emptyOwner
        }

        val packageManager = vpnService.packageManager

        val packages = try {
            packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        } catch (e: Throwable) {
            logFindOwnerErrorOnce("getPackagesForUid($uid) failed: ${e.message}")
            emptyList()
        }

        if (packages.isEmpty()) {
            return ConnectionOwner().apply {
                userId = uid
                userName = "uid:$uid"
                processPath = ""
                setAndroidPackageNames(LibboxStringIterator(emptyList()))
            }
        }

        val uidName = try {
            packageManager.getNameForUid(uid) ?: "uid:$uid"
        } catch (_: Throwable) {
            "uid:$uid"
        }

        logOwnerResolvedOnce(uid, packages)

        return ConnectionOwner().apply {
            userId = uid
            userName = uidName
            processPath = packages.firstOrNull().orEmpty()
            setAndroidPackageNames(LibboxStringIterator(packages))
        }
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

    private fun emptyConnectionOwner(): ConnectionOwner {
        return ConnectionOwner().apply {
            userId = -1
            userName = ""
            processPath = ""
            setAndroidPackageNames(LibboxStringIterator(emptyList()))
        }
    }

    private fun cleanIpAddress(value: String): String {
        return value
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
    }

    private fun logFindOwnerErrorOnce(message: String) {
        val now = System.currentTimeMillis()

        if (now - lastFindOwnerErrorLogMs >= 5_000) {
            lastFindOwnerErrorLogMs = now
            SmartRouteLogStore.add("Platform.findConnectionOwner: $message")
        }
    }

    private fun logOwnerResolvedOnce(
        uid: Int,
        packages: List<String>
    ) {
        val key = packages.joinToString(",")

        synchronized(loggedOwnerKeys) {
            if (loggedOwnerKeys.size >= 30) {
                return
            }

            if (!loggedOwnerKeys.add(key)) {
                return
            }
        }

        SmartRouteLogStore.add(
            "Platform.findConnectionOwner: uid=$uid packages=${packages.joinToString(",")}"
        )
    }
}
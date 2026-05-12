package com.pa3ma3ah.smartroute

import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator

class EmptyStringIterator : StringIterator {
    override fun hasNext(): Boolean = false
    override fun len(): Int = 0
    override fun next(): String = ""
}

class ListStringIterator(
    private val values: List<String>
) : StringIterator {
    private var index = 0

    override fun hasNext(): Boolean {
        return index < values.size
    }

    override fun len(): Int {
        return values.size
    }

    override fun next(): String {
        if (!hasNext()) return ""
        return values[index++]
    }
}

class EmptyNetworkInterfaceIterator : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = false

    override fun next(): NetworkInterface {
        throw NoSuchElementException("No network interfaces")
    }
}

class EmptyRoutePrefixIterator : RoutePrefixIterator {
    override fun hasNext(): Boolean = false

    override fun next(): RoutePrefix {
        throw NoSuchElementException("No route prefixes")
    }
}
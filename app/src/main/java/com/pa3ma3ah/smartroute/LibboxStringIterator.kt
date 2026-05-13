package com.pa3ma3ah.smartroute

import io.nekohasekai.libbox.StringIterator

class LibboxStringIterator(
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
        if (index >= values.size) {
            return ""
        }

        val value = values[index]
        index += 1
        return value
    }
}
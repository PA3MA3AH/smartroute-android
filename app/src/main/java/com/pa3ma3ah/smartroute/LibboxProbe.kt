package com.pa3ma3ah.smartroute

object LibboxProbe {
    fun check(): String {
        return try {
            val clazz = Class.forName("io.nekohasekai.libbox.Libbox")
            val methodNames = clazz.methods
                .map { it.name }
                .distinct()
                .sorted()
                .take(40)
                .joinToString(", ")

            "libbox loaded: ${clazz.name}\nmethods: $methodNames"
        } catch (e: Throwable) {
            "libbox load failed: ${e::class.java.simpleName}: ${e.message}"
        }
    }
}
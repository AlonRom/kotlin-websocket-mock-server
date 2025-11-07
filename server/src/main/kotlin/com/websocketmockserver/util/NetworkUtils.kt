package com.websocketmockserver.util

import java.net.Inet4Address
import java.net.NetworkInterface

fun getLocalIpAddress(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        val validInterfaces = interfaces.toList().filter { it.isUp && !it.isLoopback }

        validInterfaces.forEach { iface ->
            iface.inetAddresses.asSequence()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { address ->
                    !address.isLoopbackAddress && !address.hostAddress.startsWith("169.254.")
                }
                ?.let { return it.hostAddress }
        }

        validInterfaces.forEach { iface ->
            iface.inetAddresses.asSequence()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.let { return it.hostAddress }
        }

        null
    } catch (e: Exception) {
        println("Failed to get local IP: $e")
        null
    }
}

private fun <T> java.util.Enumeration<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    while (hasMoreElements()) {
        list.add(nextElement())
    }
    return list
}


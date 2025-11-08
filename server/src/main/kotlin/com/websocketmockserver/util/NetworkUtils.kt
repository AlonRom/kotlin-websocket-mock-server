package com.websocketmockserver.util

import java.net.Inet4Address
import java.net.NetworkInterface

fun getLocalIpAddress(): String? {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
            ?.toList()
            ?: emptyList()

        val ipv4Addresses = interfaces
            .flatMap { iface -> iface.inetAddresses.asSequence().filterIsInstance<Inet4Address>() }
            .toList()

        ipv4Addresses.firstOrNull { it.isPreferredAddress() }?.hostAddress
            ?: ipv4Addresses.firstOrNull { !it.isLoopbackAddress }?.hostAddress
    }.getOrElse {
        println("Failed to get local IP: ${it.message}")
        null
    }
}

private fun Inet4Address.isPreferredAddress(): Boolean {
    return !isLoopbackAddress && !isLinkLocalAddress
}
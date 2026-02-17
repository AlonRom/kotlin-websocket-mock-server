package com.websocketmockserver.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Gets the best local IPv4 address for network communication.
 * Prioritizes real network interfaces over virtual ones (Docker, VMs, etc.)
 * and prefers common private network ranges.
 * 
 * Works cross-platform: Windows, macOS, and Linux.
 */
fun getLocalIpAddress(): String? {
    return runCatching {
        val candidateAddresses = getCandidateAddresses()
        
        // Sort by priority and return the best match
        candidateAddresses
            .sortedByDescending { it.priority }
            .firstOrNull()
            ?.address
            ?.hostAddress
    }.getOrElse {
        println("Failed to get local IP: ${it.message}")
        null
    }
}

/**
 * Represents a network interface address with its priority score.
 */
private data class AddressCandidate(
    val address: Inet4Address,
    val priority: Int
)

/**
 * Collects all valid IPv4 addresses from network interfaces, excluding virtual ones.
 */
private fun getCandidateAddresses(): List<AddressCandidate> {
    val interfaces = NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
        ?.filter { !isVirtualInterface(it.name) }
        ?: return emptyList()

    return interfaces
        .flatMap { iface ->
            iface.inetAddresses.asSequence()
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { ip -> AddressCandidate(ip, calculatePriority(ip)) }
        }
        .toList()
}

/**
 * Calculates priority score for an IP address.
 * Higher score = better choice for network communication.
 */
private fun calculatePriority(address: Inet4Address): Int {
    val hostAddress = address.hostAddress ?: return 0
    val octets = parseIpOctets(hostAddress) ?: return 0
    
    return when {
        // Highest priority: 10.x.x.x (common corporate/home networks)
        octets[0] == 10 -> 10
        
        // High priority: 192.168.x.x (home networks), but exclude 192.168.64.x (Docker Desktop)
        octets[0] == 192 && octets[1] == 168 && octets[2] != 64 -> 8
        
        // Medium priority: other 192.168.x.x ranges
        octets[0] == 192 && octets[1] == 168 -> 5
        
        // Exclude Docker default range (172.16-31.x.x)
        octets[0] == 172 && octets[1] in 16..31 -> 0
        
        // Exclude Docker Desktop/VM range (192.168.64.x)
        octets[0] == 192 && octets[1] == 168 && octets[2] == 64 -> 0
        
        // Exclude link-local addresses (169.254.x.x)
        octets[0] == 169 && octets[1] == 254 -> 0
        
        // Low priority: other addresses
        else -> 1
    }
}

/**
 * Parses IP address string into octets array, or null if invalid.
 */
private fun parseIpOctets(ip: String): IntArray? {
    val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
    return if (parts.size == 4) parts.toIntArray() else null
}

/**
 * Checks if a network interface name indicates a virtual interface.
 * Works cross-platform (Windows, macOS, Linux).
 */
private fun isVirtualInterface(name: String): Boolean {
    val lowerName = name.lowercase()
    
    // Common virtual interface patterns across platforms
    val virtualPatterns = listOf(
        "docker",      // Docker (Linux, macOS, Windows)
        "veth",        // Virtual Ethernet (Linux)
        "vmnet",       // VMware (macOS, Linux, Windows)
        "vboxnet",     // VirtualBox (all platforms)
        "virbr",       // libvirt bridge (Linux)
        "br-",         // Bridge interfaces (Linux)
        "tun",         // TUN interfaces (all platforms)
        "tap",         // TAP interfaces (all platforms)
        "lo",          // Loopback (all platforms)
        "wsl",         // Windows Subsystem for Linux
        "hyper-v",     // Hyper-V (Windows)
        "vpn",         // VPN interfaces (all platforms)
        "ppp"          // Point-to-Point Protocol (all platforms)
    )
    
    return virtualPatterns.any { lowerName.contains(it) }
}
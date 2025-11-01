package com.example.websocketclient

import android.util.Log
import kotlinx.coroutines.*
import java.net.*
import java.io.IOException
import kotlinx.serialization.*
import kotlinx.serialization.json.*

data class DiscoveredServer(
    val name: String,
    val wsUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val discoveryMethod: String = "UDP"
) {
    fun isRecent(): Boolean = System.currentTimeMillis() - timestamp < 10000 // 10 seconds
}

class ServerDiscovery(
    private val onServerDiscovered: (DiscoveredServer) -> Unit,
    private val onDiscoveryError: (String) -> Unit
) {
    private var discoveryJob: Job? = null
    private var emulatorFallbackJob: Job? = null
    private var cleanupJob: Job? = null
    private val discoveredServers = mutableMapOf<String, DiscoveredServer>()
    
    companion object {
        private const val TAG = "ServerDiscovery"
        private const val DISCOVERY_PORT = 2505
        private const val EMULATOR_PORT = 8081
    }

    fun startDiscovery() {
        stopDiscovery()
        
        Log.d(TAG, "Starting discovery - isEmulator: ${isEmulator()}")
        
        if (isEmulator()) {
            // On emulator, use both UDP broadcast (to receive from host) and active testing
            Log.d(TAG, "Running on emulator - using UDP broadcast + active connection testing")
            startUdpDiscovery()
            startEmulatorDiscovery()
        } else {
            // On real device, use both UDP broadcast and active testing
            Log.d(TAG, "Running on real device - using UDP broadcast + active testing")
            startUdpDiscovery()
            startEmulatorDiscovery()
        }
        
        // Start periodic cleanup of old servers
        startCleanupJob()
    }
    
    private fun startUdpDiscovery() {
        Log.d(TAG, "About to start UDP discovery")
        discoveryJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Creating DatagramSocket on port $DISCOVERY_PORT")
                val socket = DatagramSocket(DISCOVERY_PORT)
                socket.reuseAddress = true
                socket.broadcast = true
                
                Log.d(TAG, "Started UDP discovery on port $DISCOVERY_PORT")
                
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                
                while (isActive) {
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        Log.d(TAG, "Received UDP broadcast from ${packet.address}: $message")
                        
                        parseBroadcastMessage(message).forEach { server ->
                            addServer(server)
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error receiving UDP broadcast", e)
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start UDP discovery", e)
                onDiscoveryError("Failed to start UDP discovery: ${e.message}")
            }
        }
    }
    
    private fun startEmulatorDiscovery() {
        emulatorFallbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Started emulator discovery")
                
                // Dynamic server URLs to try (no hardcoded IPs)
                val commonUrls = listOf(
                    "ws://localhost:$EMULATOR_PORT/ws", // Localhost (for testing)
                    "ws://10.0.2.2:$EMULATOR_PORT/ws"  // Standard emulator host
                )
                
                Log.d(TAG, "Emulator discovery: trying ${commonUrls.size} URLs")
                
                while (isActive) {
                    for (url in commonUrls) {
                        if (!isActive) break
                        
                        try {
                            // Try to establish a WebSocket connection to verify server exists
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                .build()

                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .build()

                            var connectionTested = false
                            client.newWebSocket(request, object : okhttp3.WebSocketListener() {
                                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                                    Log.d(TAG, "Server found at $url")
                                    connectionTested = true
                                    
                                    // Add server to discovered list with dynamic naming
                                    val host = url.substringAfter("ws://").substringBefore("/ws")
                                    val serverName = when {
                                        host == "10.0.2.2" -> "Emulator Server"
                                        host == "localhost" -> "Local Server"
                                        else -> "Server"
                                    }
                                    
                                    val discoveredServer = DiscoveredServer(
                                        name = "$serverName ($host)",
                                        wsUrl = url,
                                        discoveryMethod = "Active Scan"
                                    )
                                    addServer(discoveredServer)
                                    
                                    // Close the test connection
                                    webSocket.close(1000, "Discovery test")
                                }
                                
                                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                                    Log.d(TAG, "Server not available at $url: ${t.message}")
                                    connectionTested = true
                                }
                            })
                            
                            // Wait for connection test to complete or timeout
                            var attempts = 0
                            while (!connectionTested && attempts < 10 && isActive) {
                                delay(300) // Wait 300ms
                                attempts++
                            }
                            
                            // Small delay between attempts
                            delay(500)
                            
                        } catch (e: Exception) {
                            Log.d(TAG, "Discovery attempt failed for $url: ${e.message}")
                        }
                    }
                    
                    // Wait before next full scan
                    delay(10000) // Check every 10 seconds
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start emulator discovery", e)
            }
        }
    }
    
    private fun addServer(server: DiscoveredServer) {
        // Check if we already have a server with the same port (8081)
        val existingServer = discoveredServers.values.find { existing ->
            val existingPort = existing.wsUrl.substringAfterLast(":").substringBefore("/")
            val newPort = server.wsUrl.substringAfterLast(":").substringBefore("/")
            existingPort == newPort && existingPort == "8081"
        }
        
        if (existingServer == null) {
            // New server discovered
            val key = server.wsUrl
            discoveredServers[key] = server
            Log.d(TAG, "New server discovered via ${server.discoveryMethod}: ${server.name}")
            onServerDiscovered(server)
        } else {
            // Server already exists, prefer the one with better connectivity
            val existingPriority = getServerPriority(existingServer.wsUrl)
            val newPriority = getServerPriority(server.wsUrl)
            
            if (newPriority > existingPriority) {
                // Replace with higher priority server
                discoveredServers.remove(existingServer.wsUrl)
                discoveredServers[server.wsUrl] = server
                Log.d(TAG, "Replaced lower priority server with higher priority: ${server.name}")
                onServerDiscovered(server)
            } else if (newPriority < existingPriority) {
                // Keep the higher priority server, ignore lower priority
                Log.d(TAG, "Ignoring lower priority server, keeping higher priority: ${existingServer.name}")
            } else {
                // Both are same priority, keep the newer one
                if (server.timestamp > existingServer.timestamp) {
                    discoveredServers.remove(existingServer.wsUrl)
                    discoveredServers[server.wsUrl] = server
                    Log.d(TAG, "Updated existing server with newer discovery: ${server.name}")
                } else {
                    Log.d(TAG, "Ignoring older discovery for existing server: ${server.name}")
                }
            }
        }
    }
    
    /**
     * Get priority for server URL. Higher number = higher priority.
     * Priority order: Real network IP > Emulator host > Localhost
     */
    private fun getServerPriority(wsUrl: String): Int {
        val host = wsUrl.substringAfter("ws://").substringBefore(":")
        
        return when {
            isRealNetworkIp(host) -> 3  // Highest priority
            isEmulatorHost(host) -> 2   // Medium priority  
            isLocalhost(host) -> 1      // Lowest priority
            else -> 0                   // Unknown
        }
    }
    
    private fun isRealNetworkIp(host: String): Boolean {
        return try {
            val ip = java.net.InetAddress.getByName(host)
            !ip.isLoopbackAddress && ip is java.net.Inet4Address && 
            !host.startsWith("169.254.") && // Not link-local
            !ip.isSiteLocalAddress // Not private network
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isEmulatorHost(host: String): Boolean {
        // Check if this is likely an emulator host IP
        return try {
            val ip = java.net.InetAddress.getByName(host)
            ip.isSiteLocalAddress && !ip.isLoopbackAddress // Private network but not localhost
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isLocalhost(host: String): Boolean {
        return try {
            val ip = java.net.InetAddress.getByName(host)
            ip.isLoopbackAddress
        } catch (e: Exception) {
            host == "localhost" || host == "127.0.0.1" || host == "::1"
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        emulatorFallbackJob?.cancel()
        emulatorFallbackJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        Log.d(TAG, "Discovery stopped")
    }
    
    private fun startCleanupJob() {
        cleanupJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    delay(5000) // Check every 5 seconds
                    cleanupOldServers()
                } catch (e: Exception) {
                    Log.w(TAG, "Cleanup job error: $e")
                }
            }
        }
    }
    
    private fun cleanupOldServers() {
        val removedServers = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()
        
        discoveredServers.entries.removeIf { (_, server) ->
            val isOld = currentTime - server.timestamp > 15000 // Remove after 15 seconds (longer than isRecent check)
            if (isOld) {
                removedServers.add(server.name)
                Log.d(TAG, "Removing old server from discovery list: ${server.name}")
            }
            isOld
        }
        
        // Notify UI if servers were removed
        if (removedServers.isNotEmpty()) {
            Log.d(TAG, "Removed ${removedServers.size} old servers: ${removedServers.joinToString()}")
            // Trigger a callback to update the UI
            onServerDiscovered(DiscoveredServer("cleanup", "", currentTime, "CLEANUP"))
        }
    }

    private fun parseBroadcastMessage(message: String): List<DiscoveredServer> {
        return try {
            // Try to parse as JSON first
            if (message.trim().startsWith("{")) {
                Log.d(TAG, "Parsing as JSON broadcast message")
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val jsonElement = json.parseToJsonElement(message)
                val urls = findUrlsInJson(jsonElement, mutableListOf())
                
                Log.d(TAG, "Found ${urls.size} URLs in broadcast message")
                
                if (urls.isEmpty()) {
                    // No URLs found in JSON, don't parse it
                    Log.w(TAG, "No URLs found in JSON broadcast message")
                    return emptyList()
                }
                
                val servers = mutableListOf<DiscoveredServer>()
                urls.forEach { url ->
                    if (url.isNotEmpty()) {
                        val serverName = url.substringAfter("ws://").substringBefore("/")
                        Log.d(TAG, "Adding server from broadcast: $serverName at $url")
                        servers.add(DiscoveredServer(
                            name = serverName,
                            wsUrl = url,
                            discoveryMethod = "UDP Broadcast"
                        ))
                    }
                }
                Log.d(TAG, "Parsed ${servers.size} servers from broadcast")
                servers
            } 
            // Fall back to legacy format
            else if (message.startsWith("WEBSOCKET_SERVER:")) {
                val wsUrl = message.substringAfter("WEBSOCKET_SERVER:")
                val serverName = "Server at ${wsUrl.substringAfter("ws://").substringBefore("/ws")}"
                listOf(DiscoveredServer(serverName, wsUrl, discoveryMethod = "UDP"))
            } 
            else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse broadcast message: $message", e)
            emptyList()
        }
    }
    
    private fun findUrlsInJson(element: JsonElement, urls: MutableList<String>): List<String> {
        when (element) {
            is JsonObject -> {
                // Check if this object has a "url" property
                element["url"]?.let { urlElement ->
                    if (urlElement is JsonPrimitive && urlElement.isString) {
                        urls.add(urlElement.content)
                    }
                }
                // Recursively search all properties
                element.forEach { (_, value) ->
                    findUrlsInJson(value, urls)
                }
            }
            is JsonArray -> {
                // Recursively search all elements
                element.forEach { item ->
                    findUrlsInJson(item, urls)
                }
            }
            else -> {
                // JsonPrimitive, JsonNull, etc. - no URLs here
            }
        }
        return urls
    }

    fun getDiscoveredServers(): List<DiscoveredServer> {
        discoveredServers.values.removeAll { !it.isRecent() }
        return discoveredServers.values.toList()
    }
    
    private fun isEmulator(): Boolean {
        return try {
            val buildConfig = android.os.Build::class.java.getField("FINGERPRINT").get(null) as String
            buildConfig.contains("generic") || 
            buildConfig.contains("sdk") || 
            buildConfig.contains("google_sdk") ||
            buildConfig.contains("Emulator") ||
            buildConfig.contains("Android SDK")
        } catch (e: Exception) {
            Log.d(TAG, "Error detecting emulator: ${e.message}")
            false
        }
    }
}

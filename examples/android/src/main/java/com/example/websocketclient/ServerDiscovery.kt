package com.example.websocketclient

import android.util.Log
import kotlinx.coroutines.*
import java.net.*
import java.io.IOException

data class DiscoveredServer(
    val name: String,
    val wsUrl: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isRecent(): Boolean = System.currentTimeMillis() - timestamp < 10000 // 10 seconds
}

class ServerDiscovery(
    private val onServerDiscovered: (DiscoveredServer) -> Unit,
    private val onDiscoveryError: (String) -> Unit
) {
    private var discoveryJob: Job? = null
    private val discoveredServers = mutableMapOf<String, DiscoveredServer>()
    
    companion object {
        private const val TAG = "ServerDiscovery"
        private const val DISCOVERY_PORT = 37020
    }

    fun startDiscovery() {
        stopDiscovery()
        discoveryJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT)
                socket.reuseAddress = true
                socket.broadcast = true
                
                Log.d(TAG, "Started listening for server broadcasts on port $DISCOVERY_PORT")
                
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                
                while (isActive) {
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        Log.d(TAG, "Received broadcast: $message")
                        
                        parseBroadcastMessage(message)?.let { server ->
                            val key = server.wsUrl
                            if (!discoveredServers.containsKey(key)) {
                                discoveredServers[key] = server
                                onServerDiscovered(server)
                                Log.d(TAG, "New server discovered: ${server.name}")
                            } else {
                                // Update existing server timestamp
                                discoveredServers[key] = server
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error receiving broadcast", e)
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start discovery", e)
                onDiscoveryError("Failed to start discovery: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    private fun parseBroadcastMessage(message: String): DiscoveredServer? {
        return try {
            if (message.startsWith("WEBSOCKET_SERVER:")) {
                val wsUrl = message.substringAfter("WEBSOCKET_SERVER:")
                val serverName = "Server at ${wsUrl.substringAfter("ws://").substringBefore("/ws")}"
                DiscoveredServer(serverName, wsUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse broadcast message: $message", e)
            null
        }
    }

    fun getDiscoveredServers(): List<DiscoveredServer> {
        discoveredServers.values.removeAll { !it.isRecent() }
        return discoveredServers.values.toList()
    }
}

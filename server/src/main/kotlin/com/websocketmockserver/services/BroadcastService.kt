package com.websocketmockserver.services

import com.websocketmockserver.models.BroadcastStatus
import com.websocketmockserver.util.getLocalIpAddress
import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class BroadcastService {
    private var isActive = false
    private var interval = 2000L
    private var messageTemplate = ""
    private var messagesSent = 0L
    private var startTime = System.currentTimeMillis()
    private var port = 2505
    private var broadcastJob: Job? = null
    private var udpBroadcastJob: Job? = null
    private var connectedClients: List<DefaultWebSocketSession> = emptyList()

    fun startBroadcast(
        clients: List<DefaultWebSocketSession>,
        interval: Long,
        messageTemplate: String,
        port: Int = 2505
    ) {
        stopBroadcast()

        this.isActive = true
        this.interval = interval
        this.messageTemplate = messageTemplate
        this.port = port
        this.connectedClients = clients
        this.messagesSent = 0L
        this.startTime = System.currentTimeMillis()

        println("Starting UDP broadcast - Template: $messageTemplate, Port: $port, Interval: ${interval}ms")

        udpBroadcastJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val message = generateMessage()
                    broadcastUdpMessage(message, port)
                    println("UDP broadcast sent on port $port: $message")
                    messagesSent++
                } catch (e: Exception) {
                    println("UDP broadcast error: $e")
                }
                delay(interval)
            }
        }

        println("UDP broadcast started")
    }

    fun stopBroadcast() {
        isActive = false
        broadcastJob?.cancel()
        broadcastJob = null
        udpBroadcastJob?.cancel()
        udpBroadcastJob = null
        println("Stopped broadcast")
    }

    fun updateClients(clients: List<DefaultWebSocketSession>) {
        this.connectedClients = clients
    }

    private fun generateMessage(): String {
        val timestamp = System.currentTimeMillis()

        return try {
            String.format(messageTemplate, timestamp, messagesSent, connectedClients.size)
        } catch (e: Exception) {
            println("WARNING: Failed to format message template: ${e.message}")
            messageTemplate
        }
    }

    fun getStatus(): BroadcastStatus {
        return BroadcastStatus(
            isActive = isActive,
            interval = interval,
            messageTemplate = messageTemplate,
            clientsConnected = connectedClients.size,
            messagesSent = messagesSent,
            port = port
        )
    }

    suspend fun broadcastServerAddress(port: Int = 2505, interval: Long = 2000) {
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val socket = DatagramSocket().apply { broadcast = true }
        val localIp = getLocalIpAddress().orEmpty().ifBlank { "127.0.0.1" }
        val wsUrl = "ws://$localIp:8081/ws"
        val message = "WEBSOCKET_SERVER:$wsUrl"
        val buffer = message.toByteArray()
        val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, port)
        while (true) {
            try {
                socket.send(packet)
                println("Broadcasted: $message")
            } catch (e: Exception) {
                println("Failed to broadcast: $e")
            }
            delay(interval)
        }
    }

    private fun broadcastUdpMessage(message: String, port: Int) {
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val socket = DatagramSocket().apply { broadcast = true }
        val buffer = message.toByteArray()
        val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, port)
        try {
            socket.send(packet)
            println("✓ UDP broadcasted to 255.255.255.255:$port - $message")
        } catch (e: Exception) {
            println("✗ Failed to UDP broadcast: $e")
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }
}


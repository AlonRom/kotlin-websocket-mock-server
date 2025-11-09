package com.websocketmockserver.services

import com.websocketmockserver.models.BroadcastStatus
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Handles UDP discovery broadcasts and mirrors each emitted payload to WebSocket dashboards.
 */
class BroadcastService {
    private var isActive = false
    private var interval = 10000L
    private var messageTemplate = ""
    private var port = 2505
    private var udpBroadcastJob: Job? = null
    private var connectedClients: List<DefaultWebSocketSession> = emptyList()

    private companion object {
        private const val BROADCAST_ADDRESS = "255.255.255.255"
        private const val BROADCAST_PREFIX = "BROADCAST_MESSAGE:"
        private const val EMPTY_MESSAGE = "{}"
    }

    /**
     * Kicks off UDP broadcasting using the provided template and interval.
     */
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

        println("Starting UDP broadcast - Template: $messageTemplate, Port: $port, Interval: ${interval}ms")

        udpBroadcastJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val message = resolveMessage()
                    broadcastUdpMessage(message, port)
                    println("UDP broadcast sent on port $port: $message")
                    notifyClientsOfBroadcast(message)
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
        udpBroadcastJob?.cancel()
        udpBroadcastJob = null
        println("Stopped broadcast")
    }

    /**
     * Receives the current snapshot of connected WebSocket clients.
     */
    fun updateClients(clients: List<DefaultWebSocketSession>) {
        this.connectedClients = clients
    }

    /**
     * Exposes a snapshot of the broadcast configuration for dashboard consumers.
     */
    fun getStatus(): BroadcastStatus {
        return BroadcastStatus(
            isActive = isActive,
            interval = interval,
            messageTemplate = messageTemplate,
            port = port
        )
    }

    /**
     * Resolves the payload to publish, defaulting to an empty JSON object.
     */
    private fun resolveMessage(): String {
        val trimmed = messageTemplate.trim()
        if (trimmed.isEmpty()) {
            return EMPTY_MESSAGE
        }
        return trimmed
    }

    /**
     * Sends a single UDP broadcast packet with the provided payload.
     */
    private fun broadcastUdpMessage(message: String, port: Int) {
        val broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS)
        val socket = DatagramSocket().apply { broadcast = true }
        val buffer = message.toByteArray()
        val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, port)
        try {
            socket.send(packet)
            println("✓ UDP broadcasted to $BROADCAST_ADDRESS:$port - $message")
        } catch (e: Exception) {
            println("✗ Failed to UDP broadcast: $e")
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }

    /**
     * Mirrors the broadcast payload to every connected dashboard session.
     */
    private suspend fun notifyClientsOfBroadcast(message: String) {
        if (connectedClients.isEmpty()) {
            return
        }

        val payload = "$BROADCAST_PREFIX$message"
        connectedClients.forEach { client ->
            try {
                client.send(payload)
            } catch (e: Exception) {
                println("Failed to notify client of broadcast: $e")
            }
        }
    }
}


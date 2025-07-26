package com.websocketmockserver

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.CopyOnWriteArrayList
import java.net.*
import kotlinx.coroutines.DelicateCoroutinesApi

fun main() {
    val connectedClients = CopyOnWriteArrayList<DefaultWebSocketSession>()

    // Start UDP broadcast coroutine
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch {
        broadcastServerAddress()
    }
    
    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(30)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("/ws") {
                println("Client connected: $this")
                connectedClients.add(this)
                
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val receivedText = frame.readText()
                            println("Received from client: $receivedText")
                            
                            // Send echo to the sender
                            send("Echo: $receivedText")
                            
                            // Broadcast to all other connected clients
                            val echoMessage = "Broadcast: $receivedText"
                            connectedClients.forEach { client ->
                                if (client != this) {
                                    try {
                                        client.send(echoMessage)
                                    } catch (e: Exception) {
                                        println("Failed to send to client: $e")
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    println("Client disconnected: $this")
                    connectedClients.remove(this)
                }
            }

            staticResources("/", "static")
        }
    }.start(wait = true)
}

suspend fun broadcastServerAddress() {
    val port = 37020
    val broadcastAddress = InetAddress.getByName("255.255.255.255")
    val socket = DatagramSocket()
    socket.broadcast = true
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
        delay(2000)
    }
}

fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    // Prefer non-link-local addresses (not 169.254.x.x)
                    if (!(addr.hostAddress?.startsWith("169.254.") ?: false)) {
                        return addr.hostAddress
                    }
                }
            }
        }
        // If no non-link-local address found, try any IPv4 address
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        println("Failed to get local IP: $e")
    }
    return null
}

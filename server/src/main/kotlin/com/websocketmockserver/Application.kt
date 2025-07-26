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
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.CopyOnWriteArrayList

fun main() {
    val connectedClients = CopyOnWriteArrayList<DefaultWebSocketSession>()
    
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
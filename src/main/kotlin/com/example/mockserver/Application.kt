package com.example.mockserver

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

fun main() {
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
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        println("Received from client: $receivedText")
                        send("Echo: $receivedText")
                    }
                }
            }

            staticResources("/", "static")
        }
    }.start(wait = true)
}
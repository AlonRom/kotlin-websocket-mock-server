package com.websocketmockserver

import com.websocketmockserver.services.BroadcastService
import com.websocketmockserver.services.WebSocketSessionService
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val SERVER_HOST = "0.0.0.0"
private const val SERVER_PORT = 8081
private const val WEBSOCKET_PATH = "/ws"
private const val STATIC_ROUTE = "/"
private const val STATIC_RESOURCES = "static"
private const val DEFAULT_PING_PERIOD_SECONDS = 15L
private const val DEFAULT_SOCKET_TIMEOUT_SECONDS = 30L

/**
 * Bootstraps the mock WebSocket server by wiring shared services and launching the Ktor engine
 * with websocket + static resource support.
 */
fun main() {
    // Configure lenient JSON parser shared across websocket sessions.
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Maintain shared references to connected sessions and pending request callbacks.
    val connectedClients = CopyOnWriteArrayList<DefaultWebSocketSession>()
    val pendingRequests = ConcurrentHashMap<String, DefaultWebSocketSession>()
    val broadcastService = BroadcastService()

    embeddedServer(Netty, port = SERVER_PORT, host = SERVER_HOST) {
        // Enable websocket support with generous timeouts for mobile clients.
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(DEFAULT_PING_PERIOD_SECONDS)
            timeout = Duration.ofSeconds(DEFAULT_SOCKET_TIMEOUT_SECONDS)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            // Single websocket endpoint handling all interactive clients (dashboard and mobile).
            webSocket(WEBSOCKET_PATH) {
                WebSocketSessionService(
                    session = this,
                    connectedClients = connectedClients,
                    pendingRequests = pendingRequests,
                    broadcastService = broadcastService,
                    json = json
                ).handle()
            }

            // Serve static dashboard assets from bundled resources.
            staticResources(STATIC_ROUTE, STATIC_RESOURCES)
        }
    }.start(wait = true)
}


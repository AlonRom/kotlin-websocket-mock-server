package com.websocketmockserver

import com.websocketmockserver.actions.ActionHandler
import com.websocketmockserver.actions.DynamicActionRegistry
import com.websocketmockserver.models.ApiRequest
import com.websocketmockserver.models.ApiResponse
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

fun main() {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val connectedClients = CopyOnWriteArrayList<DefaultWebSocketSession>()
    val pendingRequests = ConcurrentHashMap<String, DefaultWebSocketSession>()
    val broadcastService = BroadcastService()
    val actionRegistry = DynamicActionRegistry()

    actionRegistry.register("ping", object : ActionHandler {
        override suspend fun handle(request: ApiRequest): ApiResponse {
            return ApiResponse(
                action = request.action,
                success = true,
                data = mapOf("timestamp" to System.currentTimeMillis().toString()),
                message = "Pong!",
                requestId = request.requestId
            )
        }
    })

    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(30)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket("/ws") {
                WebSocketSessionService(
                    session = this,
                    connectedClients = connectedClients,
                    pendingRequests = pendingRequests,
                    broadcastService = broadcastService,
                    json = json
                ).handle()
            }

            staticResources("/", "static")
        }
    }.start(wait = true)
}


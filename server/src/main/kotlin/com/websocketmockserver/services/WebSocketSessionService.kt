package com.websocketmockserver.services

import com.websocketmockserver.models.ApiRequest
import com.websocketmockserver.models.ApiResponse
import com.websocketmockserver.models.BroadcastControlRequest
import com.websocketmockserver.models.BroadcastControlResponse
import com.websocketmockserver.util.getLocalIpAddress
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class WebSocketSessionService(
    private val session: DefaultWebSocketSession,
    private val connectedClients: CopyOnWriteArrayList<DefaultWebSocketSession>,
    private val pendingRequests: ConcurrentHashMap<String, DefaultWebSocketSession>,
    private val broadcastService: BroadcastService,
    private val json: Json
) {

    suspend fun handle() {
        onConnect()
        try {
            session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    processIncoming(frame.readText())
                }
            }
        } finally {
            onDisconnect()
        }
    }

    private suspend fun onConnect() {
        println("Client connected: $session")
        connectedClients.add(session)
        broadcastService.updateClients(connectedClients.toList())

        sendClientCount(session)
        notifyOtherClients("CLIENT_CONNECTED:Client")
    }

    private suspend fun onDisconnect() {
        println("Client disconnected: $session")
        connectedClients.remove(session)
        broadcastService.updateClients(connectedClients.toList())

        pendingRequests.entries.removeIf { it.value == session }

        notifyOtherClients("CLIENT_DISCONNECTED:Client")
    }

    private suspend fun processIncoming(message: String) {
        println("Received from client: $message")
        println("Number of connected clients: ${connectedClients.size}")

        when {
            handleBroadcastControl(message) -> Unit
            handleApiRequest(message) -> Unit
            handleApiResponse(message) -> Unit
            else -> handleSpecialMessage(message)
        }
    }

    private suspend fun handleBroadcastControl(message: String): Boolean {
        return try {
            val request = json.decodeFromString(BroadcastControlRequest.serializer(), message)
            if (request.action.lowercase() !in setOf("start", "stop", "status")) {
                return false
            }

            println("Received broadcast control request: ${request.action}")

            val response = when (request.action.lowercase()) {
                "start" -> handleBroadcastStart(request)
                "stop" -> handleBroadcastStop(request)
                "status" -> {
                    sendBroadcastStatus(request)
                    return true
                }
                else -> BroadcastControlResponse(
                    action = request.action,
                    success = false,
                    message = "Unknown action: ${request.action}",
                    requestId = request.requestId
                )
            }

            session.send(json.encodeToString(response))
            println("Sent broadcast control response: ${response.action}")
            true
        } catch (e: Exception) {
            println("Not a broadcast control request: ${e.message}")
            false
        }
    }

    private fun handleBroadcastStart(request: BroadcastControlRequest): BroadcastControlResponse {
        val interval = request.interval ?: 2000L
        val messageTemplate = request.message
            ?: "{\"type\":\"broadcast\",\"timestamp\":%d,\"messageNumber\":%d,\"clients\":%d}"
        val port = request.port ?: 2505

        broadcastService.startBroadcast(
            connectedClients.toList(),
            interval,
            messageTemplate,
            port
        )

        return BroadcastControlResponse(
            action = "start",
            success = true,
            message = "Broadcast started with interval: ${interval}ms, port: ${port}",
            requestId = request.requestId
        )
    }

    private fun handleBroadcastStop(request: BroadcastControlRequest): BroadcastControlResponse {
        broadcastService.stopBroadcast()
        return BroadcastControlResponse(
            action = "stop",
            success = true,
            message = "Broadcast stopped",
            requestId = request.requestId
        )
    }

    private suspend fun sendBroadcastStatus(request: BroadcastControlRequest) {
        val status = broadcastService.getStatus()
        val response = BroadcastControlResponse(
            action = "status",
            success = true,
            message = "Broadcast status retrieved",
            requestId = request.requestId
        )

        val combinedResponse = buildString {
            append(json.encodeToString(response).dropLast(1))
            append(",\"status\":")
            append(json.encodeToString(status))
            append('}')
        }
        session.send(combinedResponse)
    }

    private suspend fun handleApiRequest(message: String): Boolean {
        return try {
            val apiRequest = json.decodeFromString(ApiRequest.serializer(), message)
            println("Received API request: ${apiRequest.action}")
            println("API request ID: ${apiRequest.requestId}")

            apiRequest.requestId?.let { requestId ->
                pendingRequests[requestId] = session
                println("Tracked request $requestId for client")
            }

            val webUiMessage = "API_REQUEST:" + json.encodeToString(apiRequest)
            println("Forwarding to web UI: $webUiMessage")
            connectedClients.forEach { client ->
                if (client != session) {
                    try {
                        client.send(webUiMessage)
                        println("Successfully sent to web UI client")
                    } catch (e: Exception) {
                        println("Failed to forward to web UI: $e")
                    }
                }
            }

            true
        } catch (e: Exception) {
            println("Not an API request: ${e.message}")
            false
        }
    }

    private suspend fun handleApiResponse(message: String): Boolean {
        return try {
            val apiResponse = json.decodeFromString(ApiResponse.serializer(), message)
            println("Received API response: ${apiResponse.action}")
            println("API response ID: ${apiResponse.requestId}")

            apiResponse.requestId?.let { requestId ->
                val requestingClient = pendingRequests[requestId]
                println("Looking for requesting client for ID: $requestId")
                println("Found client: ${requestingClient != null}")
                if (requestingClient != null) {
                    try {
                        requestingClient.send(json.encodeToString(apiResponse))
                        println("Sent response to requesting client")
                    } catch (e: Exception) {
                        println("Failed to send response to client: $e")
                    } finally {
                        pendingRequests.remove(requestId)
                    }
                } else {
                    println("No requesting client found for requestId: $requestId")
                }
            }
            true
        } catch (e: Exception) {
            println("Not an API response: ${e.message}")
            false
        }
    }

    private suspend fun handleSpecialMessage(message: String) {
        when (message) {
            "GET_SERVER_IP" -> {
                val localIp = getLocalIpAddress().orEmpty().ifBlank { "127.0.0.1" }
                val response = "SERVER_IP:$localIp"
                try {
                    session.send(response)
                    println("Sent server IP: $localIp")
                } catch (e: Exception) {
                    println("Failed to send server IP: $e")
                }
            }

            else -> broadcastMessageToOthers(message)
        }
    }

    private suspend fun broadcastMessageToOthers(message: String) {
        println("Received regular message: $message")
        println("Broadcasting to ${connectedClients.size} clients")

        var successCount = 0
        connectedClients.forEach { client ->
            if (client != session) {
                try {
                    client.send(message)
                    successCount++
                    println("Successfully broadcasted to client: $client")
                } catch (e: Exception) {
                    println("Failed to send to client $client: $e")
                }
            }
        }
        println("Broadcast completed: $successCount/${connectedClients.size - 1} clients received the message")
    }

    private suspend fun notifyOtherClients(message: String) {
        connectedClients.forEach { client ->
            if (client != session) {
                try {
                    client.send(message)
                    sendClientCount(client)
                } catch (e: Exception) {
                    println("Failed to notify web UI: $e")
                }
            }
        }
    }

    private suspend fun sendClientCount(target: DefaultWebSocketSession) {
        try {
            target.send("CLIENT_COUNT:${connectedClients.size}")
        } catch (e: Exception) {
            println("Failed to send client count: $e")
        }
    }
}


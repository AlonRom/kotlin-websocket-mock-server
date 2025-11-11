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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orchestrates the lifecycle of a single WebSocket client: routing control messages,
 * relaying API traffic, and mirroring broadcast metadata for the dashboard.
 */
class WebSocketSessionService(
    private val session: DefaultWebSocketSession,
    private val connectedClients: CopyOnWriteArrayList<DefaultWebSocketSession>,
    private val pendingRequests: ConcurrentHashMap<String, DefaultWebSocketSession>,
    private val broadcastService: BroadcastService,
    private val json: Json
) {

    private companion object {
        private const val EVENT_CLIENT_CONNECTED = "CLIENT_CONNECTED:Client"
        private const val EVENT_CLIENT_DISCONNECTED = "CLIENT_DISCONNECTED:Client"
        private const val CLIENT_COUNT_PREFIX = "CLIENT_COUNT:"
        private const val API_REQUEST_PREFIX = "API_REQUEST:"
        private const val SERVER_IP_PREFIX = "SERVER_IP:"
        private const val GET_SERVER_IP_COMMAND = "GET_SERVER_IP"
        private const val DEFAULT_BROADCAST_INTERVAL_MS = 10000L
        private const val DEFAULT_BROADCAST_PORT = 2505
        private const val DEFAULT_BROADCAST_MESSAGE = """{"url":"ws://10.100.102.67:8081/ws"}"""
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_STATUS = "status"
        private val VALID_BROADCAST_ACTIONS = setOf(ACTION_START, ACTION_STOP, ACTION_STATUS)
    }

    /**
     * Main entrypoint for the session. Starts lifecycle hooks and streams incoming frames.
     */
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

    /**
     * Registers the session and notifies the dashboard of the updated participant count.
     */
    private suspend fun onConnect() {
        println("Client connected: $session")
        connectedClients.add(session)
        broadcastService.updateClients(connectedClients.toList())

        sendClientCount(session)
        notifyOtherClients(EVENT_CLIENT_CONNECTED)
    }

    /**
     * Cleans up session state when a client exits and broadcasts the departure.
     */
    private suspend fun onDisconnect() {
        println("Client disconnected: $session")
        connectedClients.remove(session)
        broadcastService.unregisterDashboard(session)
        broadcastService.updateClients(connectedClients.toList())

        pendingRequests.entries.removeIf { it.value == session }

        notifyOtherClients(EVENT_CLIENT_DISCONNECTED)
    }

    /**
     * Routes each text frame to the appropriate handler.
     */
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

    /**
     * Parses and executes broadcast control commands (start/stop/status).
     */
    private suspend fun handleBroadcastControl(message: String): Boolean {
        return try {
            val request = json.decodeFromString<BroadcastControlRequest>(message)
            val action = request.action.lowercase(Locale.getDefault())
            if (action !in VALID_BROADCAST_ACTIONS) {
                return false
            }

            println("Received broadcast control request: ${request.action}")

            val response = when (action) {
                ACTION_START -> handleBroadcastStart(request)
                ACTION_STOP -> handleBroadcastStop(request)
                ACTION_STATUS -> {
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

    /**
     * Begins broadcasting with supplied overrides or default values.
     */
    private fun handleBroadcastStart(request: BroadcastControlRequest): BroadcastControlResponse {
        val interval = request.interval ?: DEFAULT_BROADCAST_INTERVAL_MS
        val messageTemplate = request.message ?: DEFAULT_BROADCAST_MESSAGE
        val port = request.port ?: DEFAULT_BROADCAST_PORT

        broadcastService.startBroadcast(
            connectedClients.toList(),
            interval,
            messageTemplate,
            port
        )

        return BroadcastControlResponse(
            action = ACTION_START,
            success = true,
            message = "Broadcast started with interval: ${interval}ms, port: ${port}",
            requestId = request.requestId
        )
    }

    /**
     * Stops the active broadcast if one is running.
     */
    private fun handleBroadcastStop(request: BroadcastControlRequest): BroadcastControlResponse {
        broadcastService.stopBroadcast()
        return BroadcastControlResponse(
            action = ACTION_STOP,
            success = true,
            message = "Broadcast stopped",
            requestId = request.requestId
        )
    }

    /**
     * Replies to the requester with the latest broadcast status.
     */
    private suspend fun sendBroadcastStatus(request: BroadcastControlRequest) {
        val status = broadcastService.getStatus()
        val response = BroadcastControlResponse(
            action = ACTION_STATUS,
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

    /**
     * Forwards API requests from external clients to the dashboard and tracks pending replies.
     */
    private suspend fun handleApiRequest(message: String): Boolean {
        return try {
            val apiRequest = json.decodeFromString<ApiRequest>(message)
            println("Received API request: ${apiRequest.action}")
            println("API request ID: ${apiRequest.requestId}")

            apiRequest.requestId?.let { requestId ->
                pendingRequests[requestId] = session
                println("Tracked request $requestId for client")
            }

            val webUiMessage = API_REQUEST_PREFIX + json.encodeToString(apiRequest)
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

    /**
     * Delivers responses originating from the dashboard back to the original requester.
     */
    private suspend fun handleApiResponse(message: String): Boolean {
        return try {
            val apiResponse = json.decodeFromString<ApiResponse>(message)
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

    /**
     * Handles auxiliary commands such as IP discovery; otherwise re-broadcasts payloads.
     */
    private suspend fun handleSpecialMessage(message: String) {
        when (message) {
            GET_SERVER_IP_COMMAND -> {
                broadcastService.registerDashboard(session)
                broadcastService.updateClients(connectedClients.toList())
                val localIp = getLocalIpAddress().orEmpty().ifBlank { "127.0.0.1" }
                val response = SERVER_IP_PREFIX + localIp
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

    /**
     * Fan-outs arbitrary messages to every other connected client.
     */
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

    /**
     * Notifies peers about lifecycle events and keeps their client counts fresh.
     */
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

    /**
     * Sends the current participant count to the specified session.
     */
    private suspend fun sendClientCount(target: DefaultWebSocketSession) {
        try {
            target.send("$CLIENT_COUNT_PREFIX${connectedClients.size}")
        } catch (e: Exception) {
            println("Failed to send client count: $e")
        }
    }
}


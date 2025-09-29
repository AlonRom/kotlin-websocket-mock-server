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
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class ApiRequest(
    val operation: String,
    val data: Map<String, String> = emptyMap(),
    val requestId: String? = null
)

@Serializable
data class ApiResponse(
    val operation: String,
    val success: Boolean,
    val data: Map<String, String> = emptyMap(),
    val message: String = "",
    val requestId: String? = null
)

@Serializable
data class BroadcastControlRequest(
    val action: String, // "start", "stop"
    val interval: Long? = null, // milliseconds
    val message: String? = null,
    val discoveryPort: Int? = null, // UDP discovery port
    val requestId: String? = null
)

@Serializable
data class BroadcastControlResponse(
    val action: String,
    val success: Boolean,
    val message: String = "",
    val requestId: String? = null
)

@Serializable
data class BroadcastStatus(
    val isActive: Boolean,
    val interval: Long,
    val messageTemplate: String,
    val clientsConnected: Int,
    val messagesSent: Long
)

// Dynamic operation handler interface
interface OperationHandler {
    suspend fun handle(request: ApiRequest): ApiResponse
}

// Dynamic operation registry
class DynamicOperationRegistry {
    private val handlers = mutableMapOf<String, OperationHandler>()

    fun register(operation: String, handler: OperationHandler) {
        handlers[operation.lowercase()] = handler
    }

    suspend fun handle(request: ApiRequest): ApiResponse {
        val handler = handlers[request.operation.lowercase()]
        return if (handler != null) {
            try {
                handler.handle(request)
            } catch (e: Exception) {
                ApiResponse(
                    operation = request.operation,
                    success = false,
                    data = emptyMap(),
                    message = "Error handling operation: ${e.message}",
                    requestId = request.requestId
                )
            }
        } else {
            // Generate a dynamic response for unknown operations
            generateDynamicResponse(request)
        }
    }

    private suspend fun generateDynamicResponse(request: ApiRequest): ApiResponse {
        return when {
            request.operation.lowercase().contains("get") -> {
                ApiResponse(
                    operation = request.operation,
                    success = true,
                    data = mapOf(
                        "timestamp" to System.currentTimeMillis().toString(),
                        "operation_type" to "get",
                        "dynamic_response" to "true",
                        "requested_operation" to request.operation
                    ),
                    message = "Dynamic GET operation handled successfully",
                    requestId = request.requestId
                )
            }
            request.operation.lowercase().contains("subscribe") -> {
                ApiResponse(
                    operation = request.operation,
                    success = true,
                    data = mapOf(
                        "subscription_id" to "sub_${System.currentTimeMillis()}",
                        "status" to "subscribed",
                        "dynamic_response" to "true"
                    ),
                    message = "Dynamic subscription created successfully",
                    requestId = request.requestId
                )
            }
            request.operation.lowercase().contains("unsubscribe") -> {
                ApiResponse(
                    operation = request.operation,
                    success = true,
                    data = mapOf(
                        "status" to "unsubscribed",
                        "dynamic_response" to "true"
                    ),
                    message = "Dynamic unsubscription completed",
                    requestId = request.requestId
                )
            }
            else -> {
                ApiResponse(
                    operation = request.operation,
                    success = true,
                    data = mapOf(
                        "timestamp" to System.currentTimeMillis().toString(),
                        "dynamic_response" to "true",
                        "unknown_operation" to request.operation
                    ),
                    message = "Unknown operation handled dynamically",
                    requestId = request.requestId
                )
            }
        }
    }
}

// Broadcast control state management
class BroadcastController {
    private var isActive = false
    private var interval = 2000L // 2 seconds default
    private var messageTemplate = ""
    private var messagesSent = 0L
    private var startTime = System.currentTimeMillis()
    private var discoveryPort = 2505 // Discovery port default
    private var broadcastJob: Job? = null
    private var udpBroadcastJob: Job? = null
    private var connectedClients: List<DefaultWebSocketSession> = emptyList()

    fun startBroadcast(clients: List<DefaultWebSocketSession>, interval: Long, messageTemplate: String, discoveryPort: Int = 2505) {
        stopBroadcast() // Stop any existing broadcast
        
        this.isActive = true
        this.interval = interval
        this.messageTemplate = messageTemplate
        this.discoveryPort = discoveryPort
        this.connectedClients = clients
        this.messagesSent = 0L
        this.startTime = System.currentTimeMillis()
        
        println("Starting broadcast with ${clients.size} clients, interval: ${interval}ms")
        
        // Start WebSocket broadcast
        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val message = generateMessage()
                    broadcastToClients(message)
                    messagesSent++
                } catch (e: Exception) {
                    println("Broadcast error: $e")
                }
                delay(interval)
            }
        }
        
        // Start UDP message broadcast
        udpBroadcastJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val message = generateMessage()
                    broadcastUdpMessage(message, discoveryPort)
                } catch (e: Exception) {
                    println("UDP broadcast error: $e")
                }
                delay(interval)
            }
        }
        
        println("Started broadcast with interval: ${interval}ms")
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
            messageTemplate.format(timestamp, messagesSent, connectedClients.size)
        } catch (e: Exception) {
            // If formatting fails, return a simple message
            "{\"type\":\"broadcast\",\"timestamp\":$timestamp,\"messageNumber\":$messagesSent,\"clients\":${connectedClients.size}}"
        }
    }
    
    private suspend fun broadcastToClients(message: String) {
        var successCount = 0
        var failCount = 0
        connectedClients.forEach { client ->
            try {
                client.send(message)
                successCount++
            } catch (e: Exception) {
                failCount++
                println("Failed to send broadcast message to client: $e")
            }
        }
        println("Broadcasted to $successCount clients (${failCount} failed): $message")
    }
    
    fun getStatus(): BroadcastStatus {
        return BroadcastStatus(
            isActive = isActive,
            interval = interval,
            messageTemplate = messageTemplate,
            clientsConnected = connectedClients.size,
            messagesSent = messagesSent
        )
    }
}

fun main() {
    val connectedClients = CopyOnWriteArrayList<DefaultWebSocketSession>()
    val operationRegistry = DynamicOperationRegistry()
    val pendingRequests = mutableMapOf<String, DefaultWebSocketSession>() // Track requestId -> client
    val broadcastController = BroadcastController()
    
    // Register some example handlers (optional - for demonstration)
    operationRegistry.register("ping", object : OperationHandler {
        override suspend fun handle(request: ApiRequest): ApiResponse {
            return ApiResponse(
                operation = request.operation,
                success = true,
                data = mapOf("timestamp" to System.currentTimeMillis().toString()),
                message = "Pong!",
                requestId = request.requestId
            )
        }
    })

    // UDP broadcast disabled - only controlled broadcasting via web dashboard
    // @OptIn(DelicateCoroutinesApi::class)
    // GlobalScope.launch {
    //     broadcastServerAddress()
    // }

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
                connectedClients.add(this) // Track connected clients
                
                // Update broadcast controller with current client list
                broadcastController.updateClients(connectedClients.toList())
                 
                 // Notify web UI about new client connection
                 val connectionMessage = "CLIENT_CONNECTED:Client"
                 connectedClients.forEach { client ->
                     if (client != this) {
                         try {
                             client.send(connectionMessage)
                         } catch (e: Exception) {
                             println("Failed to notify web UI about connection: $e")
                         }
                     }
                 }

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val receivedText = frame.readText()
                            println("Received from client: $receivedText")
                            println("Number of connected clients: ${connectedClients.size}")

                            var messageHandled = false
                            
                            // Try to parse as API request (from Android clients)
                            try {
                                val apiRequest = Json.decodeFromString<ApiRequest>(receivedText)
                                println("Received API request: ${apiRequest.operation}")
                                println("API request ID: ${apiRequest.requestId}")
                                
                                // Track this request to route response back to this client
                                if (apiRequest.requestId != null) {
                                    pendingRequests[apiRequest.requestId] = this
                                    println("Tracked request ${apiRequest.requestId} for client")
                                }
                                
                                // Forward API request to web UI for manual handling
                                val webUiMessage = "API_REQUEST:" + Json.encodeToString(apiRequest)
                                println("Forwarding to web UI: $webUiMessage")
                                connectedClients.forEach { client ->
                                    if (client != this) { // Send to all clients except the sender (which is the Android app)
                                        try {
                                            client.send(webUiMessage) // This sends to the web UI
                                            println("Successfully sent to web UI client")
                                        } catch (e: Exception) {
                                            println("Failed to forward to web UI: $e")
                                        }
                                    }
                                }
                                
                                // Don't send automatic response - wait for manual response from web UI
                                messageHandled = true
                            } catch (e: Exception) {
                                println("Not an API request: ${e.message}")
                                // Not an API request, continue to next check
                            }
                            
                            // Only process further if message wasn't handled as API request
                            if (!messageHandled) {
                                // Try to parse as API response (from web UI)
                                try {
                                    val apiResponse = Json.decodeFromString<ApiResponse>(receivedText)
                                    println("Received API response: ${apiResponse.operation}")
                                    println("API response ID: ${apiResponse.requestId}")
                                    
                                    // Route response back to the requesting client
                                    if (apiResponse.requestId != null) {
                                        val requestingClient = pendingRequests[apiResponse.requestId]
                                        println("Looking for requesting client for ID: ${apiResponse.requestId}")
                                        println("Found client: ${requestingClient != null}")
                                        if (requestingClient != null) {
                                            try {
                                                requestingClient.send(Json.encodeToString(apiResponse))
                                                println("Sent response to requesting client")
                                                pendingRequests.remove(apiResponse.requestId) // Clean up
                                            } catch (e: Exception) {
                                                println("Failed to send response to client: $e")
                                                pendingRequests.remove(apiResponse.requestId) // Clean up
                                            }
                                        } else {
                                            println("No requesting client found for requestId: ${apiResponse.requestId}")
                                        }
                                    }
                                    messageHandled = true
                                } catch (e: Exception) {
                                    println("Not an API response: ${e.message}")
                                    // Not an API response, continue to next check
                                }
                            }
                            
                            // Only process further if message wasn't handled as API request or response
                            if (!messageHandled) {
                                // Try to parse as broadcast control request (from web UI)
                                try {
                                    val broadcastRequest = Json.decodeFromString<BroadcastControlRequest>(receivedText)
                                    println("Received broadcast control request: ${broadcastRequest.action}")
                                    
                                    val response = when (broadcastRequest.action.lowercase()) {
                                        "start" -> {
                                            val interval = broadcastRequest.interval ?: 2000L
                                            val messageTemplate = broadcastRequest.message ?: "{\"type\":\"broadcast\",\"timestamp\":%d,\"messageNumber\":%d,\"clients\":%d}"
                                            val discoveryPort = broadcastRequest.discoveryPort ?: 2505
                                            
                                            broadcastController.startBroadcast(connectedClients.toList(), interval, messageTemplate, discoveryPort)
                                            
                                            BroadcastControlResponse(
                                                action = "start",
                                                success = true,
                                                message = "Broadcast started with interval: ${interval}ms, discovery port: ${discoveryPort}",
                                                requestId = broadcastRequest.requestId
                                            )
                                        }
                                        "stop" -> {
                                            broadcastController.stopBroadcast()
                                            BroadcastControlResponse(
                                                action = "stop",
                                                success = true,
                                                message = "Broadcast stopped",
                                                requestId = broadcastRequest.requestId
                                            )
                                        }
                                        "status" -> {
                                            val status = broadcastController.getStatus()
                                            val responseJson = Json.encodeToString(BroadcastControlResponse(
                                                action = "status",
                                                success = true,
                                                message = "Broadcast status retrieved",
                                                requestId = broadcastRequest.requestId
                                            ))
                                            val statusJson = Json.encodeToString(status)
                                            val combinedResponse = responseJson.dropLast(1) + ",\"status\":" + statusJson + "}"
                                            this.send(combinedResponse)
                                            continue // Skip the regular response sending
                                        }
                                        else -> {
                                            BroadcastControlResponse(
                                                action = broadcastRequest.action,
                                                success = false,
                                                message = "Unknown action: ${broadcastRequest.action}",
                                                requestId = broadcastRequest.requestId
                                            )
                                        }
                                    }
                                    
                                    // Send response back to the web UI client
                                    try {
                                        this.send(Json.encodeToString(response))
                                        println("Sent broadcast control response: ${response.action}")
                                    } catch (e: Exception) {
                                        println("Failed to send broadcast control response: $e")
                                    }
                                    
                                    messageHandled = true
                                } catch (e: Exception) {
                                    println("Not a broadcast control request: ${e.message}")
                                    // Not a broadcast control request, continue to next check
                                }
                            }
                            
                            // Only process further if message wasn't handled as API request, response, or broadcast control
                            if (!messageHandled) {
                                // Handle special messages
                                when (receivedText) {
                                    "GET_SERVER_IP" -> {
                                        val localIp = getLocalIpAddress().orEmpty().ifBlank { "127.0.0.1" }
                                        val response = "SERVER_IP:$localIp"
                                        try {
                                            this.send(response)
                                            println("Sent server IP: $localIp")
                                        } catch (e: Exception) {
                                            println("Failed to send server IP: $e")
                                        }
                                    }
                                    else -> {
                                        // Handle regular messages (from web UI push messages or other clients)
                                        println("Received regular message: $receivedText")
                                        println("Broadcasting to ${connectedClients.size} clients")
                                        
                                        // Broadcast to all other connected clients (including Android apps)
                                        val broadcastMessage = receivedText
                                        var successCount = 0
                                        connectedClients.forEach { client ->
                                            if (client != this) {
                                                try {
                                                    client.send(broadcastMessage)
                                                    successCount++
                                                    println("Successfully broadcasted to client: $client")
                                                } catch (e: Exception) {
                                                    println("Failed to send to client $client: $e")
                                                }
                                            }
                                        }
                                        println("Broadcast completed: $successCount/${connectedClients.size - 1} clients received the message")
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    println("Client disconnected: $this")
                    connectedClients.remove(this) // Clean up on disconnect
                    
                    // Update broadcast controller with current client list
                    broadcastController.updateClients(connectedClients.toList())
                     
                     // Clean up any pending requests from this client
                     val requestsToRemove = pendingRequests.filterValues { it == this }.keys
                     requestsToRemove.forEach { requestId ->
                         pendingRequests.remove(requestId)
                         println("Cleaned up pending request: $requestId")
                     }
                     
                    // Notify web UI about client disconnection (only send to web UI, not Android clients)
                    val disconnectionMessage = "CLIENT_DISCONNECTED:Client"
                    // Note: In a real implementation, you'd need to identify web UI clients
                    // For now, we'll send to all clients but Android should ignore these internal messages
                    connectedClients.forEach { client ->
                        try {
                            client.send(disconnectionMessage)
                        } catch (e: Exception) {
                            println("Failed to notify web UI about disconnection: $e")
                        }
                    }
                }
            }

            staticResources("/", "static")
        }
    }.start(wait = true)
}

suspend fun broadcastUdpMessage(message: String, port: Int = 2505) {
    val broadcastAddress = InetAddress.getByName("255.255.255.255")
    val socket = DatagramSocket()
    socket.broadcast = true
    val buffer = message.toByteArray()
    val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, port)
    try {
        socket.send(packet)
        println("UDP broadcasted: $message")
    } catch (e: Exception) {
        println("Failed to UDP broadcast: $e")
    } finally {
        socket.close()
    }
}

suspend fun broadcastServerAddress(port: Int = 2505, interval: Long = 2000) {
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
        delay(interval) // Use the configurable interval
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

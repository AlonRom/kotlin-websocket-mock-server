package com.websocketmockserver.actions

import com.websocketmockserver.models.ApiRequest
import com.websocketmockserver.models.ApiResponse

class DynamicActionRegistry {
    private val handlers = mutableMapOf<String, ActionHandler>()

    fun register(action: String, handler: ActionHandler) {
        handlers[action.lowercase()] = handler
    }

    suspend fun handle(request: ApiRequest): ApiResponse {
        val handler = handlers[request.action.lowercase()]
        return if (handler != null) {
            try {
                handler.handle(request)
            } catch (e: Exception) {
                ApiResponse(
                    action = request.action,
                    success = false,
                    data = emptyMap(),
                    message = "Error handling action: ${e.message}",
                    requestId = request.requestId
                )
            }
        } else {
            generateDynamicResponse(request)
        }
    }

    private suspend fun generateDynamicResponse(request: ApiRequest): ApiResponse {
        return when {
            request.action.lowercase().contains("get") -> {
                ApiResponse(
                    action = request.action,
                    success = true,
                    data = mapOf(
                        "timestamp" to System.currentTimeMillis().toString(),
                        "action_type" to "get",
                        "dynamic_response" to "true",
                        "requested_action" to request.action
                    ),
                    message = "Dynamic GET action handled successfully",
                    requestId = request.requestId
                )
            }
            request.action.lowercase().contains("subscribe") -> {
                ApiResponse(
                    action = request.action,
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
            request.action.lowercase().contains("unsubscribe") -> {
                ApiResponse(
                    action = request.action,
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
                    action = request.action,
                    success = true,
                    data = mapOf(
                        "timestamp" to System.currentTimeMillis().toString(),
                        "dynamic_response" to "true",
                        "unknown_action" to request.action
                    ),
                    message = "Unknown action handled dynamically",
                    requestId = request.requestId
                )
            }
        }
    }
}


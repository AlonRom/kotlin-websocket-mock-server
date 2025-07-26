package com.example.websocketclient

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Callback interface for API operations
interface ApiCallback {
    fun onSuccess(data: Map<String, String>, message: String)
    fun onError(error: String)
}

// Dynamic API Client that manages operations and callbacks
class DynamicApiClient {
    private val callbacks = ConcurrentHashMap<String, ApiCallback>()
    private val pendingRequests = ConcurrentHashMap<String, ApiCallback>()
    
    // Register a callback for a specific operation
    fun registerCallback(operation: String, callback: ApiCallback) {
        callbacks[operation.lowercase()] = callback
    }
    
    // Unregister a callback for a specific operation
    fun unregisterCallback(operation: String) {
        callbacks.remove(operation.lowercase())
    }
    
    // Send an API request and handle the response
    fun sendRequest(
        operation: String, 
        data: Map<String, String> = emptyMap(),
        callback: ApiCallback? = null
    ): String {
        val requestId = UUID.randomUUID().toString()
        val request = ApiRequest(
            operation = operation,
            data = data,
            requestId = requestId
        )
        
        // Store callback for this specific request if provided
        if (callback != null) {
            pendingRequests[requestId] = callback
        }
        
        return Json.encodeToString(request)
    }
    
    // Handle incoming API response
    fun handleResponse(responseJson: String) {
        try {
            val response = Json.decodeFromString<ApiResponse>(responseJson)
            val requestId = response.requestId
            
            // First check if this is a response to a specific request
            val pendingCallback = pendingRequests.remove(requestId)
            if (pendingCallback != null) {
                if (response.success) {
                    pendingCallback.onSuccess(response.data, response.message)
                } else {
                    pendingCallback.onError(response.message)
                }
                return
            }
            
            // If no specific callback, check for operation-based callback
            val operationCallback = callbacks[response.operation.lowercase()]
            if (operationCallback != null) {
                if (response.success) {
                    operationCallback.onSuccess(response.data, response.message)
                } else {
                    operationCallback.onError(response.message)
                }
            }
        } catch (e: Exception) {
            // Re-throw the exception so the caller knows this wasn't an API response
            throw e
        }
    }
    
    // Clear all callbacks
    fun clearCallbacks() {
        callbacks.clear()
        pendingRequests.clear()
    }
} 
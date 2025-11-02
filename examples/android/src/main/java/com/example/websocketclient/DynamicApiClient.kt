package com.example.websocketclient

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Callback interface for API actions
interface ApiCallback {
    fun onSuccess(data: Map<String, String>, message: String)
    fun onError(error: String)
}

// Dynamic API Client that manages actions and callbacks
class DynamicApiClient {
    private val callbacks = ConcurrentHashMap<String, ApiCallback>()
    private val pendingRequests = ConcurrentHashMap<String, ApiCallback>()
    
    // Register a callback for a specific action
    fun registerCallback(action: String, callback: ApiCallback) {
        callbacks[action.lowercase()] = callback
    }
    
    // Unregister a callback for a specific action
    fun unregisterCallback(action: String) {
        callbacks.remove(action.lowercase())
    }
    
    // Send an API request and handle the response
    fun sendRequest(
        action: String, 
        data: Map<String, String> = emptyMap(),
        callback: ApiCallback? = null
    ): String {
        val requestId = UUID.randomUUID().toString()
        val request = ApiRequest(
            action = action,
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
            
            // If no specific callback, check for action-based callback
            val actionCallback = callbacks[response.action.lowercase()]
            if (actionCallback != null) {
                if (response.success) {
                    actionCallback.onSuccess(response.data, response.message)
                } else {
                    actionCallback.onError(response.message)
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
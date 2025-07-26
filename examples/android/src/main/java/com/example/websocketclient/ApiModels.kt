package com.example.websocketclient

import kotlinx.serialization.Serializable

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
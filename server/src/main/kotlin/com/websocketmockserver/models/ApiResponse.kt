package com.websocketmockserver.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val action: String,
    val success: Boolean,
    val data: Map<String, String> = emptyMap(),
    val message: String = "",
    val requestId: String? = null
)


package com.websocketmockserver.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiRequest(
    val action: String,
    val data: Map<String, String> = emptyMap(),
    val requestId: String? = null
)


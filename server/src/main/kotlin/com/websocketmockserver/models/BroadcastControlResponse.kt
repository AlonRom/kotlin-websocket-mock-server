package com.websocketmockserver.models

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastControlResponse(
    val action: String,
    val success: Boolean,
    val message: String = "",
    val requestId: String? = null
)


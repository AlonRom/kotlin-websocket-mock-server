package com.websocketmockserver.models

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastControlRequest(
    val action: String,
    val interval: Long? = null,
    val message: String? = null,
    val port: Int? = null,
    val requestId: String? = null
)


package com.websocketmockserver.models

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastStatus(
    val isActive: Boolean,
    val interval: Long,
    val messageTemplate: String,
    val clientsConnected: Int,
    val messagesSent: Long,
    val port: Int? = null
)


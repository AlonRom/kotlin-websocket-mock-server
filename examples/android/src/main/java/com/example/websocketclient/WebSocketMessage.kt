package com.example.websocketclient

data class WebSocketMessage(
    val message: String,
    val timestamp: Long,
    val type: MessageType = MessageType.CLIENT
) {
    fun getFormattedTime(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }
}

enum class MessageType {
    CLIENT,
    SERVER
} 
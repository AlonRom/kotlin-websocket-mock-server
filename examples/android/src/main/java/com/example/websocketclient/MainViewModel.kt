package com.example.websocketclient

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _messages = MutableLiveData<MutableList<WebSocketMessage>>(mutableListOf())
    val messages: LiveData<MutableList<WebSocketMessage>> = _messages

    private val _connectionStatus = MutableLiveData<String>("Disconnected")
    val connectionStatus: LiveData<String> = _connectionStatus

    fun addMessage(message: WebSocketMessage) {
        val currentMessages = _messages.value ?: mutableListOf()
        val newMessages = currentMessages.toMutableList() // create a new list instance
        newMessages.add(message)
        _messages.value = newMessages
    }

    fun clearMessages() {
        _messages.value = mutableListOf()
    }

    fun updateConnectionStatus(status: String) {
        _connectionStatus.value = status
    }
} 
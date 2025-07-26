package com.example.websocketclient

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.websocketclient.databinding.ActivityMainBinding
import okhttp3.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var webSocket: WebSocket? = null
    private lateinit var messagesAdapter: MessagesAdapter

    companion object {
        private const val TAG = "WebSocketClient"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setDefaultServerUrlAndHint()
    }

    private fun setDefaultServerUrlAndHint() {
        try {
            val isEmulator = isEmulator()
            if (isEmulator) {
                binding.serverUrlInput.setText("ws://10.0.2.2:8081/ws")
                binding.serverUrlLayout.helperText = "Emulator detected: using 10.0.2.2 for localhost."
            } else {
                binding.serverUrlInput.setText("")
                binding.serverUrlLayout.helperText = "Real device: Enter your computer's IP, e.g. ws://192.168.x.x:8081/ws"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting default URL", e)
            binding.serverUrlInput.setText("")
            binding.serverUrlLayout.helperText = "Enter WebSocket URL (e.g. ws://192.168.x.x:8081/ws)"
        }
    }

    private fun isEmulator(): Boolean {
        return try {
            (Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.lowercase().contains("emulator") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                    "google_sdk" == Build.PRODUCT)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting emulator", e)
            false
        }
    }

    private fun setupRecyclerView() {
        try {
            messagesAdapter = MessagesAdapter()
            binding.messagesRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = messagesAdapter
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up RecyclerView", e)
        }
    }

    private fun setupObservers() {
        try {
            viewModel.messages.observe(this) { messages ->
                try {
                    messagesAdapter.submitList(messages.toList())
                    binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating messages", e)
                }
            }

            viewModel.connectionStatus.observe(this) { status ->
                try {
                    binding.statusText.text = status
                    updateConnectButton(status)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating status", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observers", e)
        }
    }

    private fun setupClickListeners() {
        try {
            binding.connectButton.setOnClickListener {
                try {
                    if (webSocket == null) {
                        connectToWebSocket()
                    } else {
                        disconnectFromWebSocket()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in connect button click", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            binding.clearButton.setOnClickListener {
                try {
                    viewModel.clearMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing messages", e)
                }
            }

            binding.sendButton.setOnClickListener {
                try {
                    sendMessage()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending message", e)
                    Toast.makeText(this, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners", e)
        }
    }

    private fun sendMessage() {
        try {
            val messageText = binding.messageInput.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return
            }

            if (webSocket == null) {
                Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d(TAG, "Sending message: $messageText")
            val success = webSocket?.send(messageText) ?: false
            
            if (success) {
                // Clear the input field
                binding.messageInput.text?.clear()
                Log.d(TAG, "Message sent successfully")
            } else {
                Log.e(TAG, "Failed to send message")
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Toast.makeText(this, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToWebSocket() {
        try {
            val serverUrl = binding.serverUrlInput.text.toString().trim()
            Log.d(TAG, "Attempting to connect to: $serverUrl")
            
            if (serverUrl.isEmpty()) {
                Toast.makeText(this, "Please enter a server URL", Toast.LENGTH_SHORT).show()
                return
            }

            // Validate URL format
            if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
                Toast.makeText(this, "Invalid URL format. Must start with ws:// or wss://", Toast.LENGTH_SHORT).show()
                return
            }

            viewModel.updateConnectionStatus("Connecting...")

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .build()

            Log.d(TAG, "Creating WebSocket connection...")
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connection opened successfully")
                    Log.d(TAG, "Response code: ${response.code}")
                    Log.d(TAG, "Response message: ${response.message}")
                    Log.d(TAG, "Response headers: ${response.headers}")
                    runOnUiThread {
                        try {
                            viewModel.updateConnectionStatus("Connected")
                            Toast.makeText(this@MainActivity, "Connected to server!", Toast.LENGTH_SHORT).show()
                            
                            // Send a test message to verify the connection
                            Log.d(TAG, "Sending test message to verify connection")
                            webSocket.send("Android app connected")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating UI on connection open", e)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: $text")
                    Log.d(TAG, "Message length: ${text.length}")
                    Log.d(TAG, "Current thread: ${Thread.currentThread().name}")
                    runOnUiThread {
                        try {
                            Log.d(TAG, "Adding message to ViewModel: $text")
                            viewModel.addMessage(WebSocketMessage(text, System.currentTimeMillis()))
                            Log.d(TAG, "Message added successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding message", e)
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Connection closing: $code - $reason")
                    runOnUiThread {
                        try {
                            viewModel.updateConnectionStatus("Disconnected")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating UI on connection closing", e)
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Connection closed: $code - $reason")
                    runOnUiThread {
                        try {
                            viewModel.updateConnectionStatus("Disconnected")
                            this@MainActivity.webSocket = null
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating UI on connection closed", e)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Connection failed", t)
                    runOnUiThread {
                        try {
                            val errorMessage = "Connection failed: ${t.message}"
                            viewModel.updateConnectionStatus(errorMessage)
                            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                            this@MainActivity.webSocket = null
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating UI on connection failure", e)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WebSocket", e)
            viewModel.updateConnectionStatus("Error: ${e.message}")
            Toast.makeText(this, "Error connecting: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnectFromWebSocket() {
        try {
            Log.d(TAG, "Disconnecting from WebSocket")
            webSocket?.close(1000, "User disconnected")
            webSocket = null
            viewModel.updateConnectionStatus("Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from WebSocket", e)
        }
    }

    private fun updateConnectButton(status: String) {
        try {
            binding.connectButton.text = if (status == "Connected") "Disconnect" else "Connect"
        } catch (e: Exception) {
            Log.e(TAG, "Error updating connect button", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            webSocket?.close(1000, "Activity destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket on destroy", e)
        }
    }
} 
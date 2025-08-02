package com.example.websocketclient

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.websocketclient.databinding.ActivityMainBinding
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import java.net.*
import java.io.IOException
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var webSocket: WebSocket? = null
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var serverAdapter: ServerAdapter
    private var serverDiscovery: ServerDiscovery? = null
    private lateinit var dynamicApiClient: DynamicApiClient


    companion object {
        private const val TAG = "WebSocketClient"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        dynamicApiClient = DynamicApiClient()
        setupRecyclerViews() // Renamed from setupRecyclerView
        setupObservers()
        setupClickListeners()
        setupApiCallbacks()

        // Prefill manual connect field if running in emulator
        if (isEmulator()) {
            binding.serverUrlInput.editText?.setText("ws://10.0.2.2:8081/ws")
        }

        startServerDiscovery() // Start discovery instead of setDefaultServerUrlAndHint
    }

    private fun isEmulator(): Boolean {
        return try {
            val buildConfig = android.os.Build::class.java.getField("FINGERPRINT").get(null) as String
            buildConfig.contains("generic") ||
            buildConfig.contains("sdk") ||
            buildConfig.contains("google_sdk") ||
            buildConfig.contains("Emulator") ||
            buildConfig.contains("Android SDK")
        } catch (e: Exception) {
            Log.d("MainActivity", "Error detecting emulator: ${e.message}")
            false
        }
    }

    private fun setupApiCallbacks() {
        // Register callbacks for the 4 specific operations
        dynamicApiClient.registerCallback("getConfiguration", object : ApiCallback {
            override fun onSuccess(data: Map<String, String>, message: String) {
                runOnUiThread {
                    val displayMessage = "✅ Configuration received: $message\nData: $data"
                    viewModel.addMessage(WebSocketMessage(displayMessage, System.currentTimeMillis()))
                    Toast.makeText(this@MainActivity, "Configuration loaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Configuration error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
        
        dynamicApiClient.registerCallback("getData", object : ApiCallback {
            override fun onSuccess(data: Map<String, String>, message: String) {
                runOnUiThread {
                    val displayMessage = "📊 Data received: $message\nData: $data"
                    viewModel.addMessage(WebSocketMessage(displayMessage, System.currentTimeMillis()))

                    Toast.makeText(this@MainActivity, "Data retrieved successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Data error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
        
        dynamicApiClient.registerCallback("subscribe", object : ApiCallback {
            override fun onSuccess(data: Map<String, String>, message: String) {
                runOnUiThread {
                    val subscriptionId = data["subscription_id"] ?: "unknown"
                    val displayMessage = "🔔 Subscription created: $message\nSubscription ID: $subscriptionId"
                    viewModel.addMessage(WebSocketMessage(displayMessage, System.currentTimeMillis()))
                    Toast.makeText(this@MainActivity, "Subscribed successfully! ID: $subscriptionId", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Subscription error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
        
        dynamicApiClient.registerCallback("unsubscribe", object : ApiCallback {
            override fun onSuccess(data: Map<String, String>, message: String) {
                runOnUiThread {
                    val displayMessage = "🔕 Unsubscribed: $message\nData: $data"
                    viewModel.addMessage(WebSocketMessage(displayMessage, System.currentTimeMillis()))
                    Toast.makeText(this@MainActivity, "Unsubscribed successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Unsubscribe error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // Renamed and updated to setup both RecyclerViews
    private fun setupRecyclerViews() {
        try {
            messagesAdapter = MessagesAdapter()
            binding.messagesRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = messagesAdapter
            }

            serverAdapter = ServerAdapter { server ->
                handleServerClick(server)
            }
            binding.discoveredServersRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = serverAdapter
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up RecyclerViews", e)
        }
    }

    private fun setupObservers() {
        viewModel.messages.observe(this) { messages ->
            messagesAdapter.submitList(messages.toList())
            if (messages.isNotEmpty()) {
                binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
        }

    }

    private fun setupClickListeners() {
        binding.clearButton.setOnClickListener {
            viewModel.clearMessages()
        }

        // Manual connection toggle button
        binding.connectButton.setOnClickListener {
            if (webSocket == null) {
                // Not connected, try to connect
                val serverUrl = binding.serverUrlInput.editText?.text?.toString()?.trim()
                if (!serverUrl.isNullOrEmpty()) {
                    connectToServer(serverUrl)
                } else {
                    Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Connected, disconnect
                disconnectFromWebSocket()
            }
        }

        // Dynamic API Operation buttons
        binding.pingButton.setOnClickListener {
            sendDynamicApiRequest("getConfiguration")
        }
        
        binding.serverInfoButton.setOnClickListener {
            sendDynamicApiRequest("getData")
        }
        
        binding.echoButton.setOnClickListener {
            sendDynamicApiRequest("subscribe")
        }
        
        binding.calculateButton.setOnClickListener {
            sendDynamicApiRequest("unsubscribe")
        }
    }

    private fun handleServerClick(server: DiscoveredServer) {
        if (webSocket == null) {
            // Not connected, connect to this server
            connectToServer(server.wsUrl)
        } else if (currentServerUrl == server.wsUrl) {
            // Connected to this server, disconnect
            disconnectFromWebSocket()
        } else {
            // Connected to different server, switch to this one
            disconnectFromWebSocket()
            connectToServer(server.wsUrl)
        }
    }
 
    private var currentServerUrl: String? = null
 
    private fun connectToServer(serverUrl: String) {
        currentServerUrl = serverUrl
        connectToWebSocket(serverUrl)
    }

    private fun startServerDiscovery() {
        serverDiscovery = ServerDiscovery(
            onServerDiscovered = { server ->
                runOnUiThread {
                    Log.d(TAG, "Server discovered callback triggered: ${server.name}")
                    updateDiscoveredServersList() // Update list when a server is discovered
                    Log.d(TAG, "Server discovered via ${server.discoveryMethod}: ${server.name}")
                }
            },
            onDiscoveryError = { error ->
                runOnUiThread {
                    Log.w(TAG, "Server discovery error: $error")
                    // Don't show error toast for discovery failures
                }
            }
        )
        serverDiscovery?.startDiscovery()
    }

    private fun updateDiscoveredServersList() {
        val servers = serverDiscovery?.getDiscoveredServers() ?: emptyList()
        Log.d(TAG, "Updating discovered servers list: ${servers.size} servers")
        servers.forEach { server ->
            Log.d(TAG, "Server in list: ${server.name} (${server.wsUrl}) via ${server.discoveryMethod}")
        }
        
        Log.d(TAG, "Submitting list to adapter with ${servers.size} servers")
        serverAdapter.submitList(servers) // Submit the list to the adapter
        serverAdapter.setConnectedServer(currentServerUrl)

        if (servers.isNotEmpty()) {
            Log.d(TAG, "Showing discovered servers card - servers count: ${servers.size}")
            binding.discoveredServersCard.visibility = android.view.View.VISIBLE
            Log.d(TAG, "Card visibility set to VISIBLE")
        } else {
            Log.d(TAG, "Hiding discovered servers card - no servers")
            binding.discoveredServersCard.visibility = android.view.View.GONE
            Log.d(TAG, "Card visibility set to GONE")
        }
    }

    private fun sendDynamicApiRequest(operation: String, data: Map<String, String> = emptyMap()) {
        try {
            val jsonRequest = dynamicApiClient.sendRequest(operation, data)
            webSocket?.send(jsonRequest)
            Log.d(TAG, "Sent dynamic API request: $jsonRequest")
            
            // Add request to messages
            val displayMessage = " Sent: $operation"
            viewModel.addMessage(WebSocketMessage(displayMessage, System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send dynamic API request", e)
            Toast.makeText(this, "Failed to send API request: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToWebSocket(serverUrl: String) {
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Show connecting state
        runOnUiThread {
            binding.connectButton.text = "Connecting..."
            binding.connectButton.isEnabled = false
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connection opened successfully")
                    Log.d(TAG, "Response code: ${response.code}")
                    Log.d(TAG, "Response message: ${response.message}")
                    Log.d(TAG, "Response headers: ${response.headers}")
                    runOnUiThread {
                        try {
                            Toast.makeText(this@MainActivity, "Connected to server!", Toast.LENGTH_SHORT).show()

                            // Send a test message to verify the connection
                            Log.d(TAG, "Sending test message to verify connection")
                            webSocket.send("Android app connected")
                             
                            // Update the server adapter to show connected state
                            updateDiscoveredServersList()
                            updateConnectButtonState()
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
                            // Try to parse as API response first
                            try {
                                Log.d(TAG, "Attempting to parse as API response")
                                // Handle with dynamic API client
                                dynamicApiClient.handleResponse(text)
                                Log.d(TAG, "Successfully handled as API response")
                            } catch (e: Exception) {
                                // Fallback to regular message display
                                Log.d(TAG, "Not an API response, treating as regular message: ${e.message}")
                                Log.d(TAG, "Adding message to ViewModel: $text")
                                viewModel.addMessage(WebSocketMessage(text, System.currentTimeMillis()))
                            }
                            Log.d(TAG, "Message added successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding message", e)
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Connection closing: $code - $reason")
                    runOnUiThread {
                        this@MainActivity.webSocket = null // Clear the WebSocket
                        currentServerUrl = null
                        updateDiscoveredServersList()
                        updateConnectButtonState() // Update button state
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Connection closed: $code - $reason")
                    runOnUiThread {
                        this@MainActivity.webSocket = null // Clear the WebSocket
                        currentServerUrl = null
                        updateDiscoveredServersList()
                        updateConnectButtonState()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Connection failed", t)
                    runOnUiThread {
                        // Show a more user-friendly error message
                        val errorMessage = when {
                            t.message?.contains("ENETUNREACH") == true -> "Server not reachable. Check if server is running and IP is correct."
                            t.message?.contains("timeout") == true -> "Connection timeout. Server may be unreachable."
                            else -> "Connection failed: ${t.message}"
                        }
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                        
                        this@MainActivity.webSocket = null // Clear the failed WebSocket
                        currentServerUrl = null
                        updateDiscoveredServersList()
                        updateConnectButtonState() // Re-enable the button
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error creating WebSocket", e)
            Toast.makeText(this, "Error creating WebSocket: ${e.message}", Toast.LENGTH_LONG).show()
            runOnUiThread {
                updateConnectButtonState() // Re-enable the button on error
            }
        }
    }

    private fun updateConnectButtonState() {
        if (webSocket != null) {
            binding.connectButton.text = "Disconnect"
            binding.connectButton.icon = getDrawable(R.drawable.ic_unsubscribe)
        } else {
            binding.connectButton.text = "Connect"
            binding.connectButton.icon = getDrawable(R.drawable.ic_config)
        }
        binding.connectButton.isEnabled = true
    }

    private fun disconnectFromWebSocket() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        currentServerUrl = null
        updateDiscoveredServersList()
        updateConnectButtonState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            webSocket?.close(1000, "Activity destroyed")
            serverDiscovery?.stopDiscovery() // Stop discovery on destroy
            dynamicApiClient.clearCallbacks() // Clear all callbacks
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket on destroy", e)
        }
    }
}

// Configuration object for easy customization
const Config = {
    // Timeouts and delays
    IP_REQUEST_TIMEOUT: 10000, // 10 seconds
    RETRY_DELAY: 1000, // 1 second
    SUCCESS_INDICATOR_DURATION: 3000, // 3 seconds
    RECONNECT_DELAY: 3000, // 3 seconds
    
    // WebSocket settings
    WS_ENDPOINT: '/ws',
    
    // Display settings
    TIMESTAMP_LOCALE: 'en-US',
    TIMESTAMP_OPTIONS: { 
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    }
};

// Message types enum for better maintainability
const MessageType = {
    SERVER_MESSAGE: 'server-message',
    CLIENT_MESSAGE: 'client-message',
    ERROR: 'error'
};

// Server message prefixes - centralized for easy maintenance
const ServerMessages = {
    CLIENT_CONNECTED: 'CLIENT_CONNECTED:',
    CLIENT_DISCONNECTED: 'CLIENT_DISCONNECTED:',
    CLIENT_COUNT: 'CLIENT_COUNT:',
    API_REQUEST: 'API_REQUEST:',
    SERVER_IP: 'SERVER_IP:',
    GET_SERVER_IP: 'GET_SERVER_IP'
};

// Broadcast control state
let broadcastActive = false;

// Message icons mapping
const MessageIcons = {
    [MessageType.SERVER_MESSAGE]: '🗄️',
    [MessageType.CLIENT_MESSAGE]: '👤',
    [MessageType.ERROR]: '❌'
};

// Message parsing function for better maintainability
function parseServerMessage(message) {
    for (const [type, prefix] of Object.entries(ServerMessages)) {
        if (message.startsWith(prefix)) {
            return {
                type: type,
                payload: message.substring(prefix.length),
                prefix: prefix
            };
        }
    }
    return {
        type: 'UNKNOWN',
        payload: message,
        prefix: null
    };
}

let ws;
let clientCount = 0;

function updateSocketStatus(status, clients) {
    const statusElement = document.getElementById('socketConnectionStatus');
    const clientCountElement = document.getElementById('socketClientCount');
    
    if (statusElement && status !== undefined) {
        statusElement.textContent = status;
        // Remove all status classes
        statusElement.className = 'status-value';
        
        // Add appropriate class based on status
        if (status === 'Connected') {
            statusElement.classList.add('active');
        } else if (status === 'Server Ready') {
            statusElement.classList.add('active');
        } else if (status === 'Disconnected') {
            statusElement.classList.add('inactive');
        } else if (status === 'Connecting...') {
            statusElement.classList.add('status-connecting');
        } else if (status === 'Error') {
            statusElement.classList.add('inactive');
        }
    }
    
    if (clientCountElement && clients !== undefined) {
        clientCountElement.textContent = clients;
    }
}

function updateClientCount(count) {
    const clientCountElement = document.getElementById('socketClientCount');
    const statusElement = document.getElementById('socketConnectionStatus');
    
    if (clientCountElement) {
        // Subtract 1 because the dashboard itself is a client
        const otherClients = Math.max(0, count - 1);
        console.log('updateClientCount: Total clients:', count, 'Other clients:', otherClients);
        clientCountElement.textContent = otherClients;
        
        // Update connection status based on whether there are other clients
        if (statusElement) {
            if (otherClients > 0) {
                console.log('Setting status to Connected');
                statusElement.textContent = 'Connected';
                statusElement.className = 'status-value active';
            } else {
                console.log('Setting status to No Clients');
                statusElement.textContent = 'No Clients';
                statusElement.className = 'status-value';
            }
        }
    }
}

function connect() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}${Config.WS_ENDPOINT}`;
    
    // Ensure we show "Connecting..." state
    updateSocketStatus('Connecting...');
    
    ws = new WebSocket(wsUrl);
    
    ws.onopen = function() {
        console.log('Server: Connected to WebSocket');
        // Small delay to ensure connecting state is visible
        setTimeout(() => {
            updateSocketStatus('No Clients');
            // Reset client count to 0 initially
            updateClientCount(1); // Dashboard is 1 client, so 1-1=0 other clients
        }, 100);
    };
    
    ws.onclose = function() {
        console.log('Server: Disconnected from WebSocket');
        updateSocketStatus('Disconnected');
        setTimeout(() => {
            updateSocketStatus('Connecting...');
            connect();
        }, Config.RECONNECT_DELAY);
    };
    
    ws.onerror = function(error) {
        console.log('Server: WebSocket error:', error);
        updateSocketStatus('Error');
    };
    
    ws.onmessage = function(event) {
        const message = event.data;
        const parsedMessage = parseServerMessage(message);
        
        switch (parsedMessage.type) {
            case 'CLIENT_CONNECTED':
                addMessage('Server status: Client connected', MessageType.SERVER_MESSAGE);
                break;
            case 'CLIENT_DISCONNECTED':
                addMessage('Server status: Client disconnected', MessageType.SERVER_MESSAGE);
                break;
            case 'CLIENT_COUNT':
                const count = parseInt(parsedMessage.payload);
                console.log('Received CLIENT_COUNT:', count, 'Other clients:', Math.max(0, count - 1));
                clientCount = Math.max(0, count - 1);
                updateClientCount(count);
                break;
            case 'API_REQUEST':
                handleApiRequest(parsedMessage.payload);
                break;
            case 'SERVER_IP':
                updateServerUrlDisplay(parsedMessage.payload);
                break;
            default:
                // Try to parse as broadcast control response
                try {
                    const response = JSON.parse(message);
                    if (response.action && response.success !== undefined) {
                        handleBroadcastControlResponse(response);
                        return;
                    }
                } catch (e) {
                    // Not a JSON response, treat as regular message
                }
                addMessage(`Server received from client: ${message}`, MessageType.SERVER_MESSAGE);
        }
    };
}

function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const message = messageInput.value.trim();
    
    if (message && ws && ws.readyState === WebSocket.OPEN) {
        ws.send(message);
        addMessage(`Server sent: ${message}`, MessageType.SERVER_MESSAGE);
        messageInput.value = '';
    }
}

function addMessage(text, type) {
    const messagesDiv = document.getElementById('messages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;
    
    const timestamp = new Date().toLocaleTimeString(Config.TIMESTAMP_LOCALE, Config.TIMESTAMP_OPTIONS);
    
    // Get icon based on message type, fallback to a neutral icon
    const icon = MessageIcons[type] || '💬';
    
    messageDiv.innerHTML = `
        <div class="message-icon">${icon}</div>
        <div class="message-content">${timestamp}: ${text}</div>
    `;
    
    messagesDiv.appendChild(messageDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function handleApiRequest(apiRequestJson) {
    try {
        const apiRequest = JSON.parse(apiRequestJson);
        addMessage(`Server received API request: ${apiRequest.action}`, MessageType.SERVER_MESSAGE);
        
        const container = document.getElementById('apiRequestsContainer');
        container.innerHTML = '';
        
        const actionDiv = document.createElement('div');
        actionDiv.className = 'action-item';
        actionDiv.innerHTML = `
            <div class="action-content">
                <div class="action-title">${apiRequest.action}</div>
                <div class="action-description">API Request from client</div>
            </div>
            <div class="action-data">${JSON.stringify(apiRequest, null, 2)}</div>
            <div class="response-section">
                <textarea id="responseInput" placeholder="Enter response JSON..." rows="4"></textarea>
                <button type="button" class="button send-api-response-btn" data-request-id="${apiRequest.requestId}">Send Response</button>
            </div>
        `;
        
        container.appendChild(actionDiv);
        
        // Attach event listener to the dynamically created button
        const responseBtn = actionDiv.querySelector('.send-api-response-btn');
        if (responseBtn) {
            responseBtn.addEventListener('click', function() {
                sendApiResponse(this.getAttribute('data-request-id'));
            });
        }
        
        // Auto-generate default response
        const defaultResponse = generateDefaultResponse(apiRequest);
        document.getElementById('responseInput').value = JSON.stringify(defaultResponse, null, 2);
        
    } catch (error) {
        addMessage(`Server: Error parsing API request ${error}`, MessageType.ERROR);
    }
}

function generateDefaultResponse(apiRequest) {
    return {
        action: apiRequest.action,
        success: true,
        message: `${apiRequest.action} completed successfully`,
        requestId: apiRequest.requestId,
        data: apiRequest.data || null
    };
}

function sendApiResponse(requestId) {
    const responseInput = document.getElementById('responseInput');
    const responseText = responseInput.value.trim();
    
    if (responseText && ws && ws.readyState === WebSocket.OPEN) {
        try {
            const response = JSON.parse(responseText);
            ws.send(JSON.stringify(response));
            addMessage(`Server sent: API Response ${response.action}`, MessageType.SERVER_MESSAGE);
            
            // Clear the API requests container
            document.getElementById('apiRequestsContainer').innerHTML = 
                '<p class="no-requests">No pending API requests</p>';
                
        } catch (error) {
            addMessage(`Server: Error sending API response ${error}`, MessageType.ERROR);
        }
    }
}

let ipRequestTimeout;

function requestServerIpAddress() {
    // Send a special message to request the server's IP address
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(ServerMessages.GET_SERVER_IP);
        console.log('Requesting server IP address...');
        
        // Set a timeout in case the request fails
        ipRequestTimeout = setTimeout(() => {
            console.log('IP detection failed - timeout reached');
            const loadingIndicator = document.getElementById('ipLoadingIndicator');
            if (loadingIndicator) {
                loadingIndicator.innerHTML = '<span style="color: #EF4444;">❌ IP detection failed</span>';
            }
        }, Config.IP_REQUEST_TIMEOUT);
    } else {
        // If WebSocket isn't ready yet, wait a bit and try again
        setTimeout(requestServerIpAddress, Config.RETRY_DELAY);
    }
}

function updateServerUrlDisplay(serverIp) {
    // Clear the timeout since we got the IP
    if (ipRequestTimeout) {
        clearTimeout(ipRequestTimeout);
        ipRequestTimeout = null;
    }
    
    document.getElementById('serverHost').textContent = serverIp;
    console.log('Server IP detected:', serverIp);
    
    // Hide the loading indicator
    const loadingIndicator = document.getElementById('ipLoadingIndicator');
    if (loadingIndicator) {
        loadingIndicator.style.display = 'none';
    }
    
    // Add a success indicator briefly
    const urlDisplay = document.getElementById('serverUrlDisplay');
    const successDiv = document.createElement('div');
    successDiv.style.cssText = 'display: inline-block; margin-left: 8px; color: #10B981; font-size: 12px;';
    successDiv.textContent = '✅ IP detected';
    urlDisplay.appendChild(successDiv);
    
    // Remove success indicator after configured duration
    setTimeout(() => {
        if (successDiv.parentNode) {
            successDiv.remove();
        }
    }, Config.SUCCESS_INDICATOR_DURATION);
}

function clearMessages() {
    const messagesDiv = document.getElementById('messages');
    messagesDiv.innerHTML = ''; // Clear all messages
}

// Auto-connect on page load
window.onload = function() {
    // Request server IP address from the server
    requestServerIpAddress();
    connect();
};

// Broadcast control functions
function startBroadcast() {
    const interval = parseInt(document.getElementById('broadcastInterval').value);
    const port = parseInt(document.getElementById('broadcastPort').value);
    const message = document.getElementById('broadcastMessage').value;
    
    if (interval < 500) {
        addMessage('Interval must be at least 500ms', MessageType.ERROR);
        return;
    }
    
    if (port < 1 || port > 65535) {
        addMessage('Port must be between 1 and 65535', MessageType.ERROR);
        return;
    }
    
    const request = {
        action: 'start',
        interval: interval,
        port: port,
        message: message,
        requestId: generateRequestId()
    };
    
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(request));
        addMessage(`Server starting broadcast on port: ${port} with interval: ${interval}ms`, MessageType.SERVER_MESSAGE);
    } else {
        addMessage('WebSocket not connected', MessageType.ERROR);
    }
}

function stopBroadcast() {
    const request = {
        action: 'stop',
        requestId: generateRequestId()
    };
    
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(request));
        addMessage('Server stopping broadcast', MessageType.SERVER_MESSAGE);
    } else {
        addMessage('WebSocket not connected', MessageType.ERROR);
    }
}

function getBroadcastStatus() {
    const request = {
        action: 'status',
        requestId: generateRequestId()
    };
    
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(request));
        addMessage('Requesting server broadcast status', MessageType.SERVER_MESSAGE);
    } else {
        addMessage('WebSocket not connected', MessageType.ERROR);
    }
}

function generateRequestId() {
    return 'req_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

function handleBroadcastControlResponse(response) {
    console.log('Received broadcast control response:', response);
    
    if (response.success) {
        addMessage(`Server broadcast ${response.action}: ${response.message}`, MessageType.SERVER_MESSAGE);
        
        // If this is a status response, update the status display
        if (response.action === 'status' && response.status) {
            updateBroadcastStatus(response.status);
        } else if (response.action === 'start' || response.action === 'stop') {
            // Refresh status after start/stop actions
            setTimeout(() => getBroadcastStatus(), 500);
        }
    } else {
        addMessage(`Server broadcast ${response.action} failed: ${response.message}`, MessageType.ERROR);
    }
}

function updateBroadcastStatus(status) {
    const container = document.getElementById('broadcastStatusContainer');
    container.innerHTML = '';
    
    const statusDiv = document.createElement('div');
    statusDiv.className = `broadcast-status ${status.isActive ? 'active' : 'inactive'}`;
    
    statusDiv.innerHTML = `
        <div class="status-item">
            <span class="status-label">Status:</span>
            <span class="status-value ${status.isActive ? 'active' : 'inactive'}">${status.isActive ? 'Active' : 'Inactive'}</span>
        </div>
        <div class="status-item">
            <span class="status-label">Port:</span>
            <span class="status-value">${status.port || 'N/A'}</span>
        </div>
        <div class="status-item">
            <span class="status-label">Interval:</span>
            <span class="status-value">${status.interval}ms</span>
        </div>
        <div class="status-item">
            <span class="status-label">Clients:</span>
            <span class="status-value">${status.clientsConnected}</span>
        </div>
        <div class="status-item">
            <span class="status-label">Messages Sent:</span>
            <span class="status-value">${status.messagesSent}</span>
        </div>
    `;
    
    container.appendChild(statusDiv);
    
    // Update button states
    const startBtn = document.getElementById('startBroadcastBtn');
    const stopBtn = document.getElementById('stopBroadcastBtn');
    
    if (status.isActive) {
        startBtn.disabled = true;
        stopBtn.disabled = false;
        broadcastActive = true;
    } else {
        startBtn.disabled = false;
        stopBtn.disabled = true;
        broadcastActive = false;
    }
}

// Initialize event listeners when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    // Prevent form submissions
    const broadcastForm = document.getElementById('broadcastForm');
    const messageForm = document.getElementById('messageForm');
    
    if (broadcastForm) {
        broadcastForm.addEventListener('submit', function(e) {
            e.preventDefault();
        });
    }
    
    if (messageForm) {
        messageForm.addEventListener('submit', function(e) {
            e.preventDefault();
        });
    }
    
    // Broadcast control buttons
    const startBroadcastBtn = document.getElementById('startBroadcastBtn');
    const stopBroadcastBtn = document.getElementById('stopBroadcastBtn');
    const getBroadcastStatusBtn = document.getElementById('getBroadcastStatusBtn');
    
    if (startBroadcastBtn) {
        startBroadcastBtn.addEventListener('click', startBroadcast);
    }
    
    if (stopBroadcastBtn) {
        stopBroadcastBtn.addEventListener('click', stopBroadcast);
    }
    
    if (getBroadcastStatusBtn) {
        getBroadcastStatusBtn.addEventListener('click', getBroadcastStatus);
    }
    
    // Message control buttons
    const sendMessageBtn = document.getElementById('sendMessageBtn');
    const clearMessagesBtn = document.getElementById('clearMessagesBtn');
    
    if (sendMessageBtn) {
        sendMessageBtn.addEventListener('click', sendMessage);
    }
    
    if (clearMessagesBtn) {
        clearMessagesBtn.addEventListener('click', clearMessages);
    }
    
    // Set default broadcast message template
    const broadcastMessage = document.getElementById('broadcastMessage');
    if (broadcastMessage && !broadcastMessage.value) {
        broadcastMessage.value = '{"type":"broadcast","timestamp":%d,"messageNumber":%d,"clients":%d}';
    }
    
    // Note: Default UDP port is 2505 for server discovery
    
    // Handle Enter key in message input
    const messageInput = document.getElementById('messageInput');
    if (messageInput) {
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }
});

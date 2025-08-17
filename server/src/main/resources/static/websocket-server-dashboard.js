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
    API_REQUEST: 'API_REQUEST:',
    SERVER_IP: 'SERVER_IP:',
    GET_SERVER_IP: 'GET_SERVER_IP'
};

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

function connect() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    
    ws = new WebSocket(wsUrl);
    
    ws.onopen = function() {
        console.log('Server: Connected to WebSocket');
    };
    
    ws.onclose = function() {
        console.log('Server: Disconnected from WebSocket');
        setTimeout(connect, 3000);
    };
    
    ws.onerror = function(error) {
        console.log('Server: WebSocket error:', error);
    };
    
    ws.onmessage = function(event) {
        const message = event.data;
        const parsedMessage = parseServerMessage(message);
        
        switch (parsedMessage.type) {
            case 'CLIENT_CONNECTED':
                addMessage('Server status: Client connected', MessageType.SERVER_MESSAGE);
                clientCount++;
                break;
            case 'CLIENT_DISCONNECTED':
                addMessage('Server status: Client disconnected', MessageType.SERVER_MESSAGE);
                clientCount--;
                break;
            case 'API_REQUEST':
                handleApiRequest(parsedMessage.payload);
                break;
            case 'SERVER_IP':
                updateServerUrlDisplay(parsedMessage.payload);
                break;
            default:
                addMessage(`Client sent: ${message}`, MessageType.CLIENT_MESSAGE);
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
    
    const timestamp = new Date().toLocaleTimeString('en-US', { 
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
    
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
        addMessage(`Client sent: API Request ${apiRequest.operation}`, MessageType.CLIENT_MESSAGE);
        
        const container = document.getElementById('apiRequestsContainer');
        container.innerHTML = '';
        
        const operationDiv = document.createElement('div');
        operationDiv.className = 'operation-item';
        operationDiv.innerHTML = `
            <div class="operation-content">
                <div class="operation-title">${apiRequest.operation}</div>
                <div class="operation-description">API Request from client</div>
            </div>
            <div class="operation-data">${JSON.stringify(apiRequest, null, 2)}</div>
            <div class="response-section">
                <textarea id="responseInput" placeholder="Enter response JSON..." rows="4"></textarea>
                <button class="button" onclick="sendApiResponse('${apiRequest.requestId}')">Send Response</button>
            </div>
        `;
        
        container.appendChild(operationDiv);
        
        // Auto-generate default response
        const defaultResponse = generateDefaultResponse(apiRequest);
        document.getElementById('responseInput').value = JSON.stringify(defaultResponse, null, 2);
        
    } catch (error) {
        addMessage(`Server: Error parsing API request ${error}`, MessageType.ERROR);
    }
}

function generateDefaultResponse(apiRequest) {
    const baseResponse = {
        operation: apiRequest.operation,
        success: true,
        message: `${apiRequest.operation} completed successfully`,
        requestId: apiRequest.requestId
    };

    switch (apiRequest.operation) {
        case 'getConfiguration':
            return {
                ...baseResponse,
                data: {
                    serverVersion: "1.0.0",
                    maxConnections: "100",
                    timeout: "30s"
                }
            };
        case 'getData':
            return {
                ...baseResponse,
                data: {
                    items: [
                        { id: 1, name: "Item 1", value: 100 },
                        { id: 2, name: "Item 2", value: 200 }
                    ],
                    total: 2
                }
            };
        case 'subscribe':
            return {
                ...baseResponse,
                data: {
                    subscriptionId: (apiRequest.data && apiRequest.data.subscriptionId) || 'sub_' + Date.now(),
                    status: "active"
                }
            };
        case 'unsubscribe':
            return {
                ...baseResponse,
                data: {
                    subscriptionId: (apiRequest.data && apiRequest.data.subscriptionId) || 'unknown',
                    status: "inactive"
                }
            };
        default:
            return baseResponse;
    }
}

function sendApiResponse(requestId) {
    const responseInput = document.getElementById('responseInput');
    const responseText = responseInput.value.trim();
    
    if (responseText && ws && ws.readyState === WebSocket.OPEN) {
        try {
            const response = JSON.parse(responseText);
            ws.send(JSON.stringify(response));
            addMessage(`Server sent: API Response ${response.operation}`, MessageType.SERVER_MESSAGE);
            
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
        }, 10000); // 10 second timeout
    } else {
        // If WebSocket isn't ready yet, wait a bit and try again
        setTimeout(requestServerIpAddress, 1000);
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
    
    // Remove success indicator after 3 seconds
    setTimeout(() => {
        if (successDiv.parentNode) {
            successDiv.remove();
        }
    }, 3000);
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

// Handle Enter key in message input
document.addEventListener('DOMContentLoaded', function() {
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

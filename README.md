# WebSocket Mock Server

A simple Kotlin WebSocket server built with Ktor that provides a mock WebSocket endpoint for testing and development, with an Android example client.

## Features

- WebSocket endpoint at `/ws`
- Static file serving
- Echo functionality for WebSocket messages
- Automatic browser opening
- **Android example app** demonstrating WebSocket client usage

## Prerequisites

- Java JDK 8 or higher
- Gradle (included via wrapper)
- Android Studio (for running the Android example)

## Quick Start

### 1. Start the WebSocket Server

#### Option 1: Auto-start with browser (Recommended)
```bash
./start-server.sh
```
This will:
- Free port 8081 if needed
- Start the server
- Automatically open your browser to `http://localhost:8081`

#### Option 2: Manual start
```bash
./gradlew :server:run
```
Then manually open `http://localhost:8081` in your browser.

### 2. Run the Android Example App

1. Open the project in Android Studio
2. Build and run the Android app (`:examples:android`) on an emulator or device
3. The app comes pre-configured to connect to `ws://10.0.2.2:8081/ws` (for emulator)
4. Tap "Connect" to establish the WebSocket connection
5. View real-time messages from the server

## Usage

### WebSocket Connection
Connect to the WebSocket endpoint at:
```
ws://localhost:8081/ws
```

### Testing WebSocket
1. Open the browser to `http://localhost:8081`
2. Use browser developer tools or a WebSocket client
3. Send messages to the WebSocket endpoint
4. The server will echo back: "Echo: [your message]"

### Android Client
The Android example app demonstrates:
- WebSocket connection management
- Real-time message display
- Connection status monitoring
- Clean Material Design UI

## Project Structure

```
websocket-mock-server/
├── server/                    # WebSocket server module
│   ├── src/main/kotlin/com/websocketmockserver/
│   │   └── Application.kt     # Main server application
│   └── src/main/resources/
│       ├── static/            # Static files
│       └── application.conf   # Configuration
├── examples/
│   └── android/              # Android example app
│       ├── src/main/java/com/example/websocketclient/
│       │   ├── MainActivity.kt
│       │   ├── MainViewModel.kt
│       │   ├── MessagesAdapter.kt
│       │   └── WebSocketMessage.kt
│       └── src/main/res/     # Android resources
├── build.gradle.kts          # Root build configuration
├── start-server.sh           # Auto-start script
└── README.md                 # This file
```

## Stopping the Server

Press `Ctrl+C` in the terminal where the server is running.

## Development

### Building
```bash
# Build everything
./gradlew build

# Build server only
./gradlew :server:build

# Build Android app only
./gradlew :examples:android:build
```

### Running with info
```bash
./gradlew runWithInfo
```

## Configuration

The server runs on port 8081 by default. You can change this in `server/src/main/kotlin/com/websocketmockserver/Application.kt`:

```kotlin
embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
    // ...
}
```

### Android Configuration

For different connection scenarios:
- **Android Emulator**: `ws://10.0.2.2:8081/ws` (10.0.2.2 routes to your computer's localhost)
- **Physical Device**: `ws://YOUR_COMPUTER_IP:8081/ws` (replace with your computer's actual IP)

## Dependencies

### Server
- **Ktor Server Core**: Web framework
- **Ktor Server Netty**: Netty engine
- **Ktor WebSockets**: WebSocket support
- **Ktor HTML Builder**: HTML templating
- **Logback**: Logging framework

### Android Example
- **OkHttp**: WebSocket client
- **AndroidX**: Modern Android components
- **Material Design**: UI components 
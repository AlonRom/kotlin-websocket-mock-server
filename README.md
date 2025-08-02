# WebSocket Mock Server

A simple Kotlin WebSocket server built with Ktor that provides a mock WebSocket endpoint for testing and development, with an Android example client.

## Features

- WebSocket endpoint at `/ws`
- Static file serving
- Echo functionality for WebSocket messages
- Automatic browser opening
- **Android example app** demonstrating WebSocket client usage
- **Automatic server discovery** via UDP broadcast
- **Network-agnostic design** - no hardcoded IP addresses

## Prerequisites

- Java JDK 8 or higher
- Gradle (included via wrapper)
- Android Studio (for running the Android example)

### Windows Users
- **Android Studio Terminal/Command Prompt**: Use `start-server.bat`
- **PowerShell**: Use `.\start-server.ps1` (recommended for better process management)
- **Git Bash/WSL**: Use `./start-server.sh` (same as macOS/Linux)

## Quick Start

### 1. Start the WebSocket Server

#### Option 1: Auto-start with browser (Recommended)

**On macOS/Linux:**
```bash
./start-server.sh
```

**On Windows:**
```cmd
start-server.bat
```

**On Windows (PowerShell):**
```powershell
.\start-server.ps1
```

This will:
- Free port 8081 if needed
- Start the server
- Automatically open your browser to `http://localhost:8081`
- Begin broadcasting server availability on the network

#### Option 2: Manual start
```bash
./gradlew :server:run
```
Then manually open `http://localhost:8081` in your browser.

### 2. Run the Android Example App

#### Option 1: Using Android Studio (Recommended)
1. Open the project in Android Studio
2. Wait for the project to sync and build
3. Select the `:examples:android` module in the run configuration dropdown
4. Choose your target device (emulator or physical device)
5. Click the "Run" button (green play icon) or press `Shift+F10`
6. The app will build and install on your device
7. The app will automatically discover and connect to the server

#### Option 2: Using Command Line
```bash
# Build and install the Android app
./gradlew :examples:android:installDebug

# Or run directly (requires connected device/emulator)
./gradlew :examples:android:run
```

#### Connection Configuration
- **Automatic Discovery**: The app automatically discovers servers on the network via UDP broadcast
- **Manual Connection**: You can manually enter any WebSocket server URL
- **Emulator Support**: Automatically detects emulator environments and prefills the connection URL
- **Network Agnostic**: Works with any network configuration without hardcoded IPs

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

## Android Client

The Android app automatically discovers the server on your network and provides a modern interface for testing WebSocket communication.

### Features
- **Automatic Server Discovery**: Discovers servers via UDP broadcast
- **Manual Connection**: Connect to any WebSocket server manually
- **Real-time Messaging**: Send and receive messages in real-time
- **API Testing**: Test various API operations with dynamic responses
- **Modern UI**: Material Design 3 with dark theme
- **Emulator Detection**: Automatically detects emulator environments

### Quick Start

1. **Build and Install**:
   ```bash
   ./gradlew :examples:android:installDebug
   ```

2. **Launch the App**: The app will automatically discover the server on your network

### Connection Methods

- **Automatic Discovery**: The app listens for UDP broadcasts and automatically discovers available servers
- **Manual Connection**: Enter any WebSocket URL manually in the input field
- **Emulator Support**: Automatically detects emulator environments and prefills the connection URL

### Network Configuration

- **Real Device**: Automatically discovers the server using your computer's network IP
- **Emulator**: Automatically detects and prefills the emulator host address (`10.0.2.2`)
- **Manual**: You can enter any WebSocket URL manually

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
│       │   ├── ServerDiscovery.kt
│       │   └── WebSocketMessage.kt
│       └── src/main/res/     # Android resources
├── build.gradle.kts          # Root build configuration
├── start-server.sh           # Auto-start script
└── README.md                 # This file
```

## Stopping the Server

Press `Ctrl+C` in the terminal where the server is running.

If the server doesn't stop properly, you can force kill all Gradle processes:
```bash
pkill -f "gradle.*run"
```

**Alternative kill commands:**
```bash
# Kill all Gradle run processes
pkill -f "gradle.*run"

# Kill specific port (if using different port)
lsof -ti:8081 | xargs kill -9

# Kill all Java processes (nuclear option)
pkill -f java
```

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

The Android app automatically discovers servers on the network and supports multiple connection methods:
- **Automatic Discovery**: Discovers servers via UDP broadcast
- **Manual Connection**: Enter any WebSocket URL manually
- **Emulator Detection**: Automatically detects emulator environments and prefills connection URLs
- **Network Agnostic**: Works with any network configuration without hardcoded IPs

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
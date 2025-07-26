# Mock WebSocket Server

A simple Kotlin WebSocket server built with Ktor that provides a mock WebSocket endpoint for testing and development.

## Features

- WebSocket endpoint at `/ws`
- Static file serving
- Echo functionality for WebSocket messages
- Automatic browser opening

## Prerequisites

- Java JDK 8 or higher
- Gradle (included via wrapper)

## Quick Start

### Option 1: Auto-start with browser (Recommended)
```bash
./start-server.sh
```
This will:
- Free port 8081 if needed
- Start the server
- Automatically open your browser to `http://localhost:8081`

### Option 2: Manual start
```bash
./gradlew run
```
Then manually open `http://localhost:8081` in your browser.

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

### Static Files
Static files are served from the `src/main/resources/static/` directory.

## Project Structure

```
websocket-mock-server/
├── src/main/kotlin/com/example/mockserver/
│   └── Application.kt          # Main server application
├── src/main/resources/
│   ├── static/                 # Static files
│   └── application.conf        # Configuration
├── build.gradle.kts           # Build configuration
├── start-server.sh            # Auto-start script
└── README.md                  # This file
```

## Stopping the Server

Press `Ctrl+C` in the terminal where the server is running.

## Development

### Building
```bash
./gradlew build
```

### Running with info
```bash
./gradlew runWithInfo
```

## Configuration

The server runs on port 8081 by default. You can change this in `Application.kt`:

```kotlin
embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
    // ...
}
```

## Dependencies

- **Ktor Server Core**: Web framework
- **Ktor Server Netty**: Netty engine
- **Ktor WebSockets**: WebSocket support
- **Ktor HTML Builder**: HTML templating
- **Logback**: Logging framework 
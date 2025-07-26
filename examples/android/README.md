# Android WebSocket Client Example

This is an example Android app that demonstrates how to connect to the WebSocket Mock Server and receive messages.

## Features

- Connect to a WebSocket server
- Display real-time messages with timestamps
- Connection status indicator
- Clean Material Design UI

## How to Use

1. **Start the WebSocket Server**
   ```bash
   # From the project root
   ./gradlew :server:run
   ```

2. **Run the Android App**
   - Open the project in Android Studio
   - Build and run the Android app on an emulator or device

3. **Connect to the Server**
   - **For Android Emulator:**
     - Use the default URL: `ws://10.0.2.2:8081/ws` (10.0.2.2 routes to your computer's localhost)
   - **For Real Device:**
     - Find your computer's local IP address (on the same WiFi as your device):
       - On macOS, run: `ipconfig getifaddr en0` (or use `ifconfig` and look for an address like `192.168.x.x` or `10.x.x.x`)
     - Enter the URL in the app: `ws://YOUR_COMPUTER_IP:8081/ws`
     - Example: `ws://10.100.102.99:8081/ws`
   - Tap "Connect" to establish the WebSocket connection

4. **View Messages**
   - Once connected, any messages sent from the server will appear in the list
   - Each message shows the content and timestamp
   - Use the "Clear" button to clear the message history

## Configuration

### For Android Emulator
- Use `ws://10.0.2.2:8081/ws` (10.0.2.2 is the special IP that routes to your computer's localhost)

### For Physical Device
- Use `ws://YOUR_COMPUTER_IP:8081/ws` (replace YOUR_COMPUTER_IP with your computer's actual IP address)
- Make sure your device and computer are on the same WiFi network
- Make sure your computer's firewall allows incoming connections on port 8081

## Dependencies

- OkHttp WebSocket client for WebSocket communication
- AndroidX components for modern Android development
- Material Design components for UI

## Architecture

The app uses MVVM architecture with:
- **MainActivity**: Handles UI and WebSocket connection
- **MainViewModel**: Manages app state and data
- **MessagesAdapter**: Displays messages in a RecyclerView
- **WebSocketMessage**: Data class for message representation 
#!/bin/bash

# Kill any existing processes on port 8081 and Gradle processes
echo "Freeing port 8081 and cleaning up processes..."
lsof -ti:8081 | xargs kill -9 2>/dev/null || true
pkill -f "gradle.*run" 2>/dev/null || true
sleep 2

# Start the server in the background
echo "Starting the server..."
./gradlew run &
SERVER_PID=$!

# Wait for server to start
echo "Waiting for server to start..."
sleep 5

# Open browser
echo "Opening browser..."
open http://localhost:8081

# Wait for user to stop the server
echo "Server is running. Press Ctrl+C to stop."
wait $SERVER_PID 
#!/bin/bash

# Kill any existing processes on port 8081 and Gradle processes
echo "Freeing port 8081 and cleaning up processes..."
if command -v lsof >/dev/null 2>&1; then
  lsof -ti:8081 | xargs kill -9 2>/dev/null || true
else
  echo "Skipping 8081 cleanup: lsof not found"
fi
pkill -f "gradle.*run" 2>/dev/null || true
sleep 2

# Start the server in the background
echo "Starting the server..."
./gradlew :server:run &
SERVER_PID=$!

# Wait for server to start
echo "Waiting for server to start..."
sleep 5

# Open browser
echo "Opening browser..."
if command -v open >/dev/null 2>&1; then
  open http://localhost:8081
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open http://localhost:8081 >/dev/null 2>&1 || true
else
  echo "Please open http://localhost:8081 manually (no suitable browser opener found)"
fi

# Wait for user to stop the server
echo "Server is running. Press Ctrl+C to stop."
wait $SERVER_PID 
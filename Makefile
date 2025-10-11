.PHONY: start stop restart build clean help

# Default target
help:
	@echo "WebSocket Mock Server - Available commands:"
	@echo ""
	@echo "  make start    - Start the WebSocket server with auto-browser open"
	@echo "  make stop     - Stop the running server"
	@echo "  make restart  - Restart the server (stop + start)"
	@echo "  make build    - Build the entire project"
	@echo "  make clean    - Clean build artifacts"
	@echo "  make help     - Show this help message"
	@echo ""

# Start the server using the start script
start:
	@echo "Starting WebSocket server..."
	@./start-server.sh

# Stop the server by killing gradle run processes
stop:
	@echo "Stopping WebSocket server..."
	@pkill -f "gradle.*run" 2>/dev/null || echo "No server process found"
	@echo "Server stopped."

# Restart the server
restart: stop
	@sleep 2
	@$(MAKE) start

# Build the project
build:
	@echo "Building project..."
	@./gradlew build

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@./gradlew clean


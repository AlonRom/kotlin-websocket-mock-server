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

# Platform-specific start/stop commands
ifeq ($(OS),Windows_NT)
START_CMD = powershell -NoProfile -ExecutionPolicy Bypass -File "$(CURDIR)/start-server.ps1"
STOP_CMD = powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host 'Stopping WebSocket server...'; Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess | ForEach-Object { try { Stop-Process -Id $$_.OwningProcess -Force -ErrorAction SilentlyContinue } catch {} }; Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $$_.CommandLine -like '*:server:run*' } | ForEach-Object { try { Stop-Process -Id $$_.Id -Force -ErrorAction SilentlyContinue } catch {} }; Write-Host 'Server stopped.'"
else
# macOS and Linux default to the POSIX shell script
START_CMD = "$(CURDIR)/start-server.sh"
STOP_CMD = sh -c 'echo "Stopping WebSocket server..."; pkill -f "gradle.*run" 2>/dev/null && echo "Server stopped." || echo "No server process found"'
endif

# Start the server using the platform-specific command
start:
	@echo "Starting WebSocket server..."
	@$(START_CMD)

# Stop the server using the platform-specific command
stop:
	@$(STOP_CMD)

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


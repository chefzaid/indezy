#!/bin/bash

# Indezy Development Startup Script
# This script starts both frontend and backend services for development

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

# Function to check if port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0  # Port is in use
    else
        return 1  # Port is free
    fi
}

# Function to wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1
    
    print_status "Waiting for $service_name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" >/dev/null 2>&1; then
            print_status "$service_name is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 2
        ((attempt++))
    done
    
    print_error "$service_name failed to start within expected time"
    return 1
}

# Function to start backend
start_backend() {
    print_header "Starting Backend Server"
    
    if check_port 8080; then
        print_warning "Port 8080 is already in use. Backend may already be running."
        return 0
    fi
    
    cd /workspace/backend
    
    print_status "Starting Spring Boot application..."
    nohup ./mvnw spring-boot:run -Dspring.profiles.active=devcontainer > /tmp/backend.log 2>&1 &
    local backend_pid=$!
    
    echo $backend_pid > /tmp/backend.pid
    print_status "Backend started with PID: $backend_pid"
    
    # Wait for backend to be ready
    if wait_for_service "http://localhost:8080/api/actuator/health" "Backend"; then
        print_status "Backend is ready at http://localhost:8080/api"
    else
        print_error "Backend failed to start. Check logs: tail -f /tmp/backend.log"
        return 1
    fi
}

# Function to start frontend
start_frontend() {
    print_header "Starting Frontend Server"
    
    if check_port 4200; then
        print_warning "Port 4200 is already in use. Frontend may already be running."
        return 0
    fi
    
    cd /workspace/frontend
    
    print_status "Starting Angular development server..."
    nohup npm start > /tmp/frontend.log 2>&1 &
    local frontend_pid=$!
    
    echo $frontend_pid > /tmp/frontend.pid
    print_status "Frontend started with PID: $frontend_pid"
    
    # Wait for frontend to be ready
    if wait_for_service "http://localhost:4200" "Frontend"; then
        print_status "Frontend is ready at http://localhost:4200"
    else
        print_error "Frontend failed to start. Check logs: tail -f /tmp/frontend.log"
        return 1
    fi
}

# Function to show status
show_status() {
    print_header "Development Environment Status"
    
    echo "🌐 Services:"
    echo "  • Frontend:  http://localhost:4200"
    echo "  • Backend:   http://localhost:8080/api"
    echo "  • Swagger:   http://localhost:8080/api/swagger-ui.html"
    echo "  • pgAdmin:   http://localhost:5050"
    echo "  • Database:  localhost:5432"
    echo ""
    echo "📋 Process Information:"
    
    if [ -f /tmp/backend.pid ]; then
        local backend_pid=$(cat /tmp/backend.pid)
        if ps -p $backend_pid > /dev/null 2>&1; then
            echo "  • Backend PID: $backend_pid (running)"
        else
            echo "  • Backend: not running"
        fi
    else
        echo "  • Backend: not started by this script"
    fi
    
    if [ -f /tmp/frontend.pid ]; then
        local frontend_pid=$(cat /tmp/frontend.pid)
        if ps -p $frontend_pid > /dev/null 2>&1; then
            echo "  • Frontend PID: $frontend_pid (running)"
        else
            echo "  • Frontend: not running"
        fi
    else
        echo "  • Frontend: not started by this script"
    fi
    
    echo ""
    echo "📝 Logs:"
    echo "  • Backend:  tail -f /tmp/backend.log"
    echo "  • Frontend: tail -f /tmp/frontend.log"
}

# Function to stop services
stop_services() {
    print_header "Stopping Development Services"
    
    if [ -f /tmp/backend.pid ]; then
        local backend_pid=$(cat /tmp/backend.pid)
        if ps -p $backend_pid > /dev/null 2>&1; then
            print_status "Stopping backend (PID: $backend_pid)..."
            kill $backend_pid
            rm -f /tmp/backend.pid
        fi
    fi
    
    if [ -f /tmp/frontend.pid ]; then
        local frontend_pid=$(cat /tmp/frontend.pid)
        if ps -p $frontend_pid > /dev/null 2>&1; then
            print_status "Stopping frontend (PID: $frontend_pid)..."
            kill $frontend_pid
            rm -f /tmp/frontend.pid
        fi
    fi
    
    print_status "Services stopped"
}

# Main script logic
case "$1" in
    "start")
        print_header "Starting Indezy Development Environment"
        start_backend
        start_frontend
        show_status
        ;;
    "stop")
        stop_services
        ;;
    "restart")
        stop_services
        sleep 2
        start_backend
        start_frontend
        show_status
        ;;
    "status")
        show_status
        ;;
    "backend")
        start_backend
        ;;
    "frontend")
        start_frontend
        ;;
    *)
        print_header "Indezy Development Startup Script"
        echo "Usage: $0 {start|stop|restart|status|backend|frontend}"
        echo ""
        echo "Commands:"
        echo "  start     - Start both backend and frontend"
        echo "  stop      - Stop both services"
        echo "  restart   - Restart both services"
        echo "  status    - Show current status"
        echo "  backend   - Start only backend"
        echo "  frontend  - Start only frontend"
        echo ""
        echo "Examples:"
        echo "  $0 start    # Start full development environment"
        echo "  $0 status   # Check what's running"
        echo "  $0 stop     # Stop all services"
        ;;
esac

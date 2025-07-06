#!/bin/bash

# Health check script for Indezy development environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC} $1"
}

print_header() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

# Check if running in devcontainer
check_devcontainer() {
    if [ -n "$REMOTE_CONTAINERS" ] || [ -n "$CODESPACES" ]; then
        print_status "Running in development container"
        return 0
    else
        print_warning "Not running in development container"
        return 1
    fi
}

# Check Java installation
check_java() {
    if command -v java &> /dev/null; then
        local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
        print_status "Java installed: $java_version"
        return 0
    else
        print_error "Java not found"
        return 1
    fi
}

# Check Maven installation
check_maven() {
    if command -v mvn &> /dev/null; then
        local maven_version=$(mvn -version 2>&1 | head -n 1 | cut -d' ' -f3)
        print_status "Maven installed: $maven_version"
        return 0
    else
        print_error "Maven not found"
        return 1
    fi
}

# Check Node.js installation
check_node() {
    if command -v node &> /dev/null; then
        local node_version=$(node --version)
        print_status "Node.js installed: $node_version"
        return 0
    else
        print_error "Node.js not found"
        return 1
    fi
}

# Check Angular CLI installation
check_angular() {
    if command -v ng &> /dev/null; then
        local ng_version=$(ng version --skip-git 2>/dev/null | grep "Angular CLI" | cut -d':' -f2 | xargs)
        print_status "Angular CLI installed: $ng_version"
        return 0
    else
        print_error "Angular CLI not found"
        return 1
    fi
}

# Check PostgreSQL client
check_psql() {
    if command -v psql &> /dev/null; then
        local psql_version=$(psql --version | cut -d' ' -f3)
        print_status "PostgreSQL client installed: $psql_version"
        return 0
    else
        print_error "PostgreSQL client not found"
        return 1
    fi
}

# Check database connection
check_database() {
    if pg_isready -h postgres -p 5432 -U indezy_user -d indezy &> /dev/null; then
        print_status "Database connection successful"
        return 0
    else
        print_error "Database connection failed"
        return 1
    fi
}

# Check backend dependencies
check_backend() {
    if [ -f "/workspace/backend/pom.xml" ]; then
        print_status "Backend project found"
        cd /workspace/backend
        if ./mvnw dependency:resolve -q &> /dev/null; then
            print_status "Backend dependencies resolved"
            return 0
        else
            print_error "Backend dependencies not resolved"
            return 1
        fi
    else
        print_error "Backend project not found"
        return 1
    fi
}

# Check frontend dependencies
check_frontend() {
    if [ -f "/workspace/frontend/package.json" ]; then
        print_status "Frontend project found"
        if [ -d "/workspace/frontend/node_modules" ]; then
            print_status "Frontend dependencies installed"
            return 0
        else
            print_error "Frontend dependencies not installed"
            return 1
        fi
    else
        print_error "Frontend project not found"
        return 1
    fi
}

# Check services
check_services() {
    local services=("postgres" "pgadmin")
    local all_healthy=true
    
    for service in "${services[@]}"; do
        if docker-compose ps "$service" | grep -q "Up"; then
            print_status "Service $service is running"
        else
            print_error "Service $service is not running"
            all_healthy=false
        fi
    done
    
    return $all_healthy
}

# Main health check
main() {
    print_header "Indezy Development Environment Health Check"
    
    local checks=(
        "check_devcontainer"
        "check_java"
        "check_maven"
        "check_node"
        "check_angular"
        "check_psql"
        "check_database"
        "check_backend"
        "check_frontend"
        "check_services"
    )
    
    local failed_checks=0
    
    for check in "${checks[@]}"; do
        if ! $check; then
            ((failed_checks++))
        fi
    done
    
    print_header "Health Check Summary"
    
    if [ $failed_checks -eq 0 ]; then
        print_status "All checks passed! Environment is ready for development."
        print_info "You can start developing with:"
        echo "  • indezy-backend  - Start backend server"
        echo "  • indezy-frontend - Start frontend server"
        echo "  • indezy-db       - Connect to database"
        return 0
    else
        print_error "$failed_checks check(s) failed. Please review the issues above."
        return 1
    fi
}

# Run main function
main "$@"

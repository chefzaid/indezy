# Indezy - Development Commands

This is a [mask](https://github.com/jacobdeichert/mask) file that provides easy-to-use commands for developing the Indezy application.

## Installation

First, install mask:

```bash
# Using cargo (Rust)
cargo install mask

# Using npm
npm install -g @jacobdeichert/mask

# Using homebrew (macOS/Linux)
brew install mask
```

## Usage

Run `mask` or `mask --help` to see all available commands.

## Available Commands

### Dependencies
- `mask install` - Install all dependencies
- `mask install-backend` - Install backend dependencies only
- `mask install-frontend` - Install frontend dependencies only

### Build
- `mask build` - Build all components
- `mask build-backend` - Build backend only
- `mask build-frontend` - Build frontend only

### Testing
- `mask test` - Run all tests
- `mask test-backend` - Run backend tests only
- `mask test-frontend` - Run frontend tests only
- `mask test-coverage` - Run tests with coverage reports

### Local Development (H2 database, no Docker)
- `mask run-local` - Run both frontend and backend locally with H2
- `mask run-frontend-local` - Run frontend only in local mode
- `mask run-backend-local` - Run backend only in local mode

### Development Environment (PostgreSQL, needs Docker/Devcontainer)
- `mask run-dev` - Run both frontend and backend in dev mode with PostgreSQL
- `mask run-frontend-dev` - Run frontend only in dev mode
- `mask run-backend-dev` - Run backend only in dev mode

### Information
- `mask info` - Show environment information
- `mask status` - Show service status
- `mask stop` - Stop all running services
- `mask logs` - View backend logs

## install-backend

> Install backend dependencies

```bash
echo "📦 Installing backend dependencies..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd dependency:go-offline"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw dependency:go-offline
fi
cd ..
echo "✅ Backend dependencies installed!"
```

## install-frontend

> Install frontend dependencies

```bash
echo "📦 Installing frontend dependencies..."
cd frontend
# Clean node_modules if it exists to avoid permission issues on Windows
if [ -d "node_modules" ]; then
    echo "🧹 Cleaning existing node_modules..."
    if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]] || [[ -n "$WINDIR" ]] || command -v cmd.exe >/dev/null 2>&1; then
        # On Windows, use rmdir which handles locked files better
        cmd.exe /c "rmdir /s /q node_modules" 2>/dev/null || rm -rf node_modules 2>/dev/null || true
    else
        rm -rf node_modules
    fi
fi
npm install
cd ..
echo "✅ Frontend dependencies installed!"
```

## install

> Install all dependencies

```bash
echo "📦 Installing all dependencies..."

# Install backend dependencies
echo "📦 Installing backend dependencies..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd dependency:go-offline"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw dependency:go-offline
fi
cd ..

# Install frontend dependencies
echo "📦 Installing frontend dependencies..."
cd frontend
# Clean node_modules if it exists to avoid permission issues on Windows
if [ -d "node_modules" ]; then
    echo "🧹 Cleaning existing node_modules..."
    if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]] || [[ -n "$WINDIR" ]] || command -v cmd.exe >/dev/null 2>&1; then
        # On Windows, use rmdir which handles locked files better
        cmd.exe /c "rmdir /s /q node_modules" 2>/dev/null || rm -rf node_modules 2>/dev/null || true
    else
        rm -rf node_modules
    fi
fi
npm install
cd ..

echo "✅ All dependencies installed!"
```

## build-backend

> Build backend application

```bash
echo "🔨 Building backend..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd clean package -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean package -DskipTests
fi
cd ..
echo "✅ Backend built successfully!"
```

## build-frontend

> Build frontend application

```bash
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..
echo "✅ Frontend built successfully!"
```

## build

> Build all components

```bash
echo "🔨 Building all components..."

# Build backend
echo "🔨 Building backend..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd clean package -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean package -DskipTests
fi
cd ..

# Build frontend
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..

echo "✅ All components built successfully!"
```

## test-backend

> Run backend tests

```bash
echo "🧪 Running backend tests..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd test"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw test
fi
cd ..
echo "✅ Backend tests completed!"
```

## test-frontend

> Run frontend tests

```bash
echo "🧪 Running frontend tests..."
cd frontend
npm test -- --watch=false --browsers=ChromeHeadless
cd ..
echo "✅ Frontend tests completed!"
```

## test

> Run all tests

```bash
echo "🧪 Running all tests..."

# Run backend tests
echo "🧪 Running backend tests..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd test"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw test
fi
cd ..

# Run frontend tests
echo "🧪 Running frontend tests..."
cd frontend
npm test -- --watch=false --browsers=ChromeHeadless
cd ..

echo "✅ All tests completed!"
```

## test-coverage

> Run tests with coverage reports

```bash
echo "🧪 Running tests with coverage..."
echo "📊 Backend coverage..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd test jacoco:report"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw test jacoco:report
fi
cd ..
echo "📊 Frontend coverage..."
cd frontend
npm run test:coverage
cd ..
echo "✅ Coverage reports generated!"
echo "📊 Backend coverage: backend/target/site/jacoco/index.html"
echo "📊 Frontend coverage: frontend/coverage/index.html"
```

## run-backend-local

> Run backend locally with H2 database

```bash
echo "🚀 Starting backend locally with H2..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
fi
cd ..
```

## run-frontend-local

> Run frontend locally

```bash
echo "🚀 Starting frontend locally..."
cd frontend
npm start
cd ..
```

## run-local

> Run both frontend and backend locally with H2

```bash
echo "🚀 Starting local development environment..."
echo "🔨 Building applications first..."

# Build backend
echo "🔨 Building backend..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd clean package -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean package -DskipTests
fi
cd ..

# Build frontend
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..

echo "🚀 Starting backend with H2..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    # Windows: Start backend in a new window
    echo "Starting backend in new window..."
    cmd.exe /c "start mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local"
    echo "Backend started in separate window"
else
    # Unix: Start in background
    chmod +x mvnw 2>/dev/null || true
    nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local > ../backend.log 2>&1 &
    BACKEND_PID=$!
    echo "Backend started with PID: $BACKEND_PID"
fi
cd ..
echo "⏳ Waiting for backend to start..."
echo "💡 You can monitor backend logs with: mask logs"
echo "💡 You can stop all services with: mask stop"
sleep 10
echo "🚀 Starting frontend..."
cd frontend
npm start
cd ..
```
## run-backend-dev

> Run backend in dev mode with PostgreSQL

```bash
echo "🚀 Starting backend in dev mode..."
echo "🐳 Starting PostgreSQL..."
docker-compose up -d postgres
echo "⏳ Waiting for database..."
sleep 5
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=devcontainer"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=devcontainer
fi
cd ..
```

## run-frontend-dev

> Run frontend in dev mode

```bash
echo "🚀 Starting frontend in dev mode..."
cd frontend
npm start
cd ..
```

## run-dev

> Run both frontend and backend in dev mode with PostgreSQL

```bash
echo "🚀 Starting development environment..."
echo "🔨 Building applications first..."

# Build backend
echo "🔨 Building backend..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    cmd.exe /c "mvnw.cmd clean package -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean package -DskipTests
fi
cd ..

# Build frontend
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..

echo "🐳 Starting PostgreSQL..."
docker-compose up -d postgres
echo "⏳ Waiting for database..."
sleep 10
echo "🚀 Starting backend..."
cd backend
if [[ -f "mvnw.cmd" ]]; then
    # Windows: Start backend in a new window
    echo "Starting backend in new window..."
    cmd.exe /c "start mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=devcontainer"
    echo "Backend started in separate window"
else
    # Unix: Start in background
    chmod +x mvnw 2>/dev/null || true
    nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=devcontainer > ../backend.log 2>&1 &
    BACKEND_PID=$!
    echo "Backend started with PID: $BACKEND_PID"
fi
cd ..
echo "⏳ Waiting for backend to start..."
sleep 15
echo "🚀 Starting frontend..."
cd frontend
npm start
cd ..
```
## info

> Show environment information

```bash
echo "📋 Indezy Development Environment Information"
echo "============================================="
echo ""
echo "🔧 System Information:"
echo "  OS: $(uname -s 2>/dev/null || echo 'Windows')"
echo "  Architecture: $(uname -m 2>/dev/null || echo 'Unknown')"
echo ""
echo "☕ Java:"
if command -v java &> /dev/null; then
    java -version 2>&1 | head -n 1
else
    echo "  Java not found"
fi
echo ""
echo "📦 Node.js:"
if command -v node &> /dev/null; then
    echo "  Version: $(node --version)"
    echo "  NPM: $(npm --version)"
else
    echo "  Node.js not found"
fi
echo ""
echo "🐳 Docker:"
if command -v docker &> /dev/null; then
    echo "  Version: $(docker --version)"
    echo "  Compose: $(docker-compose --version 2>/dev/null || echo 'Not available')"
else
    echo "  Docker not found"
fi
echo ""
echo "🌐 Default URLs:"
echo "  Frontend: http://localhost:4200"
echo "  Backend: http://localhost:8080/api"
echo "  Swagger: http://localhost:8080/api/swagger-ui.html"
echo "  Database: localhost:5432"
echo "  pgAdmin: http://localhost:5050"
```

## status

> Show service status

```bash
echo "📋 Indezy Service Status"
echo "======================="
echo ""
echo "🔍 Checking local services..."
echo ""
echo "Frontend (port 4200):"
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]] || [[ -n "$WINDIR" ]] || command -v cmd.exe >/dev/null 2>&1; then
    if netstat -an | findstr :4200 >/dev/null 2>&1; then
        echo "  ✅ Running"
    else
        echo "  ❌ Not running"
    fi
else
    if lsof -Pi :4200 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ Running"
    else
        echo "  ❌ Not running"
    fi
fi
echo ""
echo "Backend (port 8080):"
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]] || [[ -n "$WINDIR" ]] || command -v cmd.exe >/dev/null 2>&1; then
    if netstat -an | findstr :8080 >/dev/null 2>&1; then
        echo "  ✅ Running"
        if curl -s http://localhost:8080/api/actuator/health >/dev/null 2>&1; then
            echo "  ✅ Health check passed"
        else
            echo "  ⚠️  Health check failed"
        fi
    else
        echo "  ❌ Not running"
    fi
else
    if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ Running"
        if curl -s http://localhost:8080/api/actuator/health >/dev/null 2>&1; then
            echo "  ✅ Health check passed"
        else
            echo "  ⚠️  Health check failed"
        fi
    else
        echo "  ❌ Not running"
    fi
fi
echo ""
echo "Database (port 5432):"
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]] || [[ -n "$WINDIR" ]] || command -v cmd.exe >/dev/null 2>&1; then
    if netstat -an | findstr :5432 >/dev/null 2>&1; then
        echo "  ✅ Running"
    else
        echo "  ❌ Not running"
    fi
else
    if lsof -Pi :5432 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ Running"
    else
        echo "  ❌ Not running"
    fi
fi
echo ""
echo "🐳 Docker services:"
if command -v docker-compose &> /dev/null; then
    docker-compose ps 2>/dev/null || echo "  No Docker services running"
else
    echo "  Docker Compose not available"
fi
```

## stop

> Stop all running services

```bash
echo "🛑 Stopping all services..."

# Stop backend processes
echo "Stopping backend processes..."
if [[ -f "backend.log" ]]; then
    # Try to find and kill backend processes
    pkill -f "spring-boot:run" 2>/dev/null || true
    pkill -f "mvnw" 2>/dev/null || true
    echo "Backend processes stopped"
else
    echo "No backend log file found"
fi

# Stop frontend processes (if running)
echo "Stopping frontend processes..."
pkill -f "ng serve" 2>/dev/null || true
pkill -f "npm start" 2>/dev/null || true

# Stop Docker services
echo "Stopping Docker services..."
if command -v docker-compose &> /dev/null; then
    docker-compose down 2>/dev/null || true
fi

echo "✅ All services stopped"
```

## logs

> View backend logs

```bash
if [[ -f "backend.log" ]]; then
    echo "📋 Backend logs (last 50 lines):"
    echo "=================================="
    tail -f backend.log
else
    echo "❌ Backend log file not found"
    echo "Make sure you've started the backend with 'mask run-local' or 'mask run-dev'"
fi
```

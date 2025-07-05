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

## quick-start

> Complete setup for new developers

```bash
echo "🚀 Setting up Indezy for development..."
echo "📦 Installing all dependencies..."
echo "📦 Installing backend dependencies..."
cd backend
if command -v cmd.exe >/dev/null 2>&1; then
    cmd.exe /c "mvnw.cmd clean install -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean install -DskipTests
fi
cd ..
echo "📦 Installing frontend dependencies..."
cd frontend
npm install
cd ..
echo "🐳 Starting database..."
docker-compose up -d postgres
echo "⏳ Waiting for database to be ready..."
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    timeout /t 5 /nobreak > nul
else
    sleep 5
fi
echo "✅ Setup complete! Run 'mask run' to start the application"
```

## demo

> Start demo environment with sample data

```bash
echo "🎬 Starting demo environment..."
echo "🐳 Starting Docker services..."
docker-compose up -d
echo "✅ Demo ready!"
echo "Frontend: http://localhost:4200"
echo "Backend: http://localhost:8080/api"
echo "Swagger: http://localhost:8080/api/swagger-ui.html"
echo "Database: localhost:5432"
echo "Login with any email/password combination"
```

## install

> Install all dependencies (frontend + backend)

```bash
echo "📦 Installing all dependencies..."
echo "📦 Installing backend dependencies..."
cd backend
if command -v cmd.exe >/dev/null 2>&1; then
    cmd.exe /c "mvnw.cmd clean install -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean install -DskipTests
fi
cd ..
echo "📦 Installing frontend dependencies..."
cd frontend
npm install
cd ..
echo "✅ All dependencies installed!"
```

## install-backend

> Install backend dependencies

```bash
echo "📦 Installing backend dependencies..."
cd backend
if command -v cmd.exe >/dev/null 2>&1; then
    cmd.exe /c "mvnw.cmd clean install -DskipTests"
else
    chmod +x mvnw 2>/dev/null || true
    ./mvnw clean install -DskipTests
fi
cd ..
```

## install-frontend

> Install frontend dependencies

```bash
echo "📦 Installing frontend dependencies..."
cd frontend
npm install
cd ..
```

## build

> Build both frontend and backend

```bash
echo "🔨 Building application..."
echo "🔨 Building backend..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean package -DskipTests
else
    ./mvnw clean package -DskipTests
fi
cd ..
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..
echo "✅ Build completed!"
```

## build-backend

> Build backend only

```bash
echo "🔨 Building backend..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean package -DskipTests
else
    ./mvnw clean package -DskipTests
fi
cd ..
```

## build-frontend

> Build frontend only

```bash
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..
```

## build-prod

> Build for production

```bash
echo "🔨 Building for production..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean package -Pprod
else
    ./mvnw clean package -Pprod
fi
cd ..
cd frontend
npm run build --configuration=production
cd ..
```

## test

> Run all tests (frontend + backend)

```bash
echo "🧪 Running all tests..."
echo "🧪 Running backend tests..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean test jacoco:report
else
    ./mvnw clean test jacoco:report
fi
cd ..
echo "🧪 Running frontend tests..."
cd frontend
npm test -- --watch=false --code-coverage
cd ..
echo "✅ All tests completed!"
```

## test-backend

> Run backend tests with coverage

```bash
echo "🧪 Running backend tests..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean test jacoco:report
else
    ./mvnw clean test jacoco:report
fi
cd ..
```

## test-frontend

> Run frontend tests with coverage

```bash
echo "🧪 Running frontend tests..."
cd frontend
npm test -- --watch=false --code-coverage
cd ..
```

## test-backend-watch

> Run backend tests in watch mode

```bash
echo "🧪 Running backend tests in watch mode..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd test -Dspring-boot.run.fork=false
else
    ./mvnw test -Dspring-boot.run.fork=false
fi
cd ..
```

## test-frontend-watch

> Run frontend tests in watch mode

```bash
echo "🧪 Running frontend tests in watch mode..."
cd frontend
npm test
cd ..
```

## run

> Start both frontend and backend in development mode

```bash
echo "🚀 Starting full application..."
echo "Starting backend in background..."
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    start /B cmd /c "cd backend && ./mvnw.cmd spring-boot:run"
    echo "⏳ Waiting for backend to start..."
    timeout /t 10 /nobreak > nul
    echo "Starting frontend..."
    cd frontend && npm start
else
    cd backend && ./mvnw spring-boot:run &
    BACKEND_PID=$!
    echo "Backend PID: $BACKEND_PID"
    echo "⏳ Waiting for backend to start..."
    sleep 10
    echo "Starting frontend..."
    cd ../frontend && npm start
fi
```

## run-backend

> Start backend development server

```bash
echo "🚀 Starting backend server..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd spring-boot:run
else
    ./mvnw spring-boot:run
fi
cd ..
```

## run-frontend

> Start frontend development server

```bash
echo "🚀 Starting frontend server..."
cd frontend
npm start
cd ..
```

## docker-up

> Start all services with Docker Compose

```bash
echo "🐳 Starting Docker services..."
docker-compose up -d
echo "✅ Services started!"
echo "Frontend: http://localhost:4200"
echo "Backend: http://localhost:8080/api"
echo "Swagger: http://localhost:8080/api/swagger-ui.html"
echo "Database: localhost:5432"
```

## docker-down

> Stop all Docker services

```bash
echo "🐳 Stopping Docker services..."
docker-compose down
```

## docker-logs

> Show Docker logs

```bash
docker-compose logs -f
```

## docker-clean

> Clean Docker containers, images, and volumes

```bash
echo "🧹 Cleaning Docker resources..."
docker-compose down -v --remove-orphans
docker system prune -f
```

## docker-db

> Start only the database

```bash
echo "🐳 Starting database..."
docker-compose up -d postgres
```

## dev

> Start development environment (database + backend + frontend)

```bash
echo "🚀 Starting development environment..."
echo "🐳 Starting database..."
docker-compose up -d postgres
echo "⏳ Waiting for database to start..."
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    timeout /t 5 /nobreak > nul
else
    sleep 5
fi
echo "🚀 Starting full application..."
echo "Starting backend in background..."
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    start /B cmd /c "cd backend && ./mvnw.cmd spring-boot:run"
    echo "⏳ Waiting for backend to start..."
    timeout /t 10 /nobreak > nul
    echo "Starting frontend..."
    cd frontend && npm start
else
    cd backend && ./mvnw spring-boot:run &
    BACKEND_PID=$!
    echo "Backend PID: $BACKEND_PID"
    echo "⏳ Waiting for backend to start..."
    sleep 10
    echo "Starting frontend..."
    cd ../frontend && npm start
fi
```

## dev-reset

> Reset development environment

```bash
echo "🔄 Resetting development environment..."
echo "🧹 Cleaning Docker resources..."
docker-compose down -v --remove-orphans
docker system prune -f
echo "🧹 Cleaning build artifacts..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean
else
    ./mvnw clean
fi
cd ..
cd frontend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    if exist "dist" rmdir /s /q dist
    if exist "node_modules\.cache" rmdir /s /q "node_modules\.cache"
else
    rm -rf dist/ node_modules/.cache/
fi
cd ..
echo "📦 Installing all dependencies..."
echo "📦 Installing backend dependencies..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean install -DskipTests
else
    ./mvnw clean install -DskipTests
fi
cd ..
echo "📦 Installing frontend dependencies..."
cd frontend
npm install
cd ..
echo "🐳 Starting database..."
docker-compose up -d postgres
echo "⏳ Waiting for database to start..."
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    timeout /t 5 /nobreak > nul
else
    sleep 5
fi
echo "✅ Development environment reset complete!"
```

## clean

> Clean build artifacts

```bash
echo "🧹 Cleaning build artifacts..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean
else
    ./mvnw clean
fi
cd ..
cd frontend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    if exist "dist" rmdir /s /q dist
    if exist "node_modules\.cache" rmdir /s /q "node_modules\.cache"
else
    rm -rf dist/ node_modules/.cache/
fi
cd ..
echo "✅ Clean completed!"
```

## lint

> Run linting for frontend

```bash
echo "🔍 Running linter..."
cd frontend
npm run lint
cd ..
```

## format

> Format code (frontend)

```bash
echo "✨ Formatting code..."
cd frontend
npm run lint -- --fix
cd ..
```

## coverage

> Generate and open coverage reports

```bash
echo "📊 Generating coverage reports..."
echo "🧪 Running backend tests..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean test jacoco:report
else
    ./mvnw clean test jacoco:report
fi
cd ..
echo "🧪 Running frontend tests..."
cd frontend
npm test -- --watch=false --code-coverage
cd ..
echo "Backend coverage: backend/target/site/jacoco/index.html"
echo "Frontend coverage: frontend/coverage/index.html"
```

## swagger

> Open Swagger documentation

```bash
echo "📚 Opening Swagger documentation..."
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    start http://localhost:8080/api/swagger-ui.html
elif [[ "$OSTYPE" == "darwin"* ]]; then
    open http://localhost:8080/api/swagger-ui.html
else
    xdg-open http://localhost:8080/api/swagger-ui.html
fi
```

## status

> Show application status

```bash
echo "📊 Application Status:"
echo ""
echo "Backend:"
if curl -s http://localhost:8080/api/actuator/health 2>/dev/null | grep -q "UP"; then
    echo "  ✅ Backend is running"
else
    echo "  ❌ Backend is not running"
fi
echo ""
echo "Frontend:"
if curl -s http://localhost:4200 >/dev/null 2>&1; then
    echo "  ✅ Frontend is running"
else
    echo "  ❌ Frontend is not running"
fi
echo ""
echo "Database:"
if docker-compose ps postgres | grep -q "Up"; then
    echo "  ✅ Database is running"
else
    echo "  ❌ Database is not running"
fi
```

## info

> Show application information

```bash
echo "📋 Indezy Application Information:"
echo ""
echo "🌐 URLs:"
echo "  Frontend:  http://localhost:4200"
echo "  Backend:   http://localhost:8080/api"
echo "  Swagger:   http://localhost:8080/api/swagger-ui.html"
echo "  Database:  localhost:5432"
echo ""
echo "🔧 Tech Stack:"
echo "  Backend:   Java 21 + Spring Boot 3.5.3"
echo "  Frontend:  Angular 20.0.6 + TypeScript 5.8.3"
echo "  Database:  PostgreSQL"
echo "  Testing:   JUnit 5 + Jasmine/Karma"
echo ""
echo "📁 Project Structure:"
echo "  backend/   - Spring Boot API"
echo "  frontend/  - Angular application"
echo "  database/  - Database initialization scripts"
echo ""
echo "🚀 Quick Commands:"
echo "  mask quick-start  - Setup everything for new developers"
echo "  mask dev         - Start development environment"
echo "  mask test        - Run all tests"
echo "  mask docker-up   - Start with Docker"
```

## ci-test

> Run tests for CI/CD pipeline

```bash
echo "🔄 Running CI tests..."
echo "📦 Installing backend dependencies..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean install -DskipTests
else
    ./mvnw clean install -DskipTests
fi
cd ..
echo "📦 Installing frontend dependencies..."
cd frontend
npm install
cd ..
echo "🧪 Running backend tests..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean test jacoco:report
else
    ./mvnw clean test jacoco:report
fi
cd ..
echo "🧪 Running frontend tests..."
cd frontend
npm test -- --watch=false --code-coverage
cd ..
echo "🔨 Building backend..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean package -DskipTests
else
    ./mvnw clean package -DskipTests
fi
cd ..
echo "🔨 Building frontend..."
cd frontend
npm run build
cd ..
```

## ci-build

> Build for CI/CD pipeline

```bash
echo "🔄 Running CI build..."
echo "🔨 Building for production..."
cd backend
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    ./mvnw.cmd clean package -Pprod
else
    ./mvnw clean package -Pprod
fi
cd ..
cd frontend
npm run build --configuration=production
cd ..
```



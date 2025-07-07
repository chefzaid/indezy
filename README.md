# Indezy - Job Tracking Application for Freelancers

<div align="center">
  <img src="frontend/src/assets/images/indezy-logo.svg" alt="Indezy Logo" height="80">

  **A comprehensive job tracking and project management platform designed specifically for freelancers in the French tech market.**

  [![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
  [![Angular](https://img.shields.io/badge/Angular-20.0.6-red.svg)](https://angular.io/)
  [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-blue.svg)](https://www.postgresql.org/)
  [![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)
</div>

## 🎯 Overview

Indezy is a modern, full-stack web application that helps freelancers manage their job applications, track project opportunities, and maintain organized dashboards. Built with enterprise-grade technologies and designed for the French tech market, it provides a comprehensive solution for freelance project management.

## 🚀 Quick Start

### 🐳 Development Container (Recommended)

The fastest way to get started is using the pre-configured development container:

1. **Open in VS Code**: Open the project in VS Code
2. **Reopen in Container**: Select "Reopen in Container" when prompted
3. **Wait for Setup**: The container will build and configure automatically (~5-10 minutes first time)
4. **Start Developing**: Use the provided commands to start the application

```bash
# Start backend server
indezy-backend

# Start frontend server (in another terminal)
indezy-frontend

# Access the application at http://localhost:4200
```

**What's included in the devcontainer:**
- ✅ Java 21 + Maven pre-configured
- ✅ Node.js 18 + Angular CLI
- ✅ PostgreSQL database with sample data
- ✅ pgAdmin for database management
- ✅ All VS Code extensions and settings
- ✅ No additional configuration needed!

### 📋 Manual Setup Prerequisites
- **Docker & Docker Compose** (for database)
- **Node.js 18+** (for frontend development)
- **Java 21** (for backend development)
- **Maven 3.9+** (for backend development)

## ✨ Key Features

### 📊 Project Management
- **Comprehensive Project Tracking**: Manage project details including role, client, daily rate, tech stack, and work mode
- **Interview Process Management**: Track interview steps with status updates and scheduling
- **Document Management**: Upload and organize project-related documents
- **Personal Rating System**: Rate projects and maintain personal notes

### 👥 Client & Contact Management
- **Client Portfolio**: Manage client companies with detailed information and project history
- **Contact Integration**: Associate multiple contacts with each client in a hierarchical structure
- **Advanced Filtering**: Filter clients by city, domain, and associated contacts
- **Relationship Tracking**: Track middleman relationships and project sources

### 📈 Analytics & Insights
- **Dashboard Analytics**: View project statistics, average daily rates, and performance metrics
- **Revenue Calculations**: Automatic calculation of project revenue based on rates and duration
- **Success Rate Tracking**: Monitor interview success rates and project completion

### 🔐 Security & Authentication
- **JWT-based Authentication**: Secure token-based authentication system
- **OAuth Integration**: Support for Google, GitHub, and Microsoft OAuth providers
- **Role-based Access Control**: Secure access to user-specific data
- **GDPR Compliance**: Built with French market privacy requirements in mind

## 🏗️ Architecture

### Backend (Spring Boot)
- **Framework**: Spring Boot 3.5.3 with Java 21
- **Security**: Spring Security 6.4.2 with JWT authentication
- **Database**: PostgreSQL with JPA/Hibernate
- **API Documentation**: OpenAPI 3 with Swagger UI
- **Testing**: Comprehensive test suite with 60% coverage threshold
- **Build Tool**: Maven with wrapper for consistency

### Frontend (Angular)
- **Framework**: Angular 20.0.6 with TypeScript 5.8.3
- **UI Library**: Angular Material with custom SCSS styling
- **State Management**: RxJS observables and services
- **Testing**: Jasmine and Karma with code coverage
- **Build System**: Angular CLI with modern build pipeline

### Docker & Deployment
- **Development**: VS Code devcontainer with full development environment
- **Production**: Multi-stage Docker builds with security hardening
- **Database**: PostgreSQL with initialization scripts and health checks
- **Web Server**: Nginx for frontend with optimized static file serving
- **Orchestration**: Docker Compose for both development and production

### Database Schema
- **Freelance**: User profiles with employment status and availability
- **Projects**: Comprehensive project tracking with rates, tech stack, and timeline
- **Clients**: Company information with project relationships
- **Contacts**: Individual contacts associated with clients
- **Sources**: Project source tracking (job boards, referrals, etc.)
- **Interview Steps**: Detailed interview process management

## 🛠️ Development Commands (Mask)

**All development tasks can be performed using [Mask](https://github.com/jacobdeichert/mask) commands:**

### Installation
First, install mask:
```bash
# Using cargo (Windows/Rust)
cargo install mask

# Using homebrew (Linux/macOS)
brew install mask
```

### 🚀 Quick Start Commands
```bash
mask                  # Show all available commands

# Dependencies
mask install          # Install all dependencies
mask install-backend  # Install backend dependencies only
mask install-frontend # Install frontend dependencies only

# Build
mask build            # Build all components
mask build-backend    # Build backend only
mask build-frontend   # Build frontend only

# Testing
mask test             # Run all tests
mask test-backend     # Run backend tests only
mask test-frontend    # Run frontend tests only
mask test-coverage    # Run tests with coverage reports

# Local Development (H2 database)
mask run-local        # Run both frontend and backend locally with H2
mask run-frontend-local # Run frontend only in local mode
mask run-backend-local  # Run backend only in local mode

# Development Environment (PostgreSQL)
mask run-dev          # Run both frontend and backend in dev mode with PostgreSQL
mask run-frontend-dev # Run frontend only in dev mode
mask run-backend-dev  # Run backend only in dev mode

# Information
mask info             # Show environment information
mask status           # Show service status
```

### 📦 Installation & Build
```bash
mask install          # Install all dependencies
mask install-backend  # Backend dependencies only
mask install-frontend # Frontend dependencies only
mask build            # Build everything
mask build-prod       # Production build
```

### 🧪 Testing
```bash
mask test             # Run all tests
mask test-backend     # Backend tests with coverage
mask test-frontend    # Frontend tests with coverage
mask coverage         # Generate coverage reports
```

### 🏃 Running Applications
```bash
mask run              # Start both frontend and backend
mask run-backend      # Backend development server
mask run-frontend     # Frontend development server
```

### 🐳 Docker Commands

The application provides a unified Docker setup that serves both development and production needs using Docker Compose profiles.

#### Production Deployment
```bash
mask docker-up        # Start production services (backend-prod, frontend-prod, postgres)
mask docker-down      # Stop production services
mask docker-admin     # Start with pgAdmin included
mask docker-build     # Build production images
mask docker-logs      # View production logs
mask docker-clean     # Clean production resources
```

#### Development Environment
```bash
mask dev-docker-up    # Start development services (postgres, pgAdmin)
mask dev-docker-down  # Stop development services
mask dev-docker-clean # Clean development resources
```

#### VS Code DevContainer
```bash
# Open in VS Code and use "Dev Containers: Reopen in Container"
# Or manually start the devcontainer service:
docker-compose --profile devcontainer up -d
```

**Docker Architecture:**
- **Single docker-compose.yml**: Unified configuration with profiles for different environments
- **Dockerfiles in respective directories**: `backend/Dockerfile` and `frontend/Dockerfile` with multi-stage builds
- **Environment Profiles**:
  - `--profile prod`: Production services (optimized runtime)
  - `--profile dev`: Development services (with development tools)
  - `--profile devcontainer`: VS Code development container
  - `--profile admin`: Includes pgAdmin for database management

**Environment Configuration:**
For production deployment, copy `.env.example` to `.env` and configure:
```bash
cp .env.example .env
# Edit .env with your database credentials and ports
```

### 🔧 Utilities
```bash
mask clean            # Clean build artifacts
mask status           # Check application status
mask swagger          # Open API documentation
mask lint             # Run code linting
mask format           # Format code
mask dev-reset        # Reset entire development environment
```

### 💾 Local H2 Development (No Docker Required)
```bash
mask local            # Start full local environment with H2
mask local-backend    # Start backend with H2 only
mask local-info       # Show H2 development information
```

**Perfect for:**
- ✅ Quick local development without Docker
- ✅ Testing without external dependencies
- ✅ Offline development
- ✅ Lightweight development setup

**H2 Database Access:**
- **H2 Console**: http://localhost:8080/api/h2-console
- **JDBC URL**: `jdbc:h2:mem:indezy`
- **Username**: `sa` | **Password**: `password`

> **Note**: H2 is an in-memory database - data resets on each restart but includes the same sample data as PostgreSQL.

> **Note**: All commands are cross-platform compatible (Windows, macOS, Linux). See `maskfile.md` for detailed command documentation.

### Manual Development Setup

#### 1. Database Setup
```bash
# Using Mask
mask docker-db

# Or manually
docker-compose up -d postgres
# Database: localhost:5432, indezy/indezy_user/indezy_password
```

#### 2. Backend Development
```bash
# Using Mask
mask install-backend
mask run-backend

# Or manually (Windows)
cd backend && ./mvnw.cmd clean install && ./mvnw.cmd spring-boot:run

# Or manually (Linux/macOS)
cd backend && ./mvnw clean install && ./mvnw spring-boot:run
```

#### 3. Frontend Development
```bash
# Using Mask
mask install-frontend
mask run-frontend

# Or manually
cd frontend && npm install && npm start
```

### 🐳 DevContainer Support (Standalone Development Environment)

The devcontainer provides a complete, standalone development environment with **zero additional configuration required**:

**Features:**
- ✅ **Complete Environment**: Java 21, Node.js 18, Maven, Angular CLI pre-installed
- ✅ **Database Included**: PostgreSQL with sample data and pgAdmin
- ✅ **VS Code Ready**: All extensions and settings pre-configured
- ✅ **Cross-Platform**: Works on Windows, macOS, and Linux
- ✅ **Isolated**: No conflicts with your local environment

**Setup:**
1. **Prerequisites**: Install VS Code and Docker Desktop
2. **Open Project**: Open the project folder in VS Code
3. **Install Extension**: Install "Dev Containers" extension if not already installed
4. **Reopen in Container**: Select "Reopen in Container" when prompted (or `Ctrl+Shift+P` → "Dev Containers: Reopen in Container")
5. **Wait for Setup**: First build takes ~5-10 minutes to download and configure everything
6. **Start Developing**: Use the provided aliases or mask commands

**Quick Commands (available in devcontainer):**
```bash
# Start applications
indezy-backend          # Start Spring Boot backend
indezy-frontend         # Start Angular frontend

# Database management
indezy-db              # Connect to PostgreSQL
.devcontainer/db-utils.sh check    # Check database health

# Development utilities
.devcontainer/health-check.sh      # Check environment health
mask --help            # Show all available mask commands
```

**Access Points:**
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080/api
- **pgAdmin**: http://localhost:5050 (admin@indezy.com / admin)
- **Database**: localhost:5432 (indezy_user / indezy_password)

> **💡 Tip**: The devcontainer is the recommended way to develop Indezy as it provides a consistent environment across all platforms and requires no manual configuration.

## 🧪 Testing

```bash
# Run all tests (frontend + backend)
mask test

# Individual test commands
mask test-backend          # Backend tests with coverage
mask test-frontend         # Frontend tests with coverage
mask test-backend-watch    # Backend tests in watch mode
mask test-frontend-watch   # Frontend tests in watch mode

# Generate and view coverage reports
mask coverage
# Backend: backend/target/site/jacoco/index.html
# Frontend: frontend/coverage/index.html
```

**Test Coverage**: Both frontend and backend maintain 60% code coverage threshold.

## 📚 API Documentation

The backend provides comprehensive API documentation through OpenAPI 3:

- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api/v3/api-docs

### Main API Endpoints
- `/api/auth/*` - Authentication and user management
- `/api/freelances/*` - Freelance profile management
- `/api/projects/*` - Project CRUD operations
- `/api/clients/*` - Client management
- `/api/contacts/*` - Contact management
- `/api/sources/*` - Source tracking

## 🔧 Configuration

### Environment Variables
Create a `.env` file in the root directory:

```env
# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-change-in-production

# OAuth Configuration (optional)
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
MICROSOFT_CLIENT_ID=your-microsoft-client-id
MICROSOFT_CLIENT_SECRET=your-microsoft-client-secret
```

### Application Profiles
- **Development**: `application.yml` - Default configuration with PostgreSQL
- **Testing**: `application-test.properties` - H2 in-memory database
- **Production**: Environment-specific configuration

### Database Configuration
- **Host**: localhost:5432
- **Database**: indezy
- **Username**: indezy_user
- **Password**: indezy_password

## 🚀 Deployment

```bash
# Build for production
mask build-prod

# Or manually (Windows)
cd backend && ./mvnw.cmd clean package -Pprod
cd frontend && npm run build --configuration=production

# Or manually (Linux/macOS)
cd backend && ./mvnw clean package -Pprod
cd frontend && npm run build --configuration=production
```

## 📁 Project Structure

```
indezy/
├── .devcontainer/          # DevContainer configuration
├── backend/                # Spring Boot backend
│   ├── src/main/java/      # Java source code
│   ├── src/main/resources/ # Configuration files
│   └── pom.xml            # Maven dependencies
├── frontend/               # Angular frontend
│   ├── src/app/           # Angular application
│   ├── src/assets/        # Static assets
│   └── package.json       # NPM dependencies
├── database/              # Database initialization scripts
├── maskfile.md            # Development commands (Mask file)
├── docker-compose.yml     # Docker services
└── README.md             # This file
```

## 🔍 Development Workflow

### URLs
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080/api
- **Swagger Documentation**: http://localhost:8080/api/swagger-ui.html
- **Database**: localhost:5432

### Code Quality Tools
- **Backend**: Maven Checkstyle, SpotBugs, JaCoCo (60% coverage)
- **Frontend**: ESLint, Prettier, Angular CLI linting (60% coverage)
- **Testing**: JUnit 5 + Mockito, Jasmine + Karma

## 🛠️ Troubleshooting

### Common Issues
1. **Port conflicts**: Ensure ports 4200, 8080, and 5432 are available
2. **Database connection**: Check PostgreSQL is running (`mask status`)
3. **CORS issues**: Verify frontend URL in backend CORS configuration
4. **OAuth setup**: Confirm OAuth client IDs and secrets are configured

### Useful Commands
```bash
mask status           # Check application status
mask docker-logs      # View Docker logs
mask clean           # Clean build artifacts
mask dev-reset       # Reset entire development environment
```

### Logs
- **Backend**: Spring Boot console output
- **Frontend**: Browser developer console
- **Database**: `docker-compose logs postgres`

## 🙏 Acknowledgments

- Built with modern enterprise-grade technologies
- Designed specifically for the French freelance market
- Inspired by the need for better freelance project management tools

---

<div align="center">
  <strong>Made with ❤️ for the freelance community</strong>
</div>

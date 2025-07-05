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

### One-Command Setup
```bash
# For new developers - installs everything and sets up the database
make quick-start

# Start the full application
make run
```

### Using Docker (Recommended)
```bash
# Start everything with Docker
make docker-up

# Access the application:
# Frontend: http://localhost:4200
# Backend: http://localhost:8080/api
# Swagger: http://localhost:8080/api/swagger-ui.html
```

### Prerequisites
- **Docker & Docker Compose** (recommended)
- **Node.js 18+** (for local development)
- **Java 21** (for local development)
- **Maven 3.9+** (for local development)

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
# Using cargo (Rust)
cargo install mask

# Using npm
npm install -g @jacobdeichert/mask

# Using homebrew (macOS/Linux)
brew install mask
```

### 🚀 Quick Start Commands
```bash
mask                  # Show all available commands
mask quick-start      # Complete setup for new developers
mask dev              # Start development environment
mask demo             # Start demo with sample data
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
```bash
mask docker-up        # Start all services with Docker
mask docker-down      # Stop Docker services
mask docker-db        # Start database only
mask docker-logs      # View Docker logs
mask docker-clean     # Clean Docker resources
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

### DevContainer Support
1. Open project in VS Code
2. Install "Dev Containers" extension
3. Press `Ctrl+Shift+P` → "Dev Containers: Reopen in Container"
4. Wait for container setup to complete

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

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Run tests: `mask test`
4. Commit your changes (`git commit -m 'Add amazing feature'`)
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

### Development Guidelines
- Use `mask` commands for consistency across platforms
- Maintain 60% test coverage for both frontend and backend
- Follow existing code style and conventions
- Update documentation for API changes

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Zaid Chefchaouni** - [byteworks.dev](https://byteworks.dev)
- GitHub: [@chefzaid](https://github.com/chefzaid)
- Email: c.zaid@outlook.com

## 🙏 Acknowledgments

- Built with modern enterprise-grade technologies
- Designed specifically for the French freelance market
- Inspired by the need for better freelance project management tools

---

<div align="center">
  <strong>Made with ❤️ for the freelance community</strong>
</div>

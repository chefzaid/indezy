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

## 🚀 Getting Started

### Prerequisites
- **Docker & Docker Compose** (recommended)
- **Node.js 18+** (for local development)
- **Java 21** (for local development)
- **Maven 3.9+** (for local development)

### Quick Start with Docker
```bash
# Clone the repository
git clone https://github.com/chefzaid/indezy.git
cd indezy

# Start all services
docker-compose up -d

# Access the application
# Frontend: http://localhost:4200
# Backend API: http://localhost:8080/api
# Swagger UI: http://localhost:8080/api/swagger-ui.html
```

### Development Setup

#### 1. Database Setup
```bash
# Start PostgreSQL
docker-compose up -d postgres

# Database will be available at:
# Host: localhost:5432
# Database: indezy
# Username: indezy_user
# Password: indezy_password
```

#### 2. Backend Development
```bash
cd backend

# Install dependencies and run tests
./mvnw clean install

# Start the Spring Boot application
./mvnw spring-boot:run

# API will be available at http://localhost:8080/api
```

#### 3. Frontend Development
```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm start

# Application will be available at http://localhost:4200
```

## 🧪 Testing

### Backend Testing
```bash
cd backend

# Run all tests
./mvnw test

# Run tests with coverage report
./mvnw clean test jacoco:report

# Coverage reports available at: target/site/jacoco/index.html
```

### Frontend Testing
```bash
cd frontend

# Run unit tests
npm test

# Run tests with coverage
npm run test -- --code-coverage

# Coverage reports available at: coverage/index.html
```

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
```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=indezy
DB_USERNAME=indezy_user
DB_PASSWORD=indezy_password

# JWT Configuration
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# OAuth Configuration (optional)
GOOGLE_CLIENT_ID=your-google-client-id
GITHUB_CLIENT_ID=your-github-client-id
MICROSOFT_CLIENT_ID=your-microsoft-client-id
```

### Application Profiles
- **Development**: `application.yml` - Default development configuration
- **Testing**: `application-test.properties` - H2 in-memory database for tests
- **Production**: Environment-specific configuration

## 🛠️ Development Tools

### DevContainer Support
The project includes a complete DevContainer configuration for consistent development environments:

```bash
# Using VS Code
1. Install "Dev Containers" extension
2. Open project in VS Code
3. Press Ctrl+Shift+P → "Dev Containers: Reopen in Container"
4. Wait for container setup to complete
```

### Code Quality
- **Backend**: Maven Checkstyle, SpotBugs, and JaCoCo for code quality
- **Frontend**: ESLint, Prettier, and Angular CLI linting
- **Testing**: 60% code coverage threshold for both frontend and backend

## 🚀 Deployment

### Production Build
```bash
# Backend
cd backend
./mvnw clean package -Pprod

# Frontend
cd frontend
npm run build --prod
```

### Docker Deployment
```bash
# Build and deploy all services
docker-compose -f docker-compose.prod.yml up -d
```

## 🙏 Acknowledgments

- Built with modern enterprise-grade technologies
- Designed specifically for the French freelance market
- Inspired by the need for better freelance project management tools

---

<div align="center">
  <strong>Made with ❤️ for the freelance community</strong>
</div>

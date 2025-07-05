# Indezy - Setup Guide

## Prerequisites

- Docker and Docker Compose
- Node.js 18+ (for local development)
- Java 21 (for local development)
- Maven 3.9+ (for local development)

## Quick Start with DevContainer

1. Open the project in VS Code
2. Install the "Dev Containers" extension
3. Press `Ctrl+Shift+P` and select "Dev Containers: Reopen in Container"
4. Wait for the container to build and setup to complete

## Manual Setup

### 1. Database Setup

Start the PostgreSQL database:

```bash
docker-compose up -d postgres
```

The database will be available at:
- Host: localhost
- Port: 5432
- Database: indezy
- Username: indezy_user
- Password: indezy_password

### 2. Backend Setup

Navigate to the backend directory and start the Spring Boot application:

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The backend API will be available at: http://localhost:8080/api

### 3. Frontend Setup

Navigate to the frontend directory and start the Angular application:

```bash
cd frontend
npm install
npm start
```

The frontend will be available at: http://localhost:4200

## Development Workflow

### Backend Development

- API endpoints are available at `http://localhost:8080/api`
- Swagger documentation (when implemented): `http://localhost:8080/api/swagger-ui.html`
- Database admin interface: `http://localhost:5050` (pgAdmin)

### Frontend Development

- Angular dev server with hot reload: `http://localhost:4200`
- Build for production: `npm run build`
- Run tests: `npm test`

## Environment Variables

Create a `.env` file in the root directory with the following variables:

```env
# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-change-in-production

# OAuth Configuration
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
MICROSOFT_CLIENT_ID=your-microsoft-client-id
MICROSOFT_CLIENT_SECRET=your-microsoft-client-secret
```

## Project Structure

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
├── database/              # Database scripts
├── docs/                  # Documentation
└── docker-compose.yml     # Docker services
```

## Next Steps

1. Configure OAuth providers (Google, GitHub, Microsoft)
2. Set up proper JWT secrets for production
3. Configure CORS settings for your domain
4. Set up CI/CD pipeline
5. Configure production database

## Troubleshooting

### Common Issues

1. **Port conflicts**: Make sure ports 4200, 8080, and 5432 are available
2. **Database connection**: Ensure PostgreSQL is running and accessible
3. **CORS issues**: Check that the frontend URL is in the CORS allowed origins
4. **OAuth setup**: Verify OAuth client IDs and secrets are correctly configured

### Logs

- Backend logs: Check the Spring Boot console output
- Frontend logs: Check the browser developer console
- Database logs: `docker-compose logs postgres`

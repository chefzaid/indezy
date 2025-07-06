# Indezy Development Container

This devcontainer provides a complete, standalone development environment for the Indezy application with all dependencies pre-configured.

## 🚀 Features

- **Java 21** with Maven pre-configured
- **Node.js 18** with Angular CLI
- **PostgreSQL 15** database with sample data
- **pgAdmin 4** for database management
- **VS Code extensions** for Java, Angular, and database development
- **Pre-configured environment** with all dependencies installed

## 📋 What's Included

### Services
- **Development Container**: Main workspace with Java, Node.js, and development tools
- **PostgreSQL Database**: Fully configured with the Indezy schema
- **pgAdmin**: Web-based database administration tool

### Pre-installed Tools
- Java 21 (OpenJDK)
- Maven 3.9.6
- Node.js 18
- Angular CLI (latest)
- PostgreSQL client
- Git
- Essential development utilities

### VS Code Extensions
- Java Extension Pack
- Angular Language Service
- TypeScript support
- Tailwind CSS IntelliSense
- PostgreSQL extension
- Docker support
- GitLab Workflow

## 🏃‍♂️ Quick Start

1. **Open in VS Code**: Open the project in VS Code and select "Reopen in Container" when prompted
2. **Wait for setup**: The container will build and configure automatically (first time takes ~5-10 minutes)
3. **Start development**: Use the provided aliases or mask commands

### Quick Commands

```bash
# Start backend server
indezy-backend

# Start frontend server  
indezy-frontend

# Connect to database
indezy-db

# Run tests
indezy-test-backend
indezy-test-frontend

# Build everything
indezy-build
```

## 🌐 Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:4200 | - |
| Backend API | http://localhost:8080/api | - |
| pgAdmin | http://localhost:5050 | admin@indezy.com / admin |
| PostgreSQL | localhost:5432 | indezy_user / indezy_password |

## 🗄️ Database Configuration

The PostgreSQL database is automatically configured with:
- **Database**: `indezy`
- **Username**: `indezy_user`
- **Password**: `indezy_password`
- **Host**: `postgres` (within container) or `localhost` (from host)
- **Port**: `5432`

### Database Features
- Automatic schema creation via JPA/Hibernate
- Sample data initialization
- pgAdmin pre-configured with connection
- Persistent data storage

## 🔧 Development Workflow

### Backend Development
```bash
cd backend
./mvnw spring-boot:run
# or use alias: indezy-backend
```

### Frontend Development
```bash
cd frontend
npm start
# or use alias: indezy-frontend
```

### Running Tests
```bash
# Backend tests
cd backend && ./mvnw test

# Frontend tests
cd frontend && npm test

# Or use aliases
indezy-test-backend
indezy-test-frontend
```

## 📁 Project Structure

```
/workspace/
├── backend/           # Spring Boot application
├── frontend/          # Angular application
├── database/          # Database initialization scripts
├── .devcontainer/     # Container configuration
└── docs/             # Documentation
```

## 🐛 Troubleshooting

### Container Issues
- **Slow startup**: First build takes time to download images and dependencies
- **Port conflicts**: Ensure ports 4200, 8080, 5432, 5050 are available
- **Database connection**: Wait for PostgreSQL to fully start before running applications

### Database Issues
```bash
# Check database status
docker-compose ps

# View database logs
docker-compose logs postgres

# Reset database
docker-compose down -v
docker-compose up -d postgres
```

### Application Issues
```bash
# Check service status
indezy-status

# View logs
indezy-logs

# Restart services
docker-compose restart
```

## 🔄 Updating the Container

To update the development environment:

1. Rebuild the container:
   ```bash
   # From VS Code Command Palette
   Dev Containers: Rebuild Container
   ```

2. Or manually:
   ```bash
   docker-compose down
   docker-compose build --no-cache
   docker-compose up -d
   ```

## 📝 Environment Variables

The container sets up these environment variables automatically:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/indezy`
- `SPRING_DATASOURCE_USERNAME=indezy_user`
- `SPRING_DATASOURCE_PASSWORD=indezy_password`
- `NODE_ENV=development`

## 🔒 Security Notes

- Database credentials are for development only
- Change passwords for production environments
- OAuth credentials need to be configured separately
- JWT secret is set to a development-only value

## 📚 Additional Resources

- [VS Code Dev Containers Documentation](https://code.visualstudio.com/docs/remote/containers)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Angular Documentation](https://angular.io/docs)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

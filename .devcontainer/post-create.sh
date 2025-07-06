#!/bin/bash

set -e

echo "🚀 Starting Indezy development environment setup..."

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
until pg_isready -h postgres -p 5432 -U indezy_user -d indezy; do
  echo "PostgreSQL is unavailable - sleeping"
  sleep 2
done
echo "✅ PostgreSQL is ready!"

# Test database connection
echo "🔍 Testing database connection..."
PGPASSWORD=indezy_password psql -h postgres -U indezy_user -d indezy -c "SELECT version();" || {
  echo "❌ Database connection failed"
  exit 1
}
echo "✅ Database connection successful!"

# Install frontend dependencies if package.json exists
if [ -f "frontend/package.json" ]; then
  echo "📦 Installing frontend dependencies..."
  cd frontend
  npm ci --prefer-offline --no-audit
  cd ..
  echo "✅ Frontend dependencies installed!"
fi

# Install backend dependencies and compile
if [ -f "backend/pom.xml" ]; then
  echo "📦 Installing backend dependencies..."
  cd backend
  ./mvnw dependency:go-offline -q
  ./mvnw compile -q
  cd ..
  echo "✅ Backend dependencies installed and compiled!"
fi

# Set up Git hooks directory
mkdir -p .git/hooks

# Create useful aliases
echo "🔧 Setting up development aliases..."
cat >> ~/.bashrc << 'EOF'

# Indezy Development Aliases
alias indezy-backend='cd /workspace/backend && ./mvnw spring-boot:run'
alias indezy-frontend='cd /workspace/frontend && npm start'
alias indezy-test-backend='cd /workspace/backend && ./mvnw test'
alias indezy-test-frontend='cd /workspace/frontend && npm test'
alias indezy-build='cd /workspace && mask build-all'
alias indezy-db='PGPASSWORD=indezy_password psql -h postgres -U indezy_user -d indezy'
alias indezy-logs='docker-compose logs -f'
alias indezy-status='docker-compose ps'

# Navigation aliases
alias backend='cd /workspace/backend'
alias frontend='cd /workspace/frontend'
alias workspace='cd /workspace'
EOF

echo "✅ Development environment setup complete!"
echo ""
echo "🎉 Welcome to Indezy Development Environment!"
echo ""
echo "📋 Available services:"
echo "  🌐 Frontend (Angular): http://localhost:4200"
echo "  🔧 Backend (Spring Boot): http://localhost:8080"
echo "  🗄️  Database (PostgreSQL): localhost:5432"
echo "  🔍 pgAdmin: http://localhost:5050 (admin@indezy.com / admin)"
echo ""
echo "🚀 Quick start commands:"
echo "  indezy-backend    - Start the backend server"
echo "  indezy-frontend   - Start the frontend server"
echo "  indezy-db         - Connect to the database"
echo "  mask --help       - Show available mask commands"
echo ""
echo "📚 Database credentials:"
echo "  Host: postgres (or localhost from host)"
echo "  Database: indezy"
echo "  Username: indezy_user"
echo "  Password: indezy_password"
echo ""

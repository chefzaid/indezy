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

# Install indezy-web dependencies if package.json exists
if [ -f "indezy-web/package.json" ]; then
  echo "📦 Installing indezy-web dependencies..."
  cd indezy-web
  npm ci --prefer-offline --no-audit
  cd ..
  echo "✅ Frontend dependencies installed!"
fi

# Install indezy-server dependencies and compile
if [ -f "indezy-server/pom.xml" ]; then
  echo "📦 Installing indezy-server dependencies..."
  cd indezy-server
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
alias indezy-server='cd /workspace/indezy-server && ./mvnw spring-boot:run'
alias indezy-web='cd /workspace/indezy-web && npm start'
alias indezy-test-indezy-server='cd /workspace/indezy-server && ./mvnw test'
alias indezy-test-indezy-web='cd /workspace/indezy-web && npm test'
alias indezy-build='cd /workspace && mask build-all'
alias indezy-db='PGPASSWORD=indezy_password psql -h postgres -U indezy_user -d indezy'
alias indezy-logs='docker-compose logs -f'
alias indezy-status='docker-compose ps'

# Navigation aliases
alias indezy-server='cd /workspace/indezy-server'
alias indezy-web='cd /workspace/indezy-web'
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
echo "  indezy-server    - Start the indezy-server server"
echo "  indezy-web   - Start the indezy-web server"
echo "  indezy-db         - Connect to the database"
echo "  mask --help       - Show available mask commands"
echo ""
echo "📚 Database credentials:"
echo "  Host: postgres (or localhost from host)"
echo "  Database: indezy"
echo "  Username: indezy_user"
echo "  Password: indezy_password"
echo ""

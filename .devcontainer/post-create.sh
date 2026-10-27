#!/bin/bash

set -euo pipefail

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

if [ -f "indezy-web/package.json" ]; then
  echo "📦 Installing indezy-web dependencies..."
  (cd indezy-web && npm ci --prefer-offline --no-audit)
  # Chromium for the unit tests (Karma's ChromeHeadlessCI) and the Playwright end-to-end tests, with its system libraries.
  echo "🌐 Installing Chromium for the browser tests..."
  (cd indezy-web && npx playwright install --with-deps chromium)
  echo "✅ Frontend dependencies installed!"
fi

if [ -f "indezy-server/pom.xml" ]; then
  echo "📦 Installing indezy-server dependencies..."
  (cd indezy-server && ./mvnw -q dependency:go-offline && ./mvnw -q compile)
  echo "✅ Backend dependencies installed and compiled!"
fi

# Shell setup, written once: rebuilding the container runs this script again.
if ! grep -q "# Indezy development" ~/.bashrc; then
  echo "🔧 Setting up development aliases..."
  cat >> ~/.bashrc << 'ALIASES'

# Indezy development
# Karma finds the Chromium Playwright installed.
CHROME_BIN="$(cd /workspace/indezy-web 2>/dev/null && node -e "console.log(require('playwright-core').chromium.executablePath())" 2>/dev/null)"
export CHROME_BIN

alias indezy-server='cd /workspace/indezy-server && ./mvnw spring-boot:run'
alias indezy-web='cd /workspace/indezy-web && npm start'
alias indezy-test-server='cd /workspace/indezy-server && ./mvnw verify'
alias indezy-test-web='cd /workspace/indezy-web && npm run test:ci'
alias indezy-e2e='cd /workspace/indezy-web && npm run test:e2e'
alias indezy-db='PGPASSWORD=indezy_password psql -h postgres -U indezy_user -d indezy'
alias cd-server='cd /workspace/indezy-server'
alias cd-web='cd /workspace/indezy-web'
alias workspace='cd /workspace'
ALIASES
fi

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
echo "🚀 Quick start commands (open a new terminal first):"
echo "  indezy-server       - Start the backend"
echo "  indezy-web          - Start the frontend"
echo "  indezy-test-server  - Backend tests, Checkstyle and SpotBugs"
echo "  indezy-test-web     - Frontend unit tests"
echo "  indezy-e2e          - Playwright end-to-end tests"
echo "  indezy-db           - Connect to the database"
echo ""
echo "📚 Database credentials:"
echo "  Host: postgres (or localhost from host)"
echo "  Database: indezy"
echo "  Username: indezy_user"
echo "  Password: indezy_password"
echo ""

#!/bin/bash

set -euo pipefail

echo "⏳ Waiting for PostgreSQL..."
until pg_isready -q -h postgres -p 5432 -U indezy_user -d indezy; do
  sleep 2
done

echo "📦 Installing indezy-web dependencies..."
(cd indezy-web && npm ci --prefer-offline --no-audit)
# Chromium for the unit tests (Karma's ChromeHeadlessCI) and the Playwright end-to-end tests, with its system libraries.
echo "🌐 Installing Chromium for the browser tests..."
(cd indezy-web && npx playwright install --with-deps chromium)

echo "📦 Installing indezy-server dependencies..."
(cd indezy-server && ./mvnw -q dependency:go-offline && ./mvnw -q compile)

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

echo "✅ Development environment ready. Open a new terminal for the indezy-* aliases (see .devcontainer/README.md)."

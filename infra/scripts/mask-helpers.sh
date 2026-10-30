#!/usr/bin/env bash
# Sourced by the maskfile tasks. Under WSL, Maven, Docker and the backend run on the Windows host
# (cmd.exe, docker.exe, powershell.exe); elsewhere they run directly.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

if [[ -f .env ]]; then
    set -a
    # shellcheck disable=SC1090
    source <(sed 's/\r$//' .env)
    set +a
fi
export SERVER_PORT="${BACKEND_PORT:-8080}"
# WSL only forwards the listed variables to Windows processes.
export WSLENV="${WSLENV:+$WSLENV:}POSTGRES_PORT:POSTGRES_DB:POSTGRES_USER:POSTGRES_PASSWORD:SERVER_PORT:BACKEND_PORT:FRONTEND_PORT"

on_windows_host() {
    command -v cmd.exe >/dev/null 2>&1
}

# The Maven wrapper, run in indezy-server.
mvnw() {
    if on_windows_host; then
        (cd indezy-server && cmd.exe /c "mvnw.cmd $*")
    else
        (cd indezy-server && ./mvnw "$@")
    fi
}

compose() {
    if command -v docker.exe >/dev/null 2>&1; then
        docker.exe compose -f infra/compose/compose.yaml "$@"
    else
        docker compose -f infra/compose/compose.yaml "$@"
    fi
}

start_postgres() {
    echo "🐳 Starting PostgreSQL..."
    compose up -d --force-recreate postgres
    echo "⏳ Waiting for PostgreSQL..."
    until compose exec -T postgres pg_isready -U "${POSTGRES_USER:-indezy_user}" -d "${POSTGRES_DB:-indezy}" >/dev/null 2>&1; do
        sleep 1
    done
}

# Under WSL, localhost can be a separate network namespace from the Windows host where the backend runs.
http_ok() {
    if command -v curl.exe >/dev/null 2>&1; then
        curl.exe --silent --fail --max-time 3 "$1" >/dev/null
    else
        curl --silent --fail --max-time 3 "$1" >/dev/null
    fi
}

backend_health_url="http://localhost:${SERVER_PORT}/api/actuator/health"

#!/usr/bin/env bash

set -euo pipefail

backend_port="${BACKEND_PORT:-8080}"
frontend_port="${FRONTEND_PORT:-4200}"
database_port="${POSTGRES_PORT:-5432}"
ports=("$backend_port" "$frontend_port" "$database_port")

# Docker-published ports are owned by Docker's networking process on Windows.
# Stop the actual container first so we never terminate Docker Desktop itself.
docker_cli=()
if command -v docker.exe >/dev/null 2>&1 && docker.exe info >/dev/null 2>&1; then
    docker_cli=(docker.exe)
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    docker_cli=(docker)
fi

stopped_docker_container=false
if [[ ${#docker_cli[@]} -gt 0 ]]; then
    for port in "${ports[@]}"; do
        while read -r container_id container_name; do
            if [[ -z "${container_id:-}" ]]; then
                continue
            fi
            echo "Stopping Docker container $container_name ($container_id) publishing port $port..."
            "${docker_cli[@]}" stop "$container_id" >/dev/null
            stopped_docker_container=true
        done < <("${docker_cli[@]}" ps --filter "publish=$port" --format '{{.ID}} {{.Names}}' | tr -d '\r')
    done
fi

if [[ "$stopped_docker_container" == true ]]; then
    # Docker Desktop's forwarding listeners can linger briefly after a container exits.
    sleep 1
fi

if command -v powershell.exe >/dev/null 2>&1; then
    # WSL only forwards explicitly listed variables to Windows processes.
    export WSLENV="${WSLENV:+${WSLENV}:}BACKEND_PORT:FRONTEND_PORT:POSTGRES_PORT"

    # The single-quoted program is PowerShell and must not expand in Bash.
    # shellcheck disable=SC2016
    powershell.exe -NoProfile -NonInteractive -Command '
        $ErrorActionPreference = "Stop"
        $ports = @(
            if ($env:BACKEND_PORT) { [int]$env:BACKEND_PORT } else { 8080 }
            if ($env:FRONTEND_PORT) { [int]$env:FRONTEND_PORT } else { 4200 }
            if ($env:POSTGRES_PORT) { [int]$env:POSTGRES_PORT } else { 5432 }
        ) | Sort-Object -Unique

        foreach ($port in $ports) {
            $processIds = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty OwningProcess -Unique

            foreach ($processId in $processIds) {
                $process = Get-Process -Id $processId -ErrorAction Stop
                if ($process.ProcessName -match "(?i)docker|wslrelay") {
                    Write-Error "Port $port is still held by $($process.ProcessName); refusing to stop Docker Desktop or WSL networking"
                    exit 1
                }
                Write-Host "Stopping $($process.ProcessName) (PID $processId) on port $port..."
                Stop-Process -Id $processId -Force -ErrorAction Stop
            }

            for ($attempt = 0; $attempt -lt 50; $attempt++) {
                if (-not (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) {
                    break
                }
                Start-Sleep -Milliseconds 100
            }

            if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
                Write-Error "Port $port is still in use"
                exit 1
            }
        }
    '
    exit $?
fi

if command -v lsof >/dev/null 2>&1; then
    listener_pids() {
        lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null || true
    }
elif command -v fuser >/dev/null 2>&1; then
    listener_pids() {
        fuser "$1/tcp" 2>/dev/null || true
    }
else
    echo "Neither lsof nor fuser is available; cannot stop processes using local ports." >&2
    exit 1
fi

stop_port() {
    local port="$1"
    local -a process_ids=()
    mapfile -t process_ids < <(listener_pids "$port" | tr -s '[:space:]' '\n' | sed '/^$/d')

    if [[ ${#process_ids[@]} -eq 0 ]]; then
        return
    fi

    echo "Stopping process(es) ${process_ids[*]} on port $port..."
    kill "${process_ids[@]}" 2>/dev/null || true

    for _ in {1..50}; do
        if [[ -z "$(listener_pids "$port")" ]]; then
            return
        fi
        sleep 0.1
    done

    mapfile -t process_ids < <(listener_pids "$port" | tr -s '[:space:]' '\n' | sed '/^$/d')
    if [[ ${#process_ids[@]} -gt 0 ]]; then
        kill -9 "${process_ids[@]}" 2>/dev/null || true
    fi

    if [[ -n "$(listener_pids "$port")" ]]; then
        echo "Port $port is still in use" >&2
        exit 1
    fi
}

stop_port "$backend_port"
stop_port "$frontend_port"
stop_port "$database_port"

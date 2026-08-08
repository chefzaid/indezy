#!/usr/bin/env bash

set -euo pipefail

health_url="http://localhost:${BACKEND_PORT:-8080}/api/actuator/health"

# The Windows launcher runs Java on the Windows host. WSL's localhost can use
# a separate network namespace, so probe through Windows curl in that case.
if command -v curl.exe >/dev/null 2>&1; then
    curl.exe --silent --fail --max-time 3 "$health_url" >/dev/null
else
    curl --silent --fail --max-time 3 "$health_url" >/dev/null
fi

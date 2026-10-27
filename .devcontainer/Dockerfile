# Development container: an Ubuntu devcontainer base, with Java 25 and Node.js 26 added by the features in
# devcontainer.json. The server's own Dockerfile builds the runtime image, which holds no build tools.
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# psql and pg_isready for the database scripts, lsof for start-dev.sh's port checks.
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client lsof \
    && rm -rf /var/lib/apt/lists/*

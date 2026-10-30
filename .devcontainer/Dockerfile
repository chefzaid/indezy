# Development container: an Ubuntu devcontainer base, with Java 25 and Node.js 26 added by the features in
# devcontainer.json. The production images are built from each application's Dockerfile.runtime.
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# psql and pg_isready for post-create.sh, lsof for stop-local-ports.sh (mask run, mask stop).
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client lsof \
    && rm -rf /var/lib/apt/lists/*

#!/bin/bash

# Update system packages
sudo apt-get update

# Install Angular CLI globally
npm install -g @angular/cli

# Install PostgreSQL client
sudo apt-get install -y postgresql-client

# Install Maven (if not already installed)
sudo apt-get install -y maven

# Set up Git configuration (will be overridden by user)
git config --global init.defaultBranch main

# Create directories for the project structure
mkdir -p backend
mkdir -p frontend
mkdir -p docs

echo "Development environment setup complete!"
echo "Ports forwarded:"
echo "  - 8080: Spring Boot backend"
echo "  - 4200: Angular frontend"
echo "  - 5432: PostgreSQL database"

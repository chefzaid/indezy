#!/bin/bash

# Update system packages
sudo apt-get update

# Install Java 21 (OpenJDK)
sudo apt-get install -y openjdk-21-jdk

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> $HOME/.profile

# Install Node.js 18 (required for Angular 20)
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install Chrome for Karma tests (headless mode)
wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | sudo apt-key add -
echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" | sudo tee /etc/apt/sources.list.d/google-chrome.list
sudo apt-get update
sudo apt-get install -y google-chrome-stable

# Install Xvfb for headless browser testing
sudo apt-get install -y xvfb

# Make Maven wrapper executable
chmod +x backend/mvnw

# Install frontend dependencies
cd frontend
npm install
cd ..

# Install backend dependencies and compile
cd backend
./mvnw clean compile test-compile
cd ..

# Add Chrome and Node to PATH
echo 'export PATH=$PATH:/usr/bin' >> $HOME/.profile

# Source the profile to make sure environment variables are available
source $HOME/.profile
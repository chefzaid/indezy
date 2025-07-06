# Use the official Microsoft Java devcontainer as base
FROM mcr.microsoft.com/devcontainers/java:21-bullseye

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_VERSION=18
ENV MAVEN_VERSION=3.9.6

# Install system dependencies
RUN apt-get update && apt-get install -y \
    # Essential tools
    curl \
    wget \
    git \
    unzip \
    zip \
    # PostgreSQL client
    postgresql-client \
    # Build tools
    build-essential \
    # Network tools
    net-tools \
    iputils-ping \
    # Text editors
    nano \
    vim \
    # Process management
    htop \
    # Clean up
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js and npm
RUN curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - \
    && apt-get install -y nodejs

# Install Angular CLI globally
RUN npm install -g @angular/cli@latest

# Install Maven (latest version)
RUN wget https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Set Maven environment variables
ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

# Create workspace directory
RUN mkdir -p /workspace
WORKDIR /workspace

# Switch to vscode user
USER vscode

# Set up Git configuration
RUN git config --global init.defaultBranch main \
    && git config --global core.autocrlf input \
    && git config --global pull.rebase false

# Create Maven cache directory
RUN mkdir -p /home/vscode/.m2

# Create npm cache directory
RUN mkdir -p /home/vscode/.npm

# Set up shell environment
RUN echo 'export PATH=$MAVEN_HOME/bin:$PATH' >> /home/vscode/.bashrc \
    && echo 'export JAVA_HOME=/usr/local/sdkman/candidates/java/current' >> /home/vscode/.bashrc \
    && echo 'alias ll="ls -la"' >> /home/vscode/.bashrc \
    && echo 'alias la="ls -la"' >> /home/vscode/.bashrc \
    && echo 'alias ..="cd .."' >> /home/vscode/.bashrc

# Expose ports
EXPOSE 8080 4200 5432 5050

# Keep container running
CMD ["sleep", "infinity"]

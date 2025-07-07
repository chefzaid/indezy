# Multi-stage Dockerfile for Angular Frontend
# Can be used for both development and production

ARG BUILD_ENV=production
ARG NG_BUILD_CONFIG=production

# Stage 1: Build stage
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files first for better layer caching
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production --silent

# Copy source code
COPY . .

# Build the application with specified configuration
ARG NG_BUILD_CONFIG
RUN npm run build -- --configuration=${NG_BUILD_CONFIG}

# Stage 2: Development runtime (with Node.js for development server)
FROM node:18-alpine AS development

# Install development tools
RUN apk update && apk upgrade && \
    apk add --no-cache \
    curl \
    dumb-init \
    wget \
    && addgroup -g 1001 -S nodegroup \
    && adduser -u 1001 -S nodeuser -G nodegroup

WORKDIR /app

# Copy built application and source for development
COPY --from=builder /app .

# Change ownership to non-root user
RUN chown -R nodeuser:nodegroup /app

USER nodeuser

EXPOSE 4200

# Health check for development server
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:4200/ || exit 1

ENTRYPOINT ["dumb-init", "--"]

# Development server command
CMD ["npm", "start"]

# Stage 3: Production runtime (with Nginx)
FROM nginx:1.25-alpine AS production

# Install security updates and create non-root user
RUN apk update && apk upgrade && \
    apk add --no-cache dumb-init && \
    addgroup -g 1001 -S nginxgroup && \
    adduser -u 1001 -S nginxuser -G nginxgroup

# Copy built application from builder stage
COPY --from=builder /app/dist/frontend /usr/share/nginx/html

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Create nginx directories and set permissions
RUN mkdir -p /var/cache/nginx/client_temp && \
    mkdir -p /var/cache/nginx/proxy_temp && \
    mkdir -p /var/cache/nginx/fastcgi_temp && \
    mkdir -p /var/cache/nginx/uwsgi_temp && \
    mkdir -p /var/cache/nginx/scgi_temp && \
    mkdir -p /var/log/nginx && \
    mkdir -p /var/run && \
    chown -R nginxuser:nginxgroup /var/cache/nginx && \
    chown -R nginxuser:nginxgroup /var/log/nginx && \
    chown -R nginxuser:nginxgroup /var/run && \
    chown -R nginxuser:nginxgroup /usr/share/nginx/html && \
    chown -R nginxuser:nginxgroup /etc/nginx

USER nginxuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["dumb-init", "--"]

# Start nginx
CMD ["nginx", "-g", "daemon off;"]

# Final stage selection based on build argument
FROM ${BUILD_ENV} AS final

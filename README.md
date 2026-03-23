# Indezy - Job Tracking Platform for Freelancers

<div align="center">
  <img src="indezy-web/src/assets/images/indezy-logo.svg" alt="Indezy Logo" height="80">

  **Freelance opportunity tracking for the French tech market**

  [![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
  [![Angular](https://img.shields.io/badge/Angular-20.0.6-red.svg)](https://angular.io/)
  [![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)
</div>

## Overview
Indezy is a modern, full-stack web application that helps freelancers manage their job applications, track project opportunities, and maintain organized dashboards. Built with enterprise-grade technologies and designed for the French tech market, it provides a comprehensive solution for freelance project management.

## Implemented Scope (Current State)

- User registration and login with JWT (`/api/auth/register`, `/api/auth/login`)
- Freelance, project, client, contact, and source management (CRUD)
- Interview steps tracking (status transitions and scheduling)
- Kanban board with drag-and-drop status changes
- Dashboard page and project/client filtering capabilities
- Dashboard graphs and stats (projects by status, work mode distribution, daily rate chart)
- Commute-time sorting (home address and job location, Google Maps Distance Matrix API, driving/transit modes)
- Reversion rate calculator (net daily/monthly/yearly revenue, configurable income tax, max workable days with French public holidays)
- Internationalization (French/English)

## Next Phases

- Job board integrations (Freework, CherryPick, Malt, LinkedIn)
- Email integrations (Outlook/Gmail sync, templated follow-ups)
- Advanced analytics: daily rate evolution, client rating, conversion funnel
- In-app calendar with interview reminders
- Integration with external calendar or notification systems
- AI-powered job parsing from PDF, URLs, Outlook/Gmail
- Project recommendation engine based on peer shares and skill match
- Make reversiuon rate calculator more customizable (deductible expenses, social contributions, etc.)
- Automated status update suggestions based on user behavior
- Social/network features (freelancer network, sharing, chat, community engagement points/gamification, mutual connections, recommended contacts)
- Community/feature request board
- Cross-platform mobile applications
- Chrome extension for capture from job boards/LinkedIn

## Tech Stack

- Frontend: Angular 20, Angular Material, RxJS
- Backend: Java 21, Spring Boot 3.5.x, Spring Security, JPA/Hibernate
- Database: PostgreSQL (primary), H2 (local/test)
- Tooling: Docker Compose, Maven Wrapper, Mask command runner

## Project Structure

- `indezy-server/`: Spring Boot backend
- `indezy-web/`: Angular frontend
- `database/`: SQL initialization scripts
- `deployments/`: Kubernetes manifests (synced by ArgoCD to the `application` namespace)
- `argocd/`: ArgoCD Application bootstrap manifest (apply once manually)
- `Jenkinsfile`: Jenkins CI/CD pipeline (builds images, pushes to Nexus, updates manifests)
- `.devcontainer/`: Dev container setup
- `maskfile.md`: Executable workflow commands
- `PRD.md`: Product requirements and roadmap

## Kubernetes / ds-cluster Deployment

The application is designed to run on the [ds-cluster](https://github.com/chefzaid/ds-cluster) infrastructure (K3s + Jenkins + ArgoCD + Nexus + Nginx Ingress).

### Access URL

> **`https://indezy.swirlit.dev`** (via Nginx Ingress at `51.68.232.240:443`)

### Prerequisites

The following infrastructure must already be running (see `ds-cluster` repo):
- K3s cluster with Nginx Ingress Controller
- Jenkins at `https://jenkins.swirlit.dev`
- ArgoCD at `https://argocd.swirlit.dev`
- Nexus Docker registry at `nexus.swirlit.dev:5000`

### One-Time Bootstrap

**1. Add DNS record in Cloudflare**

| Type | Name | Value | Proxy |
|------|------|-------|-------|
| A | `indezy` | `51.68.232.240` | Proxied |

**2. Create TLS secret in the `application` namespace** (copy from infrastructure):

```bash
kubectl get secret swirlit-dev-tls -n infrastructure -o json \
  | jq 'del(.metadata.namespace, .metadata.resourceVersion, .metadata.uid, .metadata.creationTimestamp, .metadata.annotations, .metadata.managedFields) | .metadata.name = "indezy-swirlit-dev-tls" | .metadata.namespace = "application"' \
  | kubectl apply -f -
```

**3. Register the ArgoCD Application** (bootstrap GitOps):

```bash
kubectl apply -f argocd/indezy-app.yaml
```

ArgoCD will immediately sync all manifests from `deployments/` into the `application` namespace:
- `indezy-db-setup` — one-time Job that provisions the `indezy` database and `indezy_user` inside the shared infrastructure PostgreSQL
- `indezy-server` — Spring Boot backend
- `indezy-web` — Angular frontend (Nginx)
- `indezy-ingress` — Nginx Ingress routing

**4. Configure Jenkins** (CI/CD pipeline):

In Jenkins (`https://jenkins.swirlit.dev`):
- Create credential `nexus-docker-credentials` (Username/Password for Nexus Docker registry)
- Create credential `git-credentials` (GitHub Personal Access Token with `repo` scope)
- Create a Pipeline job → SCM: Git → `https://github.com/chefzaid/indezy.git` → Script Path: `Jenkinsfile`

On every push, Jenkins will:
1. Build and push Docker images to `nexus.swirlit.dev:5000`
2. Update the image tags in `deployments/*.yaml`
3. Commit and push — ArgoCD auto-syncs the new tags to the cluster

### Deployment Architecture

```
Browser → Cloudflare → Nginx Ingress (51.68.232.240:443)
                            │
                    indezy.swirlit.dev
                       /api  →  indezy-server:8080  (Spring Boot)
                         /  →  indezy-web:8080      (Angular/Nginx)
                                      │
                  postgres.infrastructure.svc.cluster.local:5432
                       (shared infrastructure PostgreSQL, db: indezy)
```

### Change Default Secrets

Before exposing to the internet, update the base64-encoded secrets in the YAML files:

```bash
# New DB password (used by indezy_user and the setup Job)
echo -n 'YOUR_DB_PASSWORD' | base64
# New JWT secret
echo -n 'YOUR_JWT_SECRET' | base64
# Infrastructure postgres admin password (must match postgres-secret in infrastructure ns)
echo -n 'YOUR_POSTGRES_ADMIN_PASSWORD' | base64
```

Edit `deployments/indezy-db-setup.yaml` and `deployments/indezy-server.yaml` with the new values.

### Google Maps API Key (Commute-Time Sorting)

The commute-time sorting feature uses the Google Maps Distance Matrix API.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project
3. Enable the **Distance Matrix API** (`Routes > Distance Matrix API`)
4. Create an API key under **APIs & Services > Credentials**
5. (Recommended) Restrict the key to the Distance Matrix API and to your server's IP
6. Set the environment variable before starting the backend:

```bash
export GOOGLE_MAPS_API_KEY=your-api-key-here
```

For Kubernetes, add the key to `deployments/indezy-server.yaml` as a Secret or env var:

```yaml
- name: GOOGLE_MAPS_API_KEY
  value: "your-api-key-here"   # or use a secretKeyRef
```

> **Note:** Without a valid API key the feature still works — projects are returned but without commute time/distance data.

## Commands

### Setup and Build
```bash
mask install
mask install-indezy-server
mask install-indezy-web
mask build
mask build-indezy-server
mask build-indezy-web
```

### Tests
```bash
mask test
mask test-indezy-server
mask test-indezy-web
mask test-coverage
```

### Run
```bash
mask run-indezy-server-local
mask run-indezy-web-local
mask run-local
mask run-indezy-server-dev
mask run-indezy-web-dev
mask run-dev
```

### Utility
```bash
mask info
mask status
mask stop
mask logs
```

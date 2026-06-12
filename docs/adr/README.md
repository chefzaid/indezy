# Architecture Overview And ADR Index

This directory contains architecture decision records for Indezy. ADRs capture decisions that should remain understandable after the code has moved on.

## System Architecture At A Glance

```text
Angular web app
  -> HTTP /api
  -> Spring Boot backend
  -> JPA repositories
  -> PostgreSQL

Jenkins
  -> builds Docker images
  -> pushes to Nexus
  -> updates Kubernetes manifests
  -> ArgoCD syncs manifests to K3s
```

Runtime entry points:

- frontend: `indezy-web`
- backend: `indezy-server`
- database: PostgreSQL in local Docker Compose or shared infrastructure PostgreSQL in cluster
- ingress host: `indezy.swirlit.dev`

## Backend Architecture

The backend is a Spring Boot modular monolith organized by technical layer:

- `controller`: HTTP endpoints
- `service`: business logic and orchestration
- `repository`: persistence queries
- `model`: JPA entities and enums
- `dto`: API contracts
- `mapper`: MapStruct conversions
- `config`: CORS, security, OpenAPI, data initialization
- `exception`: error responses and exception mapping

This structure keeps early development simple while preserving clear places for domain behavior and tests.

## Frontend Architecture

The frontend is an Angular application using:

- standalone components
- lazy-loaded route groups
- Angular Material
- RxJS
- `@ngx-translate`
- service classes for API access
- route guards and HTTP interceptors

Routes are organized around the main product areas:

- auth
- dashboard
- profile
- projects
- clients
- contacts
- sources

## Key Runtime Lifecycles

### Authenticated frontend request

1. User logs in through the Angular auth service.
2. Backend validates credentials and returns a JWT.
3. Frontend stores the token.
4. `authGuard` protects authenticated routes.
5. `authInterceptor` adds `Authorization: Bearer <token>` to API calls.
6. Backend should validate the token and enforce resource ownership.

Current caveat: backend authorization still needs hardening because non-public routes are currently permitted.

### Project creation

1. User opens project form.
2. Frontend loads clients and sources for selection.
3. User submits project data.
4. Project service sends the request to `/api/projects`.
5. Backend maps DTO to entity, resolves related freelance/client/source records, persists project, and returns DTO.
6. Dashboard/list/Kanban views consume the updated project data.

### Deployment

1. Jenkins checks out the repository.
2. Jenkins builds backend and frontend Docker images.
3. Images are pushed to Nexus with build-number and latest tags.
4. Jenkins updates image tags in `deployments/*.yaml`.
5. Jenkins commits and pushes manifest changes.
6. ArgoCD detects the Git change and syncs to K3s.

## Data Ownership Rules

Current practical rules:

- `Freelance` owns projects, clients, contacts, and sources.
- `Project` must have one final client and may have a middleman client.
- `Contact` belongs to a client and a freelance.
- `InterviewStep` belongs to a project.
- `User` owns account profile, settings, sessions, and security questions.

Future account work should clarify whether `User` and `Freelance` remain separate concepts or become a one-to-one account/workspace model.

## ADR Process

Add a new ADR when a decision:

- changes runtime architecture
- changes the data model or ownership rules
- introduces a new external dependency
- changes deployment or CI/CD behavior
- affects security, privacy, or operations
- constrains future product work

Use the next number in sequence and this filename shape:

```text
NNNN-short-title.md
```

## Accepted ADRs

- [ADR 0001: Split Documentation Out Of Root README](./0001-documentation-structure.md)
- [ADR 0002: Spring Boot And Angular Modular Monolith](./0002-spring-boot-angular-modular-monolith.md)
- [ADR 0003: Java 25, Angular 22, And PostgreSQL Stack](./0003-java-angular-postgresql-stack.md)
- [ADR 0004: GitOps Deployment Through Jenkins And ArgoCD](./0004-jenkins-argocd-gitops.md)
- [ADR 0005: Security Baseline And Hardening Direction](./0005-security-baseline.md)
- [ADR 0006: Code Quality And Verification Gates](./0006-code-quality-and-verification-gates.md)

## Proposed ADRs For Upcoming Work

Good candidates from the roadmap:

- versioned database migrations
- User/Freelance account boundary
- attachment storage and preview model
- email and calendar integration model
- AI provider and bring-your-own-key model
- mobile/offline synchronization
- feature flags and subscription entitlements
- observability baseline

## ADR Template

```markdown
# ADR NNNN: Title

- Status: Proposed | Accepted | Superseded
- Date: YYYY-MM-DD

## Context

What forces, constraints, and current facts made this decision necessary?

## Decision

What did we decide?

## Rationale

Why this option over the alternatives?

## Consequences

What becomes easier, harder, riskier, or more constrained?
```

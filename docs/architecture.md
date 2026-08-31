# Architecture Overview And ADR Index

This directory contains architecture decision records for Indezy. ADRs capture decisions that should remain understandable after the code has moved on.

## System Architecture At A Glance

```text
Angular web app
  -> HTTP /api
  -> Spring Boot backend
  -> JPA repositories
  -> PostgreSQL

GitLab CI
  -> tests and builds backend/frontend outputs
  -> publishes Generic Packages and immutable container images
  -> commits Kustomize image tags
  -> Argo CD syncs the exact Git revision to K3s
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
6. Backend validates the token for non-public routes; service-level resource ownership remains the next authorization hardening boundary.

### Project creation

1. User opens project form.
2. Frontend loads clients and sources for selection.
3. User submits project data.
4. Project service sends the request to `/api/projects`.
5. Backend maps DTO to entity, resolves related freelance/client/source records, persists project, and returns DTO.
6. Dashboard/list/Kanban views consume the updated project data.

### Deployment

1. GitLab CI checks out the repository.
2. Required `01-build`, optional `02-test`, and required `03-package` package and validate backend/frontend outputs.
3. Optional manual E2E validates the critical browser flow; quality independently publishes non-blocking dependency and Sonar reports from the test artifacts, manually in standard mode and automatically in full mode.
4. Release consumes the required build outputs and publishes checksummed packages/images; deploy requires that release job, while full mode automates build, release, and deploy.
5. Release commits its version and image tags, creates the matching tag, and prepares the next minor version.
6. Argo CD syncs that exact revision to K3s; CI waits for healthy reconciliation and checks both services.

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

- [ADR 0001: Split Documentation Out Of Root README](./adr/0001-documentation-structure.md)
- [ADR 0002: Spring Boot And Angular Modular Monolith](./adr/0002-spring-boot-angular-modular-monolith.md)
- [ADR 0003: Java 25, Angular 22, And PostgreSQL Stack](./adr/0003-java-angular-postgresql-stack.md)
- [ADR 0004: GitOps Deployment Through GitLab CI And ArgoCD](./adr/0004-gitlab-ci-argocd-gitops.md)
- [ADR 0005: Security Baseline And Hardening Direction](./adr/0005-security-baseline.md)
- [ADR 0006: Code Quality And Verification Gates](./adr/0006-code-quality-and-verification-gates.md)
- [ADR 0007: Explicit Delivery Jobs And Non-Blocking Verification](./adr/0007-explicit-delivery-jobs.md)

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

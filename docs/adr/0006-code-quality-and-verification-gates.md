# ADR 0006: Code Quality And Verification Gates

- Status: Accepted
- Date: 2026-06-12

The pipeline-gate and future-improvement portions of this decision are superseded by [ADR 0007](./0007-explicit-delivery-jobs.md). Its local test-tooling decisions remain active.

## Context

The repository has meaningful test infrastructure:

- Maven Surefire
- JaCoCo
- Checkstyle
- SpotBugs
- SonarQube properties
- Angular Karma/Jasmine tests
- Angular ESLint

The GitLab CI pipeline now validates Kubernetes state, runs Maven verification, runs Angular lint/headless tests and a production build, publishes deployable artifacts, builds runtime images, updates GitOps state, and verifies Argo CD rollout health.

## Decision

Use the existing test and quality tooling as the project baseline and evolve CI toward a stronger gate.

Local development should use:

- `mask test`
- `mask test-coverage`
- backend targeted Maven tests
- frontend targeted Karma tests
- frontend lint

The local verification baseline includes:

- backend tests
- frontend tests
- production builds
- lint checks

CI also preserves diagnostic/build artifacts and immutable Generic Package versions. The repository-wide SonarQube analysis, dependency audits, and Playwright critical-flow gate are defined by ADR 0007.

## Rationale

The project already has the right ingredients. The decision is to make them part of the expected workflow rather than treating them as optional extras.

## Consequences

New features should come with tests at the correct layer.

Coverage targets can be raised over time. The backlog target is 85 percent for both backend and frontend, while the current backend JaCoCo gate is lower.

When Mask commands and package scripts drift, they should be corrected together.

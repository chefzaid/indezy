# ADR 0006: Code Quality And Verification Gates

- Status: Accepted
- Date: 2026-06-12

## Context

The repository has meaningful test infrastructure:

- Maven Surefire
- JaCoCo
- Checkstyle
- SpotBugs
- SonarQube properties
- Angular Karma/Jasmine tests
- Angular ESLint

The Jenkins pipeline currently focuses on image build, image push, manifest update, and ArgoCD sync.

## Decision

Use the existing test and quality tooling as the project baseline and evolve CI toward a stronger gate.

Local development should use:

- `mask test`
- `mask test-coverage`
- backend targeted Maven tests
- frontend targeted Karma tests
- frontend lint

CI should grow toward:

- backend test gate
- frontend test gate
- production builds
- lint checks
- dependency vulnerability scanning
- Playwright critical-flow tests

## Rationale

The project already has the right ingredients. The decision is to make them part of the expected workflow rather than treating them as optional extras.

## Consequences

New features should come with tests at the correct layer.

Coverage targets can be raised over time. The backlog target is 85 percent for both backend and frontend, while the current backend JaCoCo gate is lower.

When Mask commands and package scripts drift, they should be corrected together.

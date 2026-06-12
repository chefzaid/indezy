# ADR 0001: Split Documentation Out Of Root README

- Status: Accepted
- Date: 2026-06-12

## Context

The root README mixed product overview, implemented features, roadmap items, local commands, deployment, secrets, infrastructure, and testing notes.

That made the README too long to scan and made detailed documentation harder to maintain. The project also needed space for operational runbooks, security notes, data model references, and architecture decisions.

## Decision

Keep the root README as the short project entry point and move detailed documentation into `docs/`.

The documentation tree is organized as:

- `docs/features.md`
- `docs/development.md`
- `docs/testing.md`
- `docs/deployment.md`
- `docs/operations.md`
- `docs/security.md`
- `docs/data-model.md`
- `docs/adr/`

Roadmap and backlog items remain in `TODO.md`.

## Rationale

This mirrors the structure used in sibling projects and gives each kind of reader a clear path:

- product readers start with features
- contributors start with development and testing
- deployers start with deployment
- operators start with operations
- reviewers start with security, data model, and ADRs

## Consequences

The README is easier to scan.

Detailed guides can grow without turning the root file into a dumping ground.

Cross-link maintenance matters more because information is now distributed across several documents.

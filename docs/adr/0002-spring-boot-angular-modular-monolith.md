# ADR 0002: Spring Boot And Angular Modular Monolith

- Status: Accepted
- Date: 2026-06-12

## Context

Indezy needs a practical full-stack architecture for a solo/small-team product. The current product domain is cohesive: authentication, freelancer profile, opportunities, clients, contacts, sources, interview steps, commute calculations, and dashboard analytics all operate on the same workspace data.

Splitting this into services too early would add infrastructure and operational overhead without a clear domain boundary payoff.

## Decision

Use a modular monolith:

- one Spring Boot backend application
- one Angular frontend application
- one PostgreSQL database
- clear backend packages by technical responsibility
- lazy-loaded frontend route areas by product surface

## Rationale

This gives the project:

- simple local development
- simple deployment
- shared transaction boundaries
- straightforward tests
- enough structure to avoid a single large code blob

The architecture still leaves room to extract future integrations, workers, or AI tasks if they become independently scalable or operationally different.

## Consequences

Feature work can move quickly because most code lives in one backend and one frontend.

The backend must keep service boundaries disciplined. Without that discipline, a modular monolith can still become tangled.

Future heavy integrations such as inbox sync, job-board scraping, and AI processing may need background jobs or separate workers.

# ADR 0003: Java 25, Angular 22, And PostgreSQL Stack

- Status: Accepted
- Date: 2026-06-12

## Context

Indezy needs a stack that supports authenticated CRUD workflows, relational data, dashboards, form-heavy UI, Docker packaging, and Kubernetes deployment.

The current repository already uses:

- Java 25
- Spring Boot 4.1.x
- Spring Security
- JPA/Hibernate
- MapStruct
- PostgreSQL
- Angular 22
- Angular Material
- RxJS
- Docker

## Decision

Continue with Java 25, Spring Boot 4.1, Angular 22, and PostgreSQL as the primary application stack.

Use H2 for lightweight local/test profiles where useful, but PostgreSQL remains the production data model target.

## Rationale

Spring Boot gives a mature backend foundation for validation, security, persistence, OpenAPI, testing, and container deployment.

Angular fits the current UI style: route-based business application, forms, guards, interceptors, dashboards, Material components, and i18n.

PostgreSQL fits the domain because opportunities, clients, contacts, sources, users, and steps are relational and need consistent ownership rules.

## Consequences

The team should invest in strong backend tests, DTO boundaries, and migration discipline.

Frontend work should follow Angular patterns rather than introducing another UI framework.

Documentation and backlog references should continue to name Angular as the frontend framework so they match the actual stack.

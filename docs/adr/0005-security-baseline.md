# ADR 0005: Security Baseline And Hardening Direction

- Status: Accepted
- Date: 2026-06-12

## Context

The application already has basic authentication mechanics:

- BCrypt password encoder
- JWT generation
- stateless session policy
- Angular auth guard
- Angular auth interceptor
- CORS configuration

However, the backend security config currently permits every request after explicitly public routes.

## Decision

Treat the current security implementation as a development baseline, not a production-complete security model.

Document the permissive backend rule as an explicit risk and prioritize hardening:

- require authentication for application APIs
- enforce resource ownership in services
- add security tests for anonymous, authenticated, and cross-owner access
- rotate all default secrets
- add rate limiting and brute-force protection
- review Swagger and actuator exposure

## Rationale

The frontend guard improves navigation but cannot protect data. The backend must enforce authorization.

Documenting the gap prevents accidental production claims and gives the backlog a concrete hardening direction.

## Consequences

Before real production use, security work is required.

New endpoints should be written as if backend authorization is mandatory, even before the global rule is fully tightened.

Security-sensitive changes should update [Security Reference](../security.md).

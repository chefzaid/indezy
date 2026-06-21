# Security Reference

This reference documents the current security posture and the expected direction for hardening. It should be read as an implementation reference, not a compliance guarantee.

## Security Posture Summary

Current implemented pieces:

- password hashing with BCrypt
- JWT generation after login and registration
- stateless Spring Security session policy
- Angular route guard for authenticated pages
- Angular auth interceptor for bearer tokens
- CORS configuration from application properties
- public Swagger/OpenAPI endpoints
- configurable JWT secret and expiration
- model fields for sessions, notification settings, security questions, and two-factor settings
- JWT bearer-token authentication enforced server-side: `JwtAuthenticationFilter` validates the `Authorization: Bearer` header and `SecurityConfig` requires authentication for all non-public endpoints (`anyRequest().authenticated()`)

Test escape hatch:

- `indezy.security.permit-all` (default `false`) relaxes the catch-all rule to `permitAll`. It is enabled only in `src/test/resources/application-test.properties` so integration tests can call endpoints without authenticating. It must never be enabled in a deployed environment.

## Authentication

Implemented auth endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`

Successful auth returns a JWT. The frontend stores and attaches that token through `authInterceptor`.

Brute-force protection: `LoginAttemptService` tracks failed logins per account (email, case-insensitive). After 5 failures within a 15-minute window the account is temporarily locked and `POST /api/auth/login` returns `429 Too Many Requests` until the window expires; a successful login clears the counter. State is in-memory per instance; IP-based and distributed (multi-instance) rate limiting remain future hardening.

Backend JWT settings:

```yaml
jwt:
  secret: ${JWT_SECRET:indezy-super-secret-key-change-in-production}
  expiration: 86400000
```

The default value must not be used for any internet-exposed deployment.

## Authorization

Current backend rules:

- `/auth/**` is public
- `/public/**` is public
- `/health` is public
- `/actuator/health` is public (other actuator endpoints require authentication)
- `/v3/api-docs/**` is public
- `/swagger-ui/**` is public
- `/swagger-ui.html` is public
- all other requests require a valid JWT (`anyRequest().authenticated()`); unauthenticated requests receive `401`

Remaining hardening direction:

1. Enforce ownership checks in services so one user cannot access another freelancer workspace.
2. Add tests for anonymous, authenticated, and cross-owner access.
3. Consider disabling Swagger in production or restricting it behind authentication/IP allowlists.

## Frontend Route Protection

Protected routes use `authGuard`:

- `/dashboard`
- `/profile`
- `/projects`
- `/clients`
- `/contacts`
- `/sources`

Public routes:

- `/login`
- `/register`
- `/404`
- `/error`

Frontend route guards improve user experience but are not a security boundary. The backend must enforce access control.

## Password Storage

The backend exposes a `PasswordEncoder` bean using BCrypt.

Rules:

- never store plain text passwords
- never return password hashes from APIs
- never log passwords
- keep password reset tokens short-lived when reset flow is implemented

## CORS

CORS is configured through `CorsConfig` and application properties.

Default local origins:

- `http://localhost:4200`
- `http://127.0.0.1:4200`

Local profile also allows:

- `http://0.0.0.0:4200`

Allowed headers include wildcard configuration plus explicit:

- `Authorization`
- `Content-Type`
- `Accept`
- `X-Requested-With`
- `Origin`

The backend exposes:

- `Authorization`

Production should use the smallest possible allowed-origin set.

## Secrets

Secrets currently used or anticipated:

- `JWT_SECRET`
- `DB_PASSWORD`
- `POSTGRES_ADMIN_PASSWORD`
- `GOOGLE_MAPS_API_KEY`
- OAuth client IDs and secrets for Google, GitHub, and Microsoft

Kubernetes manifests currently include base64 placeholder values. Base64 is encoding, not encryption.

Production expectations:

- rotate all defaults
- store real secrets outside Git where possible
- restrict Google Maps API key by API and server IP
- rotate JWT secret intentionally because it invalidates active tokens
- keep Jenkins, Nexus, and GitHub credentials scoped to the minimum necessary permissions

## OAuth Status

Backend configuration includes OAuth client registration placeholders for:

- Google
- GitHub
- Microsoft

Frontend environment files include matching OAuth client ID placeholders.

Treat OAuth as not product-complete until these are true:

- provider apps are configured
- callback URLs are documented
- auth flow is tested end to end
- account linking behavior is defined
- secrets are stored safely
- frontend and backend agree on token/session handling

## Session And Account Activity

The data model includes `UserSession`, with device, browser, location, IP address, last active time, and current-session flag.

Treat active session management as not complete until:

- sessions are created on login
- sessions are updated on activity
- users can view and revoke sessions
- JWT expiration and server-side session revocation semantics are designed

## Two-Factor Authentication

The `User` model includes:

- `twoFactorEnabled`
- `twoFactorSecret`

TOTP is listed in the backlog. Do not mark 2FA as implemented until enrollment, verification, recovery, disabling, and tests exist.

## Data Privacy

Indezy stores personal and commercially sensitive information:

- names and emails
- phone numbers
- addresses and city
- job prospects
- client and contact notes
- rates and revenue estimates
- possible CV file path
- security-question answer hashes

Account deletion and data portability:

- `POST /api/users/account/delete` performs a **soft delete**: it requires the account password as confirmation and sets `User.deletedAt` rather than removing the row. Soft-deleted accounts can no longer log in (`AuthService.login` rejects them with the generic invalid-credentials error).
- `GET /api/users/export` returns a GDPR data-portability export (`UserDataExportService`): a JSON document with the user's profile plus every project, client, contact and source they own, downloaded as an attachment.

Privacy expectations:

- avoid logging request bodies that may contain personal data
- avoid exposing cross-user records
- document retention and deletion behavior (e.g. purging soft-deleted accounts after a grace period)
- consider encrypting high-risk fields if threat model requires it

## External APIs

Google Maps Distance Matrix API is used for commute-time sorting.

Rules:

- keep the API key out of frontend code
- restrict the key in Google Cloud
- handle missing key gracefully
- do not log full addresses unnecessarily
- document API cost and quota behavior before broad use

## File Uploads And Attachments

Backend multipart limits are currently:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

The project model stores document paths, but a full attachment storage/security model is not complete.

Before enabling broad uploads:

- validate file type
- scan or quarantine untrusted files
- avoid executable content
- store files outside the web root
- enforce ownership on download
- add size, retention, and deletion rules

## Security Backlog

Priority items:

- enforce ownership checks for every user-owned resource
- add password reset and email validation
- add rate limiting and brute-force protection on auth endpoints
- wire Actuator safely and expose only appropriate endpoints
- introduce account deletion and GDPR export
- add audit log for sensitive account actions
- add dependency vulnerability scanning in CI
- review CORS and Swagger exposure for production

## Review Checklist For Security-Sensitive Changes

Before merging:

- Does the backend enforce the permission, not only the frontend?
- Does every query scope by owner where needed?
- Are new secrets kept out of Git?
- Are errors useful without leaking internals?
- Are logs free of passwords, tokens, addresses, and full request bodies?
- Are tests covering anonymous access, authenticated access, and cross-owner access?
- Are new public endpoints intentionally public?
- Does the change affect GDPR export or account deletion expectations?

## Related Guides

- [Development](./development.md)
- [Deployment](./deployment.md)
- [Operations](./operations.md)
- [Data Model](./data-model.md)
- [ADR Index](./adr/README.md)

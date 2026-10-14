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
- optional TOTP two-factor authentication, enforced at local password login
- JWT bearer-token authentication enforced server-side: `JwtAuthenticationFilter` validates the `Authorization: Bearer` header and `SecurityConfig` requires authentication for all non-public endpoints (`anyRequest().authenticated()`)

Test escape hatch:

- `indezy.security.permit-all` (default `false`) relaxes the catch-all rule to `permitAll`. It is enabled only in `src/test/resources/application-test.properties` so integration tests can call endpoints without authenticating. It must never be enabled in a deployed environment.

## Authentication

Production authentication is centralized in the shared Keycloak `swirlit` realm. The Traefik Ingress sends unauthenticated browser traffic to the cluster OAuth2 Proxy. On startup, Angular calls `GET /api/auth/sso`; Spring validates the forwarded Keycloak access token's signature, issuer, expiry, and `oauth2-proxy` audience through the realm JWKS before linking its verified email to an existing account or creating an Indezy profile. Only then does the API issue Indezy's existing local bearer token.

The Keycloak password never reaches Indezy. Local login and registration remain available for development. In production the UI is public and its API calls pass through the authentication Middleware.

This integration uses the platform's `oauth2-proxy` client. It requires no
Indezy-specific Keycloak client or platform-side application registration.
This repository owns the ingress authentication Middleware, issuer/JWKS
configuration, audience checks and local account lifecycle.

Implemented auth endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/sso`

The API accepts authentication through the explicit `Authorization: Bearer`
header. CSRF middleware is disabled because API cookies and existing servlet
sessions cannot authenticate writes. `SecurityConfigTest` verifies both their
rejection and successful bearer-authenticated writes without a CSRF token or a
session cookie. Revisit this choice if cookie-based API authentication is added.

Successful auth returns a JWT. The frontend stores and attaches that token through `authInterceptor`.

Brute-force protection: `LoginAttemptService` tracks failed logins per account (email, case-insensitive). After 5 failures within a 15-minute window the account is temporarily locked and `POST /api/auth/login` returns `429 Too Many Requests` until the window expires; a successful login clears the counter. Failed logins and lockouts are logged at `WARN` without the email (see [Logging](#logging)). State is in-memory per instance; IP-based and distributed (multi-instance) rate limiting remain future hardening.

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

Workspace isolation: `AccessGuard` resolves the caller's freelance workspace from the JWT
account and every controller checks ownership before delegating. Records of another workspace
answer `404` (so their ids cannot be probed), an explicit foreign workspace id answers `403`, and
list-all endpoints return only the caller's records. Seasons follow the same rules, including the
`seasonId` filter of the Kanban and dashboard endpoints. `WorkspaceIsolationIntegrationTest` covers
cross-owner reads, writes, references, listings and seasons with two real accounts. The checks are skipped
only when `indezy.security.permit-all` is enabled for anonymous test runs.

Remaining hardening direction:

1. Consider disabling Swagger in production or restricting it behind authentication/IP allowlists.

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

Shared project links resolve to the same protected detail route and ownership-checked
API as ordinary project navigation. Password login preserves only local application
return paths; external and recursive login destinations fall back to the dashboard.

## Password Storage

The backend exposes a `PasswordEncoder` bean using BCrypt.

Password policy for local accounts:

- registration requires at least 6 characters (`RegisterRequest`, mirrored by the register form); the profile form asks for at least 8 for a new password
- a password is at most 72 bytes in UTF-8, the most BCrypt hashes: Spring Security's BCrypt throws `IllegalArgumentException("password cannot be more than 72 bytes")` for a longer one instead of hashing it. An accented letter takes 2 bytes and an emoji 4, so 37 accented letters are already too long. The `@PasswordMaxBytes` constraint (`validation` package) checks the UTF-8 byte length of `RegisterRequest.password` and `PasswordChangeRequestDto.newPassword`, so registration and the password change answer `400` with a validation error on that field before BCrypt is reached
- the register and profile forms apply the same limit with the `passwordMaxBytes` validator (`shared/utils/password-max-bytes.ts`) and explain it in the field error (`Maximum 72 bytes (é = 2, emoji = 4)`)
- the login and current passwords are not limited: BCrypt only checks them, which never throws

Rules:

- never store plain text passwords
- never return password hashes from APIs
- never log passwords
- keep password reset tokens short-lived when reset flow is implemented

## CORS

CORS is configured through `CorsConfig` and application properties. Controllers
inherit this origin allowlist. Security integration tests verify that user
updates accept the configured origin and reject an untrusted origin.

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

Production Kubernetes manifests contain no secret values. External Secrets projects application values from `apps/indezy/*` and the database administrator contract from `infra/postgres`; base64 would be encoding, not encryption.

Production expectations:

- rotate all defaults
- store real secrets outside Git where possible
- restrict Google Maps API key by API and server IP
- rotate JWT secret intentionally because it invalidates active tokens
- keep GitLab job-token permissions and the read-only registry deploy token scoped to the minimum necessary access

## Two-Factor Authentication

Local accounts can enable TOTP two-factor authentication from the profile page: the API returns
a secret and its `otpauth` URI, `POST /api/users/security/2fa/verify` activates it once a code
from the authenticator app is valid, and `POST /api/users/security/2fa/disable` removes it.

When it is enabled, `POST /api/auth/login` needs the password and a `totpCode`. A correct
password without a code answers `401` with `twoFactorRequired: true` (not counted as a failed
attempt) so the login page asks for the code; a wrong code is a failed login. Keycloak SSO
sign-ins rely on the realm's own authentication policy instead. There is no recovery code yet.

The current `TotpService` keeps HMAC-SHA1 compatibility with its existing
`otpauth` enrollment URIs. Its tests verify the SHA1 vectors from
[RFC 6238 Appendix B](https://www.rfc-editor.org/rfc/rfc6238#appendix-B),
reduced to the configured six digits. This protocol use of a keyed MAC is
distinct from password hashing; changing its algorithm requires migrating
enrolled authenticators.

## Data Privacy

Indezy stores personal and commercially sensitive information:

- names and emails
- phone numbers
- addresses and city
- job prospects
- client and contact notes
- rates and revenue estimates
- possible CV file path

Account deletion and data portability:

- accounts are not deleted from the application: identities live in Keycloak, and an account whose `User.deletedAt` is set can no longer sign in (`AuthService` rejects it with the generic invalid-credentials error)
- `GET /api/users/export` returns a GDPR data-portability export (`UserDataExportService`): a JSON document with the user's profile plus every project, client, contact and source they own, downloaded as an attachment.

Privacy expectations:

- avoid logging request bodies that may contain personal data
- avoid exposing cross-user records
- document retention and deletion behavior (e.g. purging an account's workspace on request)
- consider encrypting high-risk fields if threat model requires it

## Logging

Logs must not carry what a user typed. `GlobalExceptionHandler` applies these rules:

- client errors (4xx: not found, illegal argument, validation, constraint violation, malformed request, data conflict) are logged at `WARN`, never at `ERROR`, and without a stack trace
- the log line names only the field or parameter and the constraint (`Validation failed for contactDto: email (Email)`), or the database constraint name and SQL state (`Data integrity violation: constraint <name>, SQL state 23505`)
- for these client errors, the binding, validation, JSON parser and database messages are never logged, because they quote the rejected values (emails, phone numbers, passwords) and the conflicting key values; an `IllegalArgumentException` is logged by the class, method and line that threw it, since its message can quote input such as an email that already exists
- the method-not-supported, upload-too-large and access-denied handlers log the framework or application message at `WARN`, which names the HTTP method, the upload size limit or the access rule and holds no field values
- application exceptions (`ResourceNotFoundException`, `ResourceInUseException`) are logged with their message, so they must be built from ids and fixed text only, never from request values
- unexpected server errors (5xx) stay at `ERROR` with their message and stack trace, as they are needed to diagnose faults

The restriction applies to the server log only: error responses still return each invalid field with its message, and the message of an `IllegalArgumentException`, to the client that sent the request. `GlobalExceptionHandlerTest` captures the log output to check the level of each line and that no rejected value appears.

The authentication flow (`AuthController`, `AuthService`) never logs the email, at any level, and keeps its responses:

- a wrong email, password or two-factor code (`Login rejected: invalid credentials`), a locked account (`Login blocked after too many failed attempts`) and an existing account at registration (`Registration rejected: an account already exists for this email`) are client errors logged at `WARN` without a stack trace
- a rejected Keycloak SSO exchange (missing, invalid, expired or unverified identity) is logged at `WARN` with its reason, which is fixed text or a token validation error, never a claim value
- any other `IllegalArgumentException` at registration answers `400`, not the `409` of an existing account, and is logged at `WARN` naming only where it was thrown (`Registration rejected at <class.method:line>`), since its message can quote the input; validation rejects the known case, a password over the 72 bytes BCrypt can hash, before it is reached (see [Password Storage](#password-storage))
- a database constraint error during login, registration or SSO (two requests creating the same account at once, a value too long for its column) is logged at `WARN` with the constraint name and SQL state (`Registration rejected by a database constraint: constraint <name>, SQL state 23505`), described by the same `ExceptionLogDetails` helper as `GlobalExceptionHandler`, without the database message, which quotes the email
- any other failure is a server fault logged at `ERROR` with its stack trace
- no account reference is logged, not even a hash, since hashing candidate addresses would reverse it; debug lines name an account by its user ID, and only once it is authenticated or created

`AuthControllerTest` checks the level of each case and that the email is absent; `AuthLoggingIntegrationTest` replays registration, a duplicate registration and wrong passwords up to the lockout over HTTP, with Spring MVC and the test client logging the request and response bodies at `DEBUG`, and checks that the whole log never contains the email, the name or a password.

Spring MVC logs each request and response body at `DEBUG` through its `toString`, which Lombok's `@Data` generates with every field. Production keeps `org.springframework.web` at `WARN`, but the `test` profile raises it to `DEBUG`, as an operator may when diagnosing a deployment, so no DTO or entity prints a secret:

- `LoginRequest`, `RegisterRequest`, `PasswordChangeRequestDto`, `TwoFactorSetupDto` and `LoginResponse` mark their passwords, two-factor code, TOTP secret, provisioning URI (which embeds the secret and the email) and JWT with `@ToString.Exclude`
- the auth requests and the login response also leave out the email and the name, so a login is logged as `LoginRequest()` and its response as `LoginResponse(user=LoginResponse.UserInfo(id=1, accountId=1), twoFactorRequired=null)`
- the `User` and `Freelance` entities, which hold the password hash and the two-factor secret, generate no `toString`
- JSON serialization is unchanged: the excluded fields are still read from requests and returned in responses

`SensitiveToStringTest` scans the `dto` and `model` packages and fails when a class holding a field named like a password (or its hash), a secret, a token, a two-factor code, a backup code or an API key prints it, or when such a class, or a class nested in one, prints the email or a name.

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
      max-file-size: 5MB
      max-request-size: 6MB
```

The 5 MB file limit matches the avatar picker; the 6 MB request limit leaves
room for multipart encoding. Both standard and devcontainer profiles enforce it.

The project model stores document paths, but a full attachment storage/security model is not complete.

Before enabling broad uploads:

- validate file type
- scan or quarantine untrusted files
- avoid executable content
- store files outside the web root
- enforce ownership on download
- add size, retention, and deletion rules

## Software Supply Chain and Code Quality

Required `01-build` and `03-package` are separate from optional `02-test`. Optional manual `01-e2e`, allowed-to-fail `02-quality`, and independent `03-security` are verify jobs; standard mode leaves quality/security manual and full mode runs them automatically. Trivy scans dependencies, IaC, and secrets, retains JSON/SARIF findings for seven days, and exits nonzero on high/critical findings without becoming a deployment gate. `01-release` depends only on the required build path.

The manual release-publication job publishes immutable, checksummed JAR and SPA archives to GitLab's registries; deployment starts only after publication passes. Daemonless Kaniko reuses 30-day registry-backed image layers without privileged runner access. Credentials remain in Vault, masked project CI variables, or short-lived GitLab job credentials.

## Security Backlog

Priority items:

- enforce ownership checks for every user-owned resource
- add password reset and email validation
- add rate limiting and brute-force protection on auth endpoints
- wire Actuator safely and expose only appropriate endpoints
- introduce account deletion and GDPR export
- add audit log for sensitive account actions
- review CORS and Swagger exposure for production

## Review Checklist For Security-Sensitive Changes

Before merging:

- Does the backend enforce the permission, not only the frontend?
- Does every query scope by owner where needed?
- Are new secrets kept out of Git?
- Are errors useful without leaking internals?
- Are logs free of passwords, tokens, addresses, full request bodies, and rejected values (see [Logging](#logging))?
- Are tests covering anonymous access, authenticated access, and cross-owner access?
- Are new public endpoints intentionally public?
- Does the change affect GDPR export or account deletion expectations?

## Related Guides

- [Development](./development.md)
- [Deployment](./deployment.md)
- [Operations](./operations.md)
- [Data Model](./data-model.md)
- [Architecture Overview and ADR Index](./architecture.md)

### Runtime dependency maintenance

Spring Boot 4.1.1 supplies patched Jackson, Log4j and PostgreSQL JDBC
dependencies. Tomcat remains overridden to 11.0.25 for CVE-2026-65182,
CVE-2026-65905 and CVE-2026-68525 until Spring Boot manages a fixed version.
The web runtime uses digest-pinned NGINX 1.30.4 on Alpine, and both CI runtime
Dockerfiles apply Alpine security updates before dropping privileges. Rebuild
and scan packaged images to verify both application and OS dependencies.
NGINX runs directly as PID 1 and supervises its own workers; the web image and
Kubernetes workload do not depend on an additional init executable. Validate
the published image's startup and graceful termination as well as its scan.

### Container configuration hardening

The application workloads run with UID and GID 10001, above the host system-user
range, with the existing read-only filesystem, dropped capabilities and runtime
seccomp profile. Writable application data and temporary files use explicit
volumes.

Bare-metal database helper jobs consume the official public PostgreSQL 18.6
image pinned by digest. They execute client commands as UID 65534 with a read-only
filesystem and need no platform registry credential. This repository owns their
configuration and image pin; application images retain app-owned pull credentials.

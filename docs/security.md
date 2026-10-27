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
- `/public/**` is public: today only the activity report validation endpoints, authorized by their link token (see [Activity Report Validation Links](#activity-report-validation-links))
- `/health` is public
- `/actuator/health` is public (other actuator endpoints require authentication)
- `/v3/api-docs/**` is public
- `/swagger-ui/**` is public
- `/swagger-ui.html` is public
- all other requests require a valid JWT (`anyRequest().authenticated()`); unauthenticated requests receive `401`
- `/admin/**` also requires an administrator, an account whose email `INDEZY_ADMIN_EMAILS` names (`AdminService`);
  anyone else receives `403`
- a feature an administrator turned off answers `403` on its paths, public ones included (`FeatureFlagInterceptor`,
  see [Admin Console And Feature Flags](./features.md#admin-console-and-feature-flags))

A JWT authenticates only while its session is open and its account active. Each sign-in opens a session
(`account_sessions`) named by the token's `sid` claim, and `JwtAuthenticationFilter` checks on every request that the
session is neither ended nor expired and that the account is neither deleted nor disabled. A token therefore stops
working at once when its owner signs out (the app ends the session with a keepalive request before redirecting), ends
the session from **Profile → Security**, changes the password (every other session ends), or when an administrator
disables the account (every session ends), instead of staying valid until it expires. The Keycloak exchange the app makes on every load
(`GET /api/auth/sso`) continues the session of the token it sends, with a new token, instead of opening another one. Tokens issued before sessions
were kept carry no `sid` and are refused: their owners sign in again once. Disabled accounts are also refused at
password and Keycloak sign-in. Sign-ins, refused sign-ins of an existing account and security changes are recorded in
the account's activity log (`account_events`, kept a year), which only its owner reads; neither table keeps an IP
address or the full User-Agent.

Personal API tokens (`idz_` followed by 32 random bytes, Base64URL) authenticate `Authorization: Bearer` requests
without a session. Only their SHA-256 hash is stored (`api_tokens.token_hash`) with their first 12 characters for
display, so a copy of the database cannot call the API; the token is returned once, at creation. `JwtAuthenticationFilter`
accepts a token while it is neither revoked nor expired and its account is active, and grants it the `API_TOKEN`
authority; `ApiTokenScopeInterceptor` then refuses (`403`) any method but `GET` and `HEAD`, and every path under
`/users`, `/admin` and `/auth` (decoded as Spring MVC matches it), so a token can neither write nor manage the account,
its sessions or other tokens. Workspace checks apply as for a session. An account holds 10 tokens at most.

Workspace isolation: `AccessGuard` resolves the caller's freelance workspace from the JWT account
(through `CurrentWorkspaceResolver`, once per request) and every controller checks ownership before
delegating. Records of another workspace answer `404` (so their ids cannot be probed), an explicit
foreign workspace id answers `403`, and lists are read per workspace (`/by-freelance/{id}`): there is no endpoint listing every record, which could fall back to other workspaces' data. Seasons
follow the same rules, including the `seasonId` filter of the Kanban and dashboard endpoints.
`WorkspaceIsolationIntegrationTest` covers cross-owner reads, writes, references, listings and
seasons with two real accounts. The checks are skipped only when `indezy.security.permit-all` is
enabled for anonymous test runs.

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
- `/activity-report` (activity report validation by a client or ESN)
- `/404`
- `/error`

`/admin` also needs `adminGuard`, and the pages of a feature that can be turned off (`/offers`, `/review`) need
`featureGuard`: both read `GET /users/capabilities` and send anyone else to the dashboard.

Frontend route guards improve user experience but are not a security boundary. The backend must enforce access control.

Shared project links resolve to the same protected detail route and ownership-checked
API as ordinary project navigation. Password login preserves only local application
return paths; external and recursive login destinations fall back to the dashboard.

## Activity Report Validation Links

A client or ESN approves a monthly activity report without an Indezy account, through a link the freelance sends. The
link is a capability, so it is built to be unguessable, short-lived, revocable and absent from logs:

- submitting a report generates 32 random bytes (`SecureRandom`), Base64url encoded (43 characters); the API returns the
  token once, in `ActivityReportLinkDto`, and stores only its SHA-256 hash (`activity_reports.validation_token_hash`), so a
  database read does not give working links. A lost link is replaced (`POST .../link`), which revokes the previous one
- a link works for 30 days (`ActivityReportService.LINK_VALIDITY`); reopening the report (`POST .../reopen`) or
  resubmitting it revokes it at once. An unknown, revoked, expired, empty or oversized (over 128 characters) token
  answers the same `404` (`This validation link is invalid or has expired`)
- the token travels after the `#` of the link (`https://<host>/activity-report#<token>`), which browsers never send to
  a server, so neither NGINX nor Traefik access logs nor `Referer` headers hold it; the page sends it in the
  `X-Report-Token` header, never in the URL, and the Angular service tests check that no request URL contains it
- the public endpoints (`/api/public/activity-reports`, `.../pdf`, `.../decision`) need no JWT. In production the
  `indezy-public-api-ingress` Ingress routes `/api/public` without the `indezy-sso` ForwardAuth Middleware (the longer
  path wins over `/api`), and the Angular app skips its Keycloak session exchange on `/activity-report`
  (`PUBLIC_PAGE_PATHS` in `auth.service.ts`), which would otherwise send the visitor to sign in
- the public view leaves out ids, the daily rate and the amount; it shows the consultant's name, the client and ESN, the
  mission, the month and the comment, which the freelance chose to send
- a report is answered once (`409` afterwards); the validator signs with a typed name (at most 120 characters) and a
  rejection needs a reason. This is a simple electronic signature with its timestamp, not a qualified one
- the token never reaches the logs: the DTO excludes it from `toString`, the controllers log the path only, and
  `ActivityReportIntegrationTest` replays the whole flow with Spring MVC at `DEBUG` and checks that the captured log
  never contains it. The validator's name and comment are excluded from the DTOs' `toString` as personal data

## Password Storage

The backend exposes a `PasswordEncoder` bean using BCrypt.

Password policy for local accounts:

- registration requires at least 8 characters (`RegisterRequest`, mirrored by the register form), and so does a password change for the new password (`PasswordChangeRequestDto.newPassword`, mirrored by the profile form); a shorter one answers `400` with a validation error on that field, so a request sent outside the form cannot set it, and the current password is kept
- a password change answers `400` with a validation error on `confirmPassword` (`New password and confirmation do not match`) when the confirmation differs from the new password, as the profile form checks, and on `currentPassword` (`Current password is incorrect`) when the current password is wrong; the field's name is the code the profile form translates and shows on that field, and the log names only the field (`Rejected field: currentPassword`)
- a password is at most 72 bytes in UTF-8, the most BCrypt hashes: Spring Security's BCrypt throws `IllegalArgumentException("password cannot be more than 72 bytes")` for a longer one instead of hashing it. An accented letter takes 2 bytes and an emoji 4, so 37 accented letters are already too long. The `@PasswordMaxBytes` constraint (`validation` package) checks the UTF-8 byte length of `RegisterRequest.password` and `PasswordChangeRequestDto.newPassword`, so registration and the password change answer `400` with a validation error on that field before BCrypt is reached
- the register and profile forms apply the same limit with the `passwordMaxBytes` validator (`shared/utils/password-max-bytes.ts`) and explain it in the field error (`Maximum 72 bytes (é = 2, emoji = 4)`)
- the login and current passwords have no minimum or maximum: BCrypt only checks them, which never throws, so an account with a shorter password (6 or 7 characters from registration before it required 8, or one set before the password change had a minimum) still signs in and can replace it

Rules:

- enforce every password rule on the server; a form check only mirrors it for feedback
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
Both read `{"code": "..."}` into `TwoFactorCodeRequest`: a missing or blank code is a `400`
validation error, and any other wrong code, malformed or not, answers `false` on verify and
`400` on disable.

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
- client and contact notes, and private notes on job offers
- saved searches and when an offer was opened from their emails
- rates and revenue estimates
- possible CV file path

Account deletion and data portability:

- accounts are not deleted from the application: identities live in Keycloak, and an account whose `User.deletedAt` is set can no longer sign in (`AuthService` rejects it with the generic invalid-credentials error)
- `GET /api/users/export` returns a GDPR data-portability export (`UserDataExportService`): a JSON document with the user's profile plus every project, client, contact, source and activity report they own, downloaded as an attachment, with dates in ISO-8601. It also lists the job offers the workspace saved, hid, opened or imported (`jobOffers`), the opportunities' journal notes (`projectNotes`), the job-hunting seasons (`seasons`) and the workspace's freelance record (`freelance`: employment status, notice period, reversion and income tax rates).
- `GET /api/public/profiles/{slug}` is read without a token, like every `/api/public` path (the ingress lets it past the SSO): it answers only profiles their owner turned on, the same `404` for a hidden profile and an unknown address, no email, phone, address or workspace record, and only `http(s)` links, so a `javascript:` link saved on the profile is never served. Its `toString` prints nothing about the person.
- `POST /api/users/import/preview` and `POST /api/users/import` bring an export into the signed-in account's own workspace only, whatever ids or workspace the file names: every record is created afresh under the caller's workspace through the same services and validation as the API, references are remapped to the records just created or matched, and the upload is bounded by the 10 MB multipart limit. The report lists records by name, and its `toString` (logged at DEBUG) prints statuses only. The profile comes from the signed-in account and every other section from its workspace, found through the account's email like every other resource (`AccessGuard`), never from the account id: account and workspace ids are numbered separately.

Privacy expectations:

- avoid logging request bodies that may contain personal data
- avoid exposing cross-user records
- document retention and deletion behavior (e.g. purging an account's workspace on request)
- consider encrypting high-risk fields if threat model requires it

## Logging

Logs must not carry what a user typed. `GlobalExceptionHandler` applies these rules:

- client errors (4xx: not found, illegal argument, validation, constraint violation, malformed request, data conflict, and the other Spring MVC request errors below) are logged at `WARN`, never at `ERROR`, and without a stack trace
- the log line names only the field or parameter and the constraint (`Validation failed for contactDto: email (Email)`, or `Validation failed for <Controller>.<method>: page (Min)` for a constraint declared on a controller method parameter, such as `@RequestParam @Min(1) int page`), or the database constraint name and SQL state (`Data integrity violation: constraint <name>, SQL state 23505`)
- for these client errors, the binding, validation, JSON parser and database messages are never logged, because they quote the rejected values (emails, phone numbers, passwords) and the conflicting key values; an `IllegalArgumentException` is logged by the class, method and line that threw it, since its message can quote input such as an email that already exists
- the method-not-supported, upload-too-large and access-denied handlers log the framework or application message at `WARN`, which names the HTTP method, the upload size limit or the access rule and holds no field values
- the other Spring MVC request errors, which have no dedicated handler (an unsupported `Content-Type`, an unacceptable `Accept`, a missing header, and any later one implementing Spring's `ErrorResponse`), keep their own status (`415`, `406`, `400`, ...) and headers (such as `Accept`, listing the supported types) instead of becoming a `500`, and are logged at `WARN` by exception type and status only (`Rejected request: HttpMediaTypeNotSupportedException (415)`), since their message can quote request values such as the rejected `Content-Type`; a `406` answers without a body, since the client accepts no JSON, and a `405` lists the supported methods in `Allow`. One that Spring reports as a server error (5xx), such as a controller returning a value that breaks its own constraint, is logged like an unexpected error
- application exceptions (`ResourceNotFoundException`, `ResourceInUseException`, `InvalidStateException`, the `409` of a request that does not fit a resource's state) are logged with their message, so they must be built from ids and fixed text only, never from request values
- unexpected server errors (5xx) stay at `ERROR` with their message and stack trace, as they are needed to diagnose faults, and answer only `An unexpected error occurred`

The restriction applies to the server log only: error responses still return each invalid field with its message (a constraint message, which does not quote the rejected value), the message of an `IllegalArgumentException`, and Spring's description of a request error (`Content-Type 'text/plain' is not supported.`), to the client that sent the request. A client whose `Accept` header takes no JSON gets the status alone: a body it
refuses could not be written, and Spring would then answer through its own resolver, which logs the exception's message
at `WARN`. `GlobalExceptionHandlerTest` captures the log output to check the level of each line and that no rejected value appears, and `GlobalExceptionHandlerMvcTest` lets Spring MVC raise these errors on a probe controller to check that each reaches its handler.

The authentication flow (`AuthController`, `AuthService`) never logs the email, at any level, and keeps its responses:

- a wrong email, password or two-factor code (`Login rejected: invalid credentials`), a locked account (`Login blocked after too many failed attempts`) and an existing account at registration (`Registration rejected: an account already exists for this email`) are client errors logged at `WARN` without a stack trace
- a rejected Keycloak SSO exchange (missing, invalid, expired or unverified identity) is logged at `WARN` with its reason, which is fixed text or a token validation error, never a claim value
- any other `IllegalArgumentException` at registration answers `400`, not the `409` of an existing account, and is logged at `WARN` naming only where it was thrown (`Registration rejected at <class.method:line>`), since its message can quote the input; validation rejects the known case, a password over the 72 bytes BCrypt can hash, before it is reached (see [Password Storage](#password-storage))
- a database constraint error during login, registration or SSO (two requests creating the same account at once, a value too long for its column) is logged at `WARN` with the constraint name and SQL state (`Registration rejected by a database constraint: constraint <name>, SQL state 23505`), described by the same `ExceptionLogDetails` helper as `GlobalExceptionHandler`, without the database message, which quotes the email
- any other failure is a server fault logged at `ERROR` with its stack trace
- no account reference is logged, not even a hash, since hashing candidate addresses would reverse it; debug lines name an account by its user ID, and only once it is authenticated or created

`AuthControllerTest` checks the level of each case and that the email is absent; `AuthLoggingIntegrationTest` replays registration, a duplicate registration and wrong passwords up to the lockout over HTTP, with Spring MVC and the test client logging the request and response bodies at `DEBUG`, and checks that the whole log never contains the email, the name or a password.

Production logs the application (`dev.swirlit.indezy`) at `INFO`: `application.yml` sets it and the `kubernetes` profile keeps it, while the `devcontainer` and `test` profiles raise it to `DEBUG`. Debug lines name a record by its id, never by the names, emails or company names submitted (`Creating new contact`, then `Created contact with id: 12`). `LoggingLevelsTest` checks the production level, and `ContactServiceTest` that creating a contact logs neither of its names.

Spring MVC's `ExceptionHandlerExceptionResolver` logs every exception it resolves at `DEBUG` with its message, which for a validation error quotes the rejected values: every profile keeps that logger at `INFO`, `GlobalExceptionHandler` logging the safe line instead, and `PasswordLengthIntegrationTest` checks no rejected password reaches the log.

Spring MVC logs each request and response body at `DEBUG` through its `toString`, which Lombok's `@Data` generates with every field. Production keeps `org.springframework.web` at `WARN`, but the `test` profile raises it to `DEBUG`, as an operator may when diagnosing a deployment, so no DTO or entity prints a secret:

- `LoginRequest`, `RegisterRequest`, `PasswordChangeRequestDto`, `TwoFactorSetupDto`, `TwoFactorCodeRequest` and `LoginResponse` mark their passwords, two-factor codes, TOTP secret, provisioning URI (which embeds the secret and the email) and JWT with `@ToString.Exclude`
- a body holding a secret is read into such a DTO, never into a `Map`, whose `toString` prints every entry (`to [{code=123456}]`)
- the auth requests and the login response also leave out the email and the name, so a login is logged as `LoginRequest()` and its response as `LoginResponse(user=LoginResponse.UserInfo(id=1, accountId=1), twoFactorRequired=null)`
- the bodies about a person (`ContactDto`, `FreelanceDto`, `UserDto`: names, email, phone, address, birth date, profile links) print their id only, as `ContactDto(id=1)`
- an exchange with a contact (`ContactInteractionDto`) prints its id, channel and direction, never its summary
- a link between contacts (`ContactRelationDto`, `ContactRelationRequest`) prints the contact ids only, never a name or its note
- a global search hit (`GlobalSearchResultDto.Hit`) prints its id only, never a name or a passage of a note
- the `User` and `Freelance` entities, which hold the password hash and the two-factor secret, generate no `toString`
- JSON serialization is unchanged: the excluded fields are still read from requests and returned in responses

`SensitiveToStringTest` scans the `dto` and `model` packages and fails when a class holding a field named like a password (or its hash), a secret, a token, a two-factor code (a bare `code` included), a backup code or an API key prints it, when such a class, or a class nested in one, prints the email or a name, or when a class holding an email or a phone prints a name, the email, the phone or the address. `UserManagementIntegrationTest` verifies and disables two-factor authentication over HTTP with these bodies logged at `DEBUG`, and checks that the log of each request shows `TwoFactorCodeRequest()` without the code.

## External APIs

Google Maps Distance Matrix API is used for commute-time sorting. The offer collection calls the job boards and the
French government geocoding API, without keys (see [Job Offer Collection](#job-offer-collection)). Saved search emails
go through Resend, which receives the workspace's address and the email's offers, with a sending key kept in Vault
(see [Saved Search Emails](#saved-search-emails)).

Rules:

- keep the API key out of frontend code
- restrict the key in Google Cloud
- handle missing key gracefully
- do not log full addresses unnecessarily
- document API cost and quota behavior before broad use

## Job Offer Collection

The backend reads public job boards for the offer feed ([ADR 0009](./adr/0009-collecting-job-offers.md)). Its rules:

- Requests go to fixed, built-in addresses only: each reader names its site's domain, and `SiteHttpClient` refuses any
  URL, redirect included, outside that domain or with a scheme other than `http` or `https`. The only fetched address
  that comes from a user is a pasted offer link ([From An Offer Link](./features.md#from-an-offer-link)), rewritten to
  `https` without credentials, port, fragment or tracking parameters, read through the same client and counted against
  the workspace's 20 such reads an hour. On one of the built-in domains it goes to that site's reader. On any other
  site (`PostingReader`), it is read only when its host is on the public internet (`PublicHosts`): IP literals,
  single-label names and private suffixes (`.internal`, `.local`, `.svc`, `.cluster.local`, `.home.arpa` and the like)
  are refused, and so is a name resolving to a loopback, private, link-local (the cloud metadata address included),
  carrier-grade NAT (the cluster's pod range), unique-local, multicast or unspecified address. The check runs before
  every request of the reading, each redirect (kept on the page's own site) and the `robots.txt` included, so a link
  cannot be turned against the cluster's services or another network (SSRF). A name resolving differently between
  the check and the request (DNS rebinding) is stopped by the network: the API's egress policy
  (`allow-indezy-server-egress` in `infra/k8s/network-policy.yaml`) admits DNS, PostgreSQL and Keycloak in `infra`
  and the public internet only, refusing 10/8, 172.16/12, 192.168/16, 100.64/10, 169.254/16 and the loopback,
  unspecified and multicast ranges. The page read is stored nowhere.
- `robots.txt` is honoured for the `IndezyBot` product token (RFC 9309: a missing file allows everything, an
  unreachable one stops the site's run); requests wait 2 seconds per host, time out after 30 seconds, identify Indezy
  with a contact URL and stop reading past 8 MB.
- Sites that forbid crawling (LinkedIn's `robots.txt` disallows every crawler) or keep missions behind a sign-in or a
  payment are not read.
- Collected content is untrusted: descriptions are converted to plain text with jsoup (scripts, attributes and markup
  dropped), links are kept only when absolute `http`/`https`, and the frontend renders them as text and opens offer
  links with `rel="noopener noreferrer"`. The readers skip the application contacts sites publish in their own
  fields (recruiter emails and phone numbers); a description keeps only what its text says.
- XML (sitemaps) is parsed with jsoup's XML parser, which never resolves external entities.
- What a workspace does with offers (`job_offer_states`) and its saved searches (`saved_offer_searches`) are read
  and written only for the caller's workspace; `freelanceId` is accepted only when security checks are off in tests,
  a foreign one is refused with `403`, and another workspace's saved search answers `404`. Both are part of the
  account data export. The badge's background count is sent with `SILENT_HTTP_ERRORS`, so its failures raise no
  message; an expired session still signs out.
- Places are looked up on the French government geocoding API (`geo.api.gouv.fr`) with the city and postal code of an
  offer or of the profile only, never a person's address.

## File Uploads And Attachments

Backend multipart limits are:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
```

The 10 MB file limit matches an opportunity's files; avatars keep their own 512 KB limit in `UserService`. The 11 MB
request limit leaves room for multipart encoding, under the ingress's 16 MiB body limit. Both standard and
devcontainer profiles enforce it.

Opportunity files (`/api/projects/{id}/attachments`, see [ADR 0010](./adr/0010-attachments-in-postgresql.md)):

- are stored in PostgreSQL, never on a disk the web server serves, and deleted with their opportunity;
- are accepted only when their first bytes are those of a PDF, PNG, JPEG, GIF or WebP file, a Word or OpenDocument
  archive with that extension, or UTF-8 text named `.txt` or `.md`: HTML, SVG, scripts and executables are refused,
  whatever the browser declares;
- keep a name without folders or control characters, of at most 255 characters;
- are read, listed and deleted only by their workspace (other workspaces get `404`);
- are served with the type recognised at upload, `Content-Disposition: inline` for PDF and images and `attachment`
  for the rest, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox; default-src 'none'` and
  `Cache-Control: no-store, private`; the interface shows PDF and images from a `blob:` address of the content it read
  with the session's credentials.

Files are not scanned for malware: only their owner can read them back, so a file harms no one but the account that
uploaded it; a scanner is listed in the [Security Backlog](#security-backlog).

## Shared Compliance Documents

The compliance vault holds sensitive papers (identity, bank details), stored in PostgreSQL like opportunity files: PDF,
PNG or JPEG recognised from their first bytes, 10 MB at most, read and changed only by their workspace (`404` to
another), served with `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox; default-src 'none'` and
`Cache-Control: no-store, private`.

A shared link:

- carries a token of 32 random bytes (base64url) after the `#` of its address, so it never reaches a server, proxy or
  referrer log; the page sends it in the `X-Pack-Token` header, never in the path or query;
- is stored as the SHA-256 of its token only; the token is answered once, when the link is created, and is part of no
  listing or export;
- works for 1 to 30 days and until revoked; an unknown, expired or revoked token answers `404`, whatever the reason;
- serves only the documents it names that are still in the vault, always as downloads (`Content-Disposition:
  attachment`), with the headers above;
- counts each opening (count and last time), shown to its owner.

At most 20 links are open at once per workspace.

## Web Server Headers

The web image's nginx (`indezy-web/nginx.conf`) sends on every response, files included: `X-Frame-Options:
SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `X-XSS-Protection` and this Content-Security-Policy:

- scripts only from the app's own origin (`script-src 'self'`): no inline script, no `eval`, no other host. The
  production build turns off Angular's inline critical CSS, whose stylesheet loader is an inline `onload` handler;
- styles from the app, inline (Angular's component styles) and Google Fonts; fonts from the app and Google Fonts;
- images from the app, `data:`, `blob:` and HTTPS (avatars);
- requests (`connect-src`) to the app's own origin only, the API being under `/api`;
- frames from the app or a `blob:` address (a file's preview), and the browser's PDF viewer for those (`object-src
  blob:`); the service worker from the app; forms and `<base>` to the app; framed only by the app itself.

`Cache-Control` is chosen by path in a `map` with one top-level `add_header`, because a location adding any header would
drop the others: hashed files a year (immutable), the service worker and its manifest revalidated each time.

To check a policy change, build for production (`npx ng build --configuration production`), serve
`dist/indezy-web/browser` with the image's `nginx.conf`, and run a Playwright page through the main pages (with the
API mocked, `/api/auth/sso` included, and service workers blocked so the mocks apply) failing on any console message
"Refused to" or "Content Security Policy"; it was done so on 2026-10-27 (dashboard, opportunities, a mission with a PDF
preview, invoices, documents, profile, offers, calendar, the shared documents page), and removing Google Fonts from
`font-src` made it fail as expected.

## Offline Copies

The service worker keeps the latest answers of the main read endpoints in the browser's cache storage for seven days
(`ngsw-config.json`, data group `workspace-reads`), so the app reads them offline. It keeps no download (PDF, export,
file, compliance document), no session, API token or account export, nothing of the admin console or of the public
pages, and no write. Signing out deletes that data group's caches (`clearOfflineData`), so another person on the same
browser cannot read the workspace offline; a browser left signed in keeps them, like the pages it shows.

## Webhooks

A webhook makes the server post to an address the user typed, so it is guarded against server-side request forgery:

- the address must be `https://`, without credentials or fragment; its host is resolved when the webhook is written
  and again before every attempt, and refused when any of its addresses is loopback, private (RFC 1918), link-local
  (cloud metadata included), carrier-grade NAT (`100.64.0.0/10`), unique-local IPv6, multicast, unspecified or `0.0.0.0/8`;
- redirects are not followed, the connection times out after 5 seconds and the answer after 10; its body is not read;
- tests lift the address check with `indezy.webhooks.allow-private-addresses=true` to reach a local receiver, never
  in production.

Each request is signed with HMAC-SHA256 over the timestamp and the body, so a receiver can check it comes from Indezy
and refuse replays by the timestamp. The secret (32 random bytes, hex) is stored as is, since signing needs it, shown
once when the webhook is added, and never listed nor exported. Webhooks are managed under `/users`, which API tokens
cannot reach. Payloads carry ids and business fields, not contacts' details.

## Spreadsheet Exports

CSV files meant for a spreadsheet (the accountant's project summary and the invoice register) write typed text through
`CsvText.text`: text starting with `=`, `+`, `-`, `@`, a tab or a carriage return gets an apostrophe first, so a client
named `=HYPERLINK(...)` shows as text instead of running as a formula (OWASP CSV injection), and `CsvText.row` quotes
fields holding the separator, a quote or a line break. Numbers and dates the server formats are written as they are.
Exports are downloaded with `Cache-Control: no-store` and hold only the signed-in workspace's data.

## Software Supply Chain and Code Quality

Required `01-build` and `03-package` are separate from optional `02-test`. Optional manual `01-e2e`, allowed-to-fail `02-quality`, and independent `03-security` are verify jobs; standard mode leaves quality/security manual and full mode runs them automatically. Trivy scans dependencies, IaC, and secrets, retains JSON/SARIF findings for seven days, and exits nonzero on high/critical findings without becoming a deployment gate. `01-release` depends only on the required build path.

The manual release-publication job publishes immutable, checksummed JAR and SPA archives to GitLab's registries; deployment starts only after publication passes. Daemonless Kaniko reuses 30-day registry-backed image layers without privileged runner access. Credentials remain in Vault, masked project CI variables, or short-lived GitLab job credentials.

## Security Backlog

Priority items:

- scan opportunity files for malware (for example a ClamAV service) before they are kept
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

## Saved Search Emails

- An email goes to the workspace's own address only, and only for a search whose emails the workspace turned on.
- The Resend key stays in Vault and the `indezy-mail` Secret; the server sends the address, the subject and the bodies
  to Resend over HTTPS and logs neither: an email's `toString` is its idempotency key, and Resend's errors are logged by
  their name only.
- No tracking pixel and no click-tracking redirect: the offer links open the offer's page in the app, which tells the
  server an email was read once the workspace is signed in (`POST /offers/searches/{id}/emails/opened`, its own searches
  only). A mailbox's link scanner opening the links records nothing.
- The unsubscribe link carries a 256-bit random token per search, after `#`, so it reaches neither access logs nor
  Referer headers; the page sends it in the `X-Email-Token` header to `/api/public/saved-search-emails`, which only
  shows the search's name and stops its emails, and only on a button press, so a link scanner opening the page stops
  nothing. The one-click unsubscribe of the `List-Unsubscribe` header (RFC 8058) must hold the token in its address
  (`POST /api/public/saved-search-emails/unsubscribe/{token}`); that address only stops the emails. Tokens longer than
  64 characters are refused before any lookup.
- Sending stops by itself after 60 days of emails without an offer opened from them, and says so.


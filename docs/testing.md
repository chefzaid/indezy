# Testing Guide

Indezy has backend and frontend test coverage today. The test suite is not only a future goal: the repository already contains controller, service, repository, mapper, DTO, integration, guard, interceptor, component, and frontend service specs.

## Test Layers

Backend test layers:

- model tests for entity helper behavior
- DTO tests for request and response contracts
- mapper tests for MapStruct conversions
- repository tests for persistence queries
- service tests for business rules
- controller tests for HTTP behavior
- integration tests for cross-layer user flows
- utility tests such as JWT generation and validation

Frontend test layers:

- service specs for API clients
- guard specs for route access behavior
- interceptor specs for auth and error handling
- component specs for dialogs and Kanban behavior
- app component smoke coverage

The frontend currently uses Karma and Jasmine through Angular CLI. The backend uses Maven Surefire, Spring Boot test support, Spring Security test support, H2, and JaCoCo.

## Quick Commands

Run everything:

```bash
mask test
```

Run backend tests:

```bash
mask test-indezy-server
```

Run frontend tests:

```bash
mask test-indezy-web
```

Run coverage:

```bash
mask test-coverage
```

Coverage output:

- backend: `indezy-server/target/site/jacoco/index.html`
- frontend: `indezy-web/coverage/index.html`

## Direct Backend Commands

From `indezy-server/`:

```bash
./mvnw test
./mvnw test jacoco:report
./mvnw clean package
```

Windows:

```bash
mvnw.cmd test
mvnw.cmd test jacoco:report
mvnw.cmd clean package
```

Run one test class:

```bash
./mvnw -Dtest=ProjectServiceTest test
```

Run one test method:

```bash
./mvnw -Dtest=ProjectServiceTest#shouldCreateProject test
```

Checkstyle (`checkstyle.xml`, every rule at `warning` severity) runs in Maven's `validate` phase and
fails the build on any warning (`violationSeverity` is `warning` in `pom.xml`), so `test`,
`package`, `verify` and `spring-boot:run` all stop on a warning such as an unused import, an operator
wrapped at the start of a line, an inner assignment or a method with more than 7 parameters. List the
findings without compiling:

```bash
./mvnw checkstyle:check
```

SpotBugs (`effort` `Max`, `threshold` `Low`) runs in Maven's `verify` phase, after the tests and
JaCoCo's coverage check, and fails the build on any finding it reports (`failOnError` is `true` in
`pom.xml`), such as two `Long` values compared with `==` (`RC_REF_COMPARISON`).
`spotbugs-exclude.xml` leaves out only the MapStruct-generated `*MapperImpl` classes, whose findings
come from the generator rather than from our code. While backend branch coverage misses JaCoCo's 80
percent rule (see [Coverage Expectations](#coverage-expectations)), `./mvnw verify` stops at that
check before SpotBugs, so run it with `-Djacoco.haltOnFailure=false` as CI does, or list the SpotBugs
findings without running the tests:

```bash
./mvnw compile spotbugs:check
```

## Direct Frontend Commands

From `indezy-web/`:

```bash
npm test -- --watch=false --browsers=ChromeHeadless
npm run test:coverage
npm run lint
```

The package currently defines `test` and `lint`. The Mask coverage command expects `npm run test:coverage`, so keep `package.json` and `maskfile.md` aligned when changing frontend test scripts.

Run a focused Karma session during local work:

```bash
npm test
```

## Existing Backend Test Map

Representative backend tests:

- controllers: `AuthControllerTest`, `ClientControllerTest`, `ProjectControllerTest`, `InterviewStepControllerTest`, ...
- services: `ProjectServiceTest`, `DashboardStatsServiceTest`, `SeasonServiceTest`, `AuthServiceTest` (including two-factor login), `CommuteServiceTest`, `CurrentWorkspaceResolverTest` (the caller's workspace resolved once per request, the permit-all and missing-workspace cases), ...
- repositories: `ClientRepositoryTest`, `ProjectRepositoryTest`, `SourceRepositoryTest`, `FreelanceRepositoryTest`
- integration: `WorkspaceIsolationIntegrationTest` (two real accounts: isolation, seasons, deletion rules), `FullStackIntegrationTest`, `ProjectIntegrationTest`, `UserManagementIntegrationTest` (including two-factor verify and disable, a missing code answered `400`, and the log of each request, at `DEBUG`, free of the code)
- configuration and schema: `SecurityConfigTest`, `ProjectStatusMigrationTest`, `SchemaMigrationTest`, `JwtUtilTest`
- error handling: `GlobalExceptionHandlerTest` (response status, log level and absence of rejected values, read with `OutputCaptureExtension`)
- auth logging: `AuthControllerTest` (log level of each rejection and fault, without the email) and `AuthLoggingIntegrationTest` (registration, duplicate registration and wrong passwords up to the lockout over HTTP, with Spring MVC and the test client logging the bodies at `DEBUG`, and the whole log free of the email, the name and the passwords)
- password length: `PasswordMaxBytesTest` (the 72-byte BCrypt limit counted in UTF-8 bytes on registration and password change, with accented and emoji passwords at and over the limit, the 6-character registration minimum, and the 8-character minimum of the new password, with only `@NotBlank` for a missing one and no minimum for the current one) and `PasswordLengthIntegrationTest` (a validation error over HTTP instead of the former `409`, no account created, and a 72-byte password that registers, signs in and can be set as the new password; a new password under 8 characters or a confirmation that differs answers `400` and the current password still signs in)
- secrets in `toString`: `SensitiveToStringTest` (scans the `dto` and `model` packages; no class prints a password, password hash, token, two-factor secret or code (a bare `code` field included), backup code or API key, nor, in such a class or one nested in it, the email or a name)

Backend test data lives under `indezy-server/src/test/resources/`:

- `application-test.properties`
- `test-data.sql`
- `project-test-data.sql`
- `cleanup-test-data.sql`
- `cleanup-project-test-data.sql`

## Existing Frontend Test Map

Representative frontend specs:

- `api-services.spec.ts`: one table-driven case per HTTP call (verb, URL, parameters, body) of the client, contact, freelance, interview-step, project, source and user-management services
- `auth.service.spec.ts`, `auth.guard.spec.ts`, `auth.interceptor.spec.ts`, `error.interceptor.spec.ts`
- `season.service.spec.ts` and `project-filter.util.spec.ts`
- `kanban-board.component.spec.ts`, `interview-step-dialog.component.spec.ts`, `dashboard-reminders.component.spec.ts`
- `login.component.spec.ts`: the redirect of a signed-in visitor, sign-in back to the requested page, the authenticator-code step, the message for each failure cause, field errors, the Keycloak hand-off, and the French and English email placeholder
- `register.component.spec.ts`: field errors (including the 72-byte password limit), submission, the conflict-only "account already exists" message, and the French and English accessible names of the password visibility buttons
- shared helpers: `app-locale.spec.ts`, `form-payload.spec.ts`, `form-errors.spec.ts`, `password-max-bytes.spec.ts`, `theme.service.spec.ts`

## Testing Strategy

### 1. Start narrow

For small backend changes, start with the service, mapper, or controller class closest to the behavior. For frontend changes, start with the service or component spec closest to the changed code.

### 2. Add integration coverage when behavior crosses boundaries

Use integration tests when a change depends on persistence, DTO mapping, controller behavior, and service rules working together.

Good candidates:

- registration and login
- creating a project with client/source relationships
- moving a project through Kanban status changes
- scheduling an interview step
- filtering project lists

### 3. Keep tests user-outcome oriented

Prefer test names that describe the behavior, not the implementation detail.

Good:

```text
creates a project for a freelance with a final client
adds bearer token to authenticated API requests
redirects anonymous users to login with returnUrl
```

Avoid:

```text
calls method
sets variable
```

### 4. Test failure paths

For APIs and services, cover:

- missing entity
- duplicate or invalid input
- unauthorized or unauthenticated behavior where applicable
- invalid state transition
- external API fallback, especially commute calculations without an API key

### 5. Keep fixtures small

Seed only the state required for the scenario. Large shared fixtures make failures harder to read and can hide ordering dependencies.

### 6. Prefer accessible frontend selectors

Playwright selectors should prefer roles, labels, and user-visible text over CSS structure. Component tests should follow the same spirit where practical.

## Coverage Expectations

The Maven JaCoCo configuration records an 80 percent instruction and branch target. CI also computes combined backend/frontend line coverage against an 80 percent policy and publishes a failed JUnit policy result when it is missed; that report does not fail the build job or block release.

Measured on 14 October 2026 with the repository's configured reporters: backend
89.59 percent lines / 87.82 percent instructions / 71.74 percent branches (JaCoCo);
frontend 90.96 percent lines / 89.74 percent statements / 87.65 percent branches (Karma).
The suites contain 471 backend tests and 284 frontend tests. Line coverage exceeds
the coding workflow's 85 percent target in both layers; backend branch coverage
still misses Maven's separate 80 percent verification rule. These reports use the
existing instrumentation configuration; the Karma figures cover modules loaded by
the unit suite, not every lazy-loaded page.

When adding risky behavior, treat the 80 percent report as a prompt for targeted tests rather than a release switch.

## Playwright E2E

Playwright is a first-class part of the optional manual E2E job. Run it locally with
`cd indezy-web && npm run test:e2e`. The configuration starts the Angular development
server automatically and retains HTML output and failure traces. The suite checks
the public authentication forms and exact-rate dashboard chart in French and English,
including switching seasons, and saving the inactivity preference before checking
its effect on Kanban highlighting. Target-rate scenarios cover profile validation,
saving and clearing the target, defaults for new seasons, and comparison with accepted
rates, including the French phone layout. Browser tests intercept API responses and
save chart and preference screenshots; backend aggregation, preference validation,
season defaults and workspace isolation have separate Java tests.
Password scenarios check, in French and English, that the register and password-change
forms refuse a password over 72 bytes (37 accented letters) with the translated limit,
accept one of exactly 72 bytes, and that a rejected registration other than a conflict
is not reported as an existing account. The password-change form also refuses a new
password under 8 characters with the translated minimum, and a server rejection of the
change shows the translated error and keeps the typed values. They also find the
register page's password visibility buttons by their translated accessible name and
check that `aria-pressed` follows the toggle, and read the login page's email placeholder
in each language.
Shared-link scenarios also check copying from project pages and Kanban cards,
reopening after reload or password login, malformed links, and unavailable projects.
Keyboard scenarios cover project creation (including the translated tech stack
placeholder), search focus with collapsed phone filters,
translated help, moving a focused Kanban card and cancelling its loss-reason dialog,
while preserving input and dialog keyboard behavior.
Extend the suite with registration, project creation, Kanban movement, filtering,
and interview scheduling as those workflows stabilize.

## CI Expectations

GitLab CI exposes ordered build, test, package, E2E, quality, security, release, deploy, and version jobs.

The current CI verification and reporting includes:

- backend unit and integration tests
- backend Checkstyle, failing the build on any warning (also in `01-build`, whose `package` runs it)
- backend SpotBugs, failing `02-test` on any finding (`01-build` packages with `-DskipTests` and does not reach `verify`)
- frontend headless tests
- frontend lint
- backend package build
- frontend production build
- Playwright browser verification in the optional manual E2E job
- non-blocking SonarQube analysis on `main`
- non-blocking dependency vulnerability audit reports
- independent Trivy dependency, IaC, and secret scans with JSON and SARIF artifacts
- seven-day JUnit, coverage, browser, and compiled-output job artifacts
- immutable JAR/SPA archives and checksums in the Generic Package Registry

Required `01-build` and `03-package` own compilation and image validation; optional `02-test` owns unit-test, SpotBugs and coverage reporting. Its `./mvnw clean verify -Djacoco.haltOnFailure=false` reaches SpotBugs despite the JaCoCo branch rule, and a finding fails the job like a failing backend test: the frontend lint, tests and coverage policy of that job do not run, and release is not blocked. Optional manual `01-e2e` runs Playwright. Default-branch `02-quality` runs automatically; standard mode leaves independent `03-security` manual, while full mode runs both automatically as non-blocking reports. Security has no dependency on quality and retains Trivy JSON/SARIF output. Release exists only on `main`, requires the successful build path, and publishes packages/images and Git state; deploy requires that release job and then waits for Argo CD plus both health checks. `PIPELINE_MODE=full` also automates release and deploy while E2E remains manual.

## Troubleshooting

### ChromeHeadless is unavailable

Install Chrome or configure the CI image with a compatible browser. The Mask command invokes:

```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

### A backend test passes alone but fails in the suite

Look for shared database state. Prefer explicit setup and cleanup SQL, or isolate the test with transactional rollback where possible.

### A frontend HTTP spec fails with an unexpected request

Check that the service URL uses `environment.apiUrl` and that every expected request is flushed. Also verify `httpMock.verify()` is present in `afterEach`.

### Coverage command fails on frontend

Check whether `test:coverage` exists in `indezy-web/package.json`. The current Mask command expects it, while the package script list may need to be brought into sync.

### Health checks fail in local status output

`mask status` checks `/api/actuator/health`. The Kubernetes manifests also reference that path. If Actuator is not wired into the backend build, this check can fail even when the server port is open.

## Related Guides

- [Development](./development.md)
- [Data Model](./data-model.md)
- [Security](./security.md)
- [Operations](./operations.md)

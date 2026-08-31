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

- `AuthControllerTest`
- `ClientControllerTest`
- `FreelanceControllerTest`
- `InterviewStepControllerTest`
- `ProjectControllerTest`
- `SourceControllerTest`
- `AuthServiceTest`
- `ClientServiceTest`
- `FreelanceServiceTest`
- `InterviewStepServiceTest`
- `ProjectServiceTest`
- `SourceServiceTest`
- `ClientRepositoryTest`
- `FreelanceRepositoryTest`
- `ProjectRepositoryTest`
- `SourceRepositoryTest`
- `FullStackIntegrationTest`
- `ProjectIntegrationTest`
- `UserManagementIntegrationTest`
- `JwtUtilTest`

Backend test data lives under `indezy-server/src/test/resources/`:

- `application-test.properties`
- `test-data.sql`
- `project-test-data.sql`
- `cleanup-test-data.sql`
- `cleanup-project-test-data.sql`

## Existing Frontend Test Map

Representative frontend specs:

- `auth.service.spec.ts`
- `client.service.spec.ts`
- `contact.service.spec.ts`
- `freelance.service.spec.ts`
- `interview-step.service.spec.ts`
- `project.service.spec.ts`
- `source.service.spec.ts`
- `user-management.service.spec.ts`
- `user-management.integration.spec.ts`
- `auth.guard.spec.ts`
- `auth.interceptor.spec.ts`
- `error.interceptor.spec.ts`
- `kanban-board.component.spec.ts`
- `step-action-dialog.component.spec.ts`
- `step-schedule-dialog.component.spec.ts`

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

Current measured coverage (June 2026): backend ~83 percent instruction / ~64 percent branch overall (JaCoCo, includes generated MapStruct mappers and Lombok code); frontend ~93 percent statements (Karma). These historical figures explain why the report can currently show a failed policy result without suppressing delivery.

When adding risky behavior, treat the 80 percent report as a prompt for targeted tests rather than a release switch.

## Playwright E2E

Playwright is a first-class part of the optional manual E2E job. Run it locally with `cd indezy-web && npm run test:e2e`. The configuration starts the production-like Angular server automatically, uses deterministic route interception for the critical authentication flow, and retains HTML output and failure traces. Extend the suite with registration, project creation, Kanban movement, filtering, and interview scheduling as those workflows stabilize.

## CI Expectations

GitLab CI exposes ordered build, test, package, E2E, quality, security, release, deploy, and version jobs.

The current CI verification and reporting includes:

- backend unit and integration tests
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

Required `01-build` and `03-package` own compilation and image validation; optional `02-test` owns unit-test and coverage reporting. Optional manual `01-e2e` runs Playwright. Standard mode leaves `02-quality` and independent `03-security` manual, while full mode runs both automatically as non-blocking reports. Security has no dependency on quality and retains Trivy JSON/SARIF output. Release exists only on `main`, requires the successful build path, and publishes packages/images and Git state; deploy requires that release job and then waits for Argo CD plus both health checks. `PIPELINE_MODE=full` also automates release and deploy while E2E remains manual.

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

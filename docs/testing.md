# Testing Guide

The backend is tested with JUnit, Spring Boot test support and H2, the frontend with Karma and Jasmine, and the
browser flows with Playwright. CI runs them as described in the [Deployment Guide](./deployment.md).

## Running Tests

From the repository root:

```bash
mask test               # both applications, with coverage
mask test-indezy-server # ./mvnw verify
mask test-indezy-web    # npm run test:ci
```

Coverage reports: `indezy-server/target/site/jacoco/index.html` and `indezy-web/coverage/index.html`.

In `indezy-server` (`mvnw.cmd` on Windows):

```bash
./mvnw verify                                   # Checkstyle, tests, JaCoCo's 80 percent check, SpotBugs
./mvnw test -Dtest=ProjectServiceTest           # one class
./mvnw test -Dtest=ProjectServiceTest#create_WithValidData_ShouldCreateProject
./mvnw checkstyle:check                         # Checkstyle findings, without compiling
./mvnw compile spotbugs:check                   # SpotBugs findings, without the tests
```

Checkstyle runs in Maven's `validate` phase and fails the build on any warning, so `test`, `package` and
`spring-boot:run` stop on one too. SpotBugs runs in `verify` and fails on any finding; `spotbugs-exclude.xml` leaves
out only the MapStruct-generated `*MapperImpl` classes.

In `indezy-web`:

```bash
npm test                                        # Karma in watch mode
npm run test:ci                                 # once, in headless Chrome
npm run test:ci -- --include=src/app/services/offer/offer.service.spec.ts
npm run test:e2e                                # Playwright
npm run test:e2e -- e2e/offers.spec.ts
npm run test:node                               # browser extension and build scripts, with Node's test runner
npm run lint
```

## Where Tests Live

- Backend tests are under `indezy-server/src/test/java`, in the package of the class they test, named `<Class>Test`.
  Tests that go through HTTP, security and the database are `*IntegrationTest` classes, most in the `integration`
  package. Spring tests run on H2 with the `test` profile (`application-test.properties`), which turns the offer
  collection and geocoding off and sets `indezy.security.permit-all=true`; tests of access rules set it back to
  `false`.
- Tests of what one account may see of another extend `TwoAccountsIntegrationTest`: two real accounts registered
  through the API, calling it with their tokens.
- The job board readers read hand-written samples of each site's pages under `src/test/resources/offers/` through
  `FakeFetcher`.
- Frontend specs sit beside the code they test as `*.spec.ts`; `api-services.spec.ts` checks each HTTP call of the
  simple API services in one table.

A few suites enforce a rule for all new code:

- `WorkspaceIsolationIntegrationTest`: one account never reads or changes another's records. Add a case for a new
  workspace-owned resource.
- `SensitiveToStringTest`: no class of the `dto` and `model` packages prints a password, token, two-factor secret or
  code, backup code or API key, nor the email or names beside them (see
  [Development](./development.md#adding-backend-features)).
- `OpenApiDocumentationIntegrationTest`: every operation has a summary, its success status and its error answers, and
  every example under `src/main/resources/openapi/examples/` fits its schema.
- `EnumCheckConstraintsTest` and `ColumnLengthsTest`: a new enum value or a longer column reaches a schema created
  before it.
- `app.routes.spec.ts`: every page loads lazily.
- `i18n.spec.ts`: `en.json` and `fr.json` have the same keys and no empty value.
- `coverage-sources.spec.ts`: imports every source file listed in `src/app/coverage-sources.ts`, so the coverage report
  counts files no spec imports. Run `npm run coverage-sources` after adding or removing a file; `npm run lint` fails
  while the list is out of date.

## Coverage

`./mvnw verify` fails when JaCoCo's instruction or branch coverage falls under 80 percent. CI's `02-test` runs the same
suites with that check reporting only (`-Djacoco.haltOnFailure=false`), then checks the combined backend and frontend
line coverage against 80 percent and reports a miss as a failed JUnit result, without blocking delivery.

## Playwright E2E

The specs are in `indezy-web/e2e`, one per feature. The configuration starts the Angular development server (or reuses
the one running), waits up to 10 seconds per assertion, and keeps the HTML report and the traces of failed tests.

No spec needs the backend: each intercepts the API with `page.route`. `e2e/helpers/workspace.ts` stores a browser-only
session (`mockWorkspaceSession`), `e2e/helpers/workspace-data.ts` answers every call with a small workspace (calls it
does not list answer an empty list), and `e2e/helpers/offer-feed.ts` serves the offer feed. Most specs run on a desktop
and on a phone, many in both languages; several audit the page or dialog they open with `@axe-core/playwright`.
`accessibility.spec.ts` runs the WCAG 2.1 A and AA rules on every main page, in the light theme on a desktop and the
dark theme on a phone, and expects no violation.

## Reader Canaries

The readers are tested on hand-written samples of each site's pages, which a site redesign does not change, so the
feed could quietly empty. `ReaderCanaryTest` reads every site the feed reads, live, through its reader and the
collection's polite client, up to the first page holding a freelance offer (five pages at most), and fails naming each
site whose freelance offers no longer yield a title, a link and a date (only title and link for the sites that date no
offer), or that answers with an error. The pages read from a failing site are written to
`indezy-server/target/reader-canary/<SITE>/` to update the test samples.

The test carries the JUnit tag `canary`, which `mvn test` and `mvn verify` leave out (`test.excludedGroups` in
`pom.xml`). Run it locally with:

```bash
cd indezy-server
./mvnw test -Dgroups=canary -Dtest.excludedGroups=none
```

CI runs it in `04-reader-canary`, only in a pipeline whose `READER_CANARY` variable is `true`, started by a schedule or
by hand from the GitLab interface; every other job skips such a pipeline, so it never builds, releases or deploys. Its
JUnit report and samples are kept 30 days. To set the weekly run up, create a pipeline schedule on `main` (CI/CD →
Schedules, for instance `0 6 * * 1` in `Europe/Paris`) with the variable `READER_CANARY` set to `true`.

## Scheduled E2E Runs

On a push or merge request the Playwright suite (`01-e2e`) waits to be started by hand. A pipeline whose
`PLAYWRIGHT_SCHEDULE` variable is `true`, started by a schedule or by hand, builds, tests and packages, then runs the
suite on its own and fails when it fails; the quality and security reports, the release and the deploy skip such a
pipeline. To run it every night, create a pipeline schedule on `main` (CI/CD → Schedules, for instance `30 2 * * *` in
`Europe/Paris`) with the variable `PLAYWRIGHT_SCHEDULE` set to `true`. Its report is kept 7 days, as on any pipeline.

## Search Load Test

`OfferSearchLoadTest` checks the offer feed stays fast with a production-sized catalogue. It collects 50,000 offers into
PostgreSQL through the real `OfferNormalizer` and `OfferStore` (`LoadTestOffers` writes the same ones on every run),
computes the rate medians, and creates a workspace with a profile and offers it saved, hid, read and noted. It times
the feed's common searches and side requests alone, then sends them 20 at a time over HTTP, 400 searches in all,
writes the timings to `indezy-server/target/load-test/report.md`, and fails when a request is not answered or when the
95th percentile of the searches reaches 300 ms.

The test carries the JUnit tag `load`, which `mvn test` and `mvn verify` leave out. It needs a disposable PostgreSQL;
the offers are kept there for the next run, so only the first run collects them (about three minutes on two cores).
`-Dindezy.load.rounds=2` sends 40 searches instead of 400. Locally, on the loopback interface only:

```bash
docker run -d --name indezy-load-pg -p 127.0.0.1:55432:5432 --cpus=2 --memory=1g \
  -e POSTGRES_USER=indezy -e POSTGRES_PASSWORD=indezy -e POSTGRES_DB=indezy_load postgres:18.6-bookworm
cd indezy-server
./mvnw test -Dtest=OfferSearchLoadTest -Dgroups=load -Dtest.excludedGroups=none -Djacoco.skip=true
docker rm -f indezy-load-pg
```

`INDEZY_LOAD_DATABASE_URL`, `INDEZY_LOAD_DATABASE_USER` and `INDEZY_LOAD_DATABASE_PASSWORD` point it at another
database. Never point it at a database holding real data: it adds its offers and accounts there. The test's
connections take the planner settings the database setup hook gives the application's role (`jit = off`,
`random_page_cost = 1.1`, see [Architecture](./architecture.md#offer-search-at-scale)).

## Troubleshooting

- **Karma cannot find Chrome**: it launches the browser `CHROME_BIN` names. Install Chromium with
  `npx playwright install chromium` in `indezy-web`, then
  `export CHROME_BIN="$(node -e "console.log(require('playwright-core').chromium.executablePath())")"`.
- **A backend test passes alone but fails in the suite**: look for database state another test left; set it up and
  clean it in the test, or roll it back with `@Transactional`.
- **A frontend HTTP spec fails with an unexpected request**: check the service builds its URL from
  `environment.apiUrl` and that the spec flushes every request and calls `httpMock.verify()`.

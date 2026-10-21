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
- error handling: `GlobalExceptionHandlerTest` (response status, headers, log level and absence of rejected values, read with `OutputCaptureExtension`, including the Spring MVC request errors that keep their 4xx status), `GlobalExceptionHandlerMvcTest` (a probe controller on which Spring MVC raises a missing header, a parameter constraint, a missing parameter and an unsupported method, each reaching its handler) and `ContactControllerTest` (`POST /contacts` with a `text/plain` body answers `415`, and a client accepting only CSV `406`, both logged at `WARN`)
- auth logging: `AuthControllerTest` (log level of each rejection and fault, without the email) and `AuthLoggingIntegrationTest` (registration, duplicate registration and wrong passwords up to the lockout over HTTP, with Spring MVC and the test client logging the bodies at `DEBUG`, and the whole log free of the email, the name and the passwords)
- password length: `PasswordMaxBytesTest` (the 72-byte BCrypt limit counted in UTF-8 bytes on registration and password change, with accented and emoji passwords at and over the limit, the 6-character registration minimum, and the 8-character minimum of the new password, with only `@NotBlank` for a missing one and no minimum for the current one) and `PasswordLengthIntegrationTest` (a validation error over HTTP instead of the former `409`, no account created, and a 72-byte password that registers, signs in and can be set as the new password; a new password under 8 characters or a confirmation that differs answers `400` and the current password still signs in)
- activity reports: `FrenchPublicHolidaysTest` (Easter dates, the eleven holidays, business days per month), `ActivityReportServiceTest` (blank month calendar, saving days with totals and amount, accepted missions only, days outside the month or twice, locked statuses, submit and renew issuing a hashed token, reopening), `ActivityReportValidationServiceTest` (the public view without ids or money, expired or malformed tokens, approval and rejection rules, PDF file names and the ESN template), `ActivityReportPdfRendererTest` (daily table in French and calendar grid in English read back with PDFBox text extraction, the location split, the electronic approval stamp in Paris time, unprintable characters and a second page), `ActivityReportControllerTest` (statuses, validation errors, `409` for a locked report, the PDF attachment headers, the `X-Report-Token` header) and `ActivityReportIntegrationTest` (two real accounts and an anonymous validator over HTTP: record, submit, approve, reject and resubmit, reopen revoking the link, workspace isolation, project deletion, the data export, and a log free of the token with Spring MVC at `DEBUG`)
- offer feed: the readers' tests (`FreeWorkReaderTest`, `CollectiveReaderTest`, `CherryPickReaderTest`, `LeHibouReaderTest`, `YeetsReaderTest`, `WorkDispoReaderTest`, `StructuredSitesReaderTest` for Next Mondays, Mindquest, Freelance Factory and LittleBig Connection, `DetailPagesReaderTest` for the detail-page template and the offer each site's page addresses name, `HtmlSitesReaderTest` for Silkhom, LeGratin, Freelance-Informatique, Espace Freelance, Beyond Skills IT, Koïno and Sixième Homme with the list-page paging) read hand-written samples of each site's answer under `src/test/resources/offers/` through `FakeFetcher` (freelance offers only, the dropped publishers, every mapped field, paging and stops, only unknown pages read, ten per batch, pages gone handed on as closed); `SitemapsTest`, `PageFactsTest`, `OfferFingerprintTest` (copies told by publisher or opening text), `OfferRelocationTest` and `OfferNormalizerTest` (stored offers whose lookup failed located again, what they knew kept), `PlaceNamesTest` (the commune of place names with an arrondissement, a cedex, remote words, a list of places, a district or a short form), `OfferMatchTest` (skills as whole words, short skills from the skill list, rate, place and availability fits, a start as soon as possible or past read as today, scores over what the profile states), `OfferMarketServiceTest` (interpolated quartiles, the share reaching the target, rate bands widened to 16 at most with open bands for rare rates, the remote split and the hybrid offers' remote days, publishers under their most used spelling, the last 30 days in Paris time), `OfferMarketQueriesTest` (the feed's offers once per mission whatever its sort, skills grouped by case and counted once per offer), `JobBoardSourcesTest` (every board a workspace has no source for added once, LinkedIn as social media, matching services without the listing flag, the workspaces created before seeded on startup), `OfferSiteHealthServiceTest` (statuses with a stale run let go, counts, failing sites first, the workspace's excluded sites, excluding once and bringing back, the kind and access of the sites not read, visits recorded and forgotten), `OfferSiteRefresherTest` (a reading in the background once claimed, the wait told on a refused request, collection off), `OfferPublisherBlockServiceTest` (a publisher blocked once whatever the spelling, names without words, the limit, another workspace's block), `OfferPublisherBlockControllerTest`, `JobPostingsTest` (schema.org postings in JSON-LD, graphs and arrays), `FrenchDatesTest` (numeric, ISO and written French dates, the closest year); `OfferTextTest` (daily rates written as text, remote policies, the remote policy production descriptions state and the passing mentions left aside, seniorities, durations in fields and in text, duration fields, titles of salaried jobs), `HtmlTextTest` (paragraphs and list items kept, markup dropped, HTML a site escaped twice read twice), `JsonTest`, `RobotsRulesTest` (RFC 9309 groups and longest match), `SiteHttpClientTest` (a local HTTP server: the user agent, the delay between requests, gzip, charsets, every redirect status kept on the site and redirect loops, robots.txt with its redirects and its 12-hour copy, the size cap), `GeoLocatorTest` (a local server standing in for the geocoding API, the cache and the name-only retry), `OfferNormalizerTest`, `OfferStoreTest` (skips recorded once, copies grouped and groups merged, publishers of stored offers keyed batch after batch, descriptions stored with markup escaped twice cleaned, the remote policy of offers stored without a work mode read), `OfferCollectorTest` (page stops, budgets, failures, skipped offers, one offer read from its link and stored, or why not), `OfferLinkReaderTest` (links read without fragment, tracking or trailing slashes, refused schemes, credentials and ports, sites told by domain, stored offers found by identifier then by the end of their address, encoded or not, with the parameters naming them, a page read alone after the workspace's count, a reading asked for, waited for or told when possible again), `OfferSiteRunsTest` (claims, backoff, the metrics of each run and when a site can be read on request again), `OfferMetricsTest` (runs, offers, durations and geocoding timeouts counted; each site's health read from the statuses at most once a minute), `OfferRetentionTest`, `FrenchGeographyTest`; `JobOfferSpecificationsTest` and `JobOfferRepositoryTest` run every filter, view, sort and query on H2 (a mission on several sites listed once, at its earliest copy the search finds, with the workspace's states shared across copies; the database's match sort and filter agree with the cards' scores for several profiles, with and without an availability date; a site a workspace excludes left out of its feed only; what each site brought over a day and a week; a site read on request once every quarter of an hour and never beside a run; a blocked publisher's offers left out of the feed whatever their spelling; offers found by the words of a workspace's note only for that workspace; the applied view of each workspace; the application read with the states; offers applied to kept past retention; offers stored before publishers had a key; the open offers stating a rate with their first skill); `OfferSearchServiceTest`, `OfferStateServiceTest` (notes kept stripped, a blank one removed, exported), `OfferImportServiceTest` (the note added to the opportunity's notes, the draft creating nothing, an opportunity added as corrected in the form), `OfferLinkServiceTest` (sites named for another site's link, the draft of an offer found, the opportunity of a mission in the pipeline, 20 page reads an hour per workspace), `OfferBulkServiceTest` (each offer changed once, offers no longer collected left out, each mission added once and the ones already in the pipeline reported), `OfferApplicationServiceTest` (a mission imported once with its application and follow-up steps in the interface's language, an opportunity already in the pipeline kept, a late follow-up planned for the next morning, no later day and no second application, today read in Paris, an application forgotten on every copy), `OfferSiteControllerTest` (the health page, the exclusion switch, reading on request with `429` and `Retry-After`), `OfferControllerTest` (query-string binding and validation, a note over 2,000 characters refused, an application recorded and forgotten, its malformed requests refused, actions on several offers and their limits, a link and its validation, an import as corrected and its checks), `SavedOfferSearchServiceTest` (limits, names, a workspace's own searches only), `OfferRateBenchmarkServiceTest` (the median of each skill in each region and in France, one per mission, ranges read as their middle, too few offers left out, the spelling written most, an offer compared in its region else in France, medians kept 10 minutes), `OfferSearchCandidatesTest` (the job title, skills alone and paired and each way of working at the target rate, what a profile without city, title or rate allows, the pipeline's skills and median rate), `OfferSearchSuggestionServiceTest` (searches finding offers best first, the saved and dismissed ones left out, counted again after 10 minutes or a profile change, the skill pair offers ask for most, the city and region named, filters told apart whatever their order, sort or page, dismissals kept once and 200 at most, exported); `OfferSearchIntegrationTest` (saved searches with their new offers and badge, kept from the other workspace; searches suggested from a real profile near Paris, left out once saved or dismissed, the dismissals exported, and another workspace told what to fill in) and `OfferFeedIntegrationTest`, both on `OfferIntegrationTestSupport` (two real accounts over HTTP: saving, hiding, importing with the site as source, a deleted project freeing its offer, the job board sources a new workspace starts with, distances from the profile's city, the data export, each workspace's profile match and its availability date set on the profile, the market snapshot of each workspace's feed with its target share, a site one workspace excludes and the other keeps, a publisher one workspace blocks, each workspace's visits of the sites to visit, an application with its steps, refused twice or for a later day, listed in its workspace's applied view only and forgotten, and several offers hidden and added to the pipeline at once, an offer's rate compared with the median of its skill computed from stored offers, an offer link drafting an opportunity that is created only once saved as corrected, then opened for its workspace only, another workspace refused its client, other sites' links and offers not collected)
- secrets in `toString`: `SensitiveToStringTest` (scans the `dto` and `model` packages; no class prints a password, password hash, token, two-factor secret or code (a bare `code` field included), backup code or API key, nor, in such a class or one nested in it, the email or a name)

Backend test data lives under `indezy-server/src/test/resources/`:

- `application-test.properties`
- `test-data.sql`
- `project-test-data.sql`
- `cleanup-test-data.sql`
- `cleanup-project-test-data.sql`
- `offers/`: samples of each job board's answer, fictional, for the reader tests

## Existing Frontend Test Map

Representative frontend specs:

- `api-services.spec.ts`: one table-driven case per HTTP call (verb, URL, parameters, body) of the client, contact, freelance, interview-step, project, source and user-management services
- `auth.service.spec.ts`, `auth.guard.spec.ts`, `auth.interceptor.spec.ts`, `error.interceptor.spec.ts`
- `season.service.spec.ts` and `project-filter.util.spec.ts`
- `kanban-board.component.spec.ts`, `interview-step-dialog.component.spec.ts`, `dashboard-reminders.component.spec.ts`
- `login.component.spec.ts`: the redirect of a signed-in visitor, sign-in back to the requested page, the authenticator-code step, the message for each failure cause, field errors, the Keycloak hand-off, and the French and English email placeholder
- `register.component.spec.ts`: field errors (including the 72-byte password limit), submission, the conflict-only "account already exists" message, and the French and English accessible names of the password visibility buttons
- activity reports: `activity-calendar.util.spec.ts` (month weeks starting on Monday, weekends, mission dates, brushes, filling business days, totals), `activity-calendar.component.spec.ts` (shading, French day descriptions, editable buttons), `activity-report-card.component.spec.ts` (opening month within the mission, editing, saving, submitting with the link to copy or email, renewing, approved and rejected months, reopening, month navigation, PDF), `activity-report-validation.component.spec.ts` (token after `#`, invalid links, name and reason rules, answers, conflicts, PDF, language switch) and `client-form.component.spec.ts` (the client's activity report template)
- offer feed: `offer-query.spec.ts` (the feed's address read and written, malformed values ignored), `offer.service.spec.ts`, `relative-time.spec.ts` (moments and calendar days), `offer-card.component.spec.ts` (facts, partial rates and places, the match badge and its details, the profile's skills first, actions, imported offers, a note added, edited and deleted, an unchanged note not saved, the application badge and its menu items, the offer's page and link from its menu, the details toggle and selection left out, the rate against the market above, below or at it, in a region or in France), `offer-apply-dialog.component.spec.ts` (an application today with a follow-up, a past one without, a late follow-up for tomorrow, a later day and an empty follow-up refused), `offer-filters.component.spec.ts` (typed fields applied after a pause, lists at once, skills and suggestions, distance without a city, the match filter without profile skills) `offer-market.component.spec.ts` (rates, the target's share and band, the remote split, rankings, a skill click, fetching again for other filters only, failure and retry, offers stating nothing), `offer-bar-chart.component.spec.ts` (the highlighted bar, redrawing, the accessible description), `blocked-publishers.component.spec.ts` (listing, hiding by name once, showing again and undoing on failure, folding as the server does), `offer-sites-to-visit.component.spec.ts` (sites to see in person and matching services grouped with what they take, a visit recorded on opening or marking, forgotten, put back on failure), `offer-sites.component.spec.ts` (statuses, facts, quiet boards, left-out boards, reading on request and its refusals, the feed switch undone on failure, collection off) and `dashboard-offers.component.spec.ts` (best unseen matches, the newest without profile skills, each offer linked to its page, nothing new, a failure hiding it), `offer-page.component.spec.ts` (the whole offer marked seen with its link copied, actions shown at once and put back on failure, a hidden offer kept on its page, an expired offer with or without its date, an offer no longer collected and an address naming none, a failure and retry, another offer's address followed), `offer-share.service.spec.ts` (the page's full address copied, a refused copy), `offer-format.spec.ts` (rates and places as cards write them), `offer-selection-bar.component.spec.ts` (the count, the actions the view allows, the whole page or none), `offer-bulk-actions.spec.ts` (selecting, a change shown at once and put back on failure, hidden offers taken out with an undo, nothing without a selection, fifty offers per import, offers already in the pipeline), `offer-page.spec.ts`, `offer-search-describe.spec.ts` (each filter of a suggested search in words, in both languages, names cut at 80 characters), `offer-search-suggestions.component.spec.ts` (suggestions in words with their counts and the empty profile fields, a preview, saving under the offered name, a name taken, dismissing at once and put back on failure, nothing to suggest, a failure and retry), `offer-saved-searches.component.spec.ts` (the suggestions opened by themselves while nothing is saved until closed, opened from the profile, a saved suggestion joining the saved searches), `offer-link-field.component.spec.ts` (an offer found handed to the form, an expired one pointed out, the opportunity of a mission in the pipeline, the site read and looked at again every 5 seconds for 2 minutes, each refusal, an empty field, `Enter`, one lookup at a time, the cursor put in the field), `project-form.component.spec.ts` (an ESN client shown, a new opportunity in the running season, the offer link field on new opportunities only, the form filled in from an offer with the stage kept and the ESN to create, saved through the import, the workspace's client and source kept, a mission added meanwhile, unlinking), `notification.service.spec.ts` (an action instead of Close), `offer-list-keys.directive.spec.ts` (moving between offers, acting on the focused one from any of its buttons, its note with `N`, its selection with `X`, typing and modified keys left alone, the focus handed on when an offer leaves), `shortcut-help.component.spec.ts`, `offer-feed.component.spec.ts` (address-driven search, views, sorts, the match sort and the profile hint, the market snapshot remembered open and its skill filter, the job boards link with failing boards, excluded sites left out of the counts and filters, a blocked publisher's cards leaving the feed but not the saved view, a note shown at once and put back on failure, an application recorded in the interface language, a second one or a failure explained, one taken back out of the applied view and put back on failure, paging, saving, hiding, details, import and its conflicts, failures)
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

Measured on 20 October 2026 with the repository's configured reporters: backend
94.80 percent lines / 94.75 percent instructions / 84.61 percent branches (JaCoCo);
frontend 95.26 percent lines / 94.56 percent statements / 91.61 percent branches (Karma).
The suites contain 860 backend tests, 428 frontend tests and 31 Playwright scenarios. Line coverage exceeds
the coding workflow's 85 percent target in both layers, and backend branch coverage
meets Maven's separate 80 percent verification rule. These reports use the
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
Activity report scenarios (`e2e/activity-reports.spec.ts`), in English and in French on a phone, fill a mission's
business days, record a half day remote, check the totals, send the month for approval and copy the link, then open it
in a separate browser without a session: the token reaches the API in the `X-Report-Token` header and never in a URL,
a rejection needs a reason, and the approval is shown. They also check the invalid-link page and its language switch,
and save an ESN's activity report template from the client form. They save the calendar and approval screenshots.
Offer feed scenarios (`e2e/offers.spec.ts`) browse the feed in English on a desktop, read an offer's match with the
profile and its highlighted skills, open the market snapshot (median, quartiles, the target's share, the rate
histogram's description, the remote split) and filter on a skill it names, sort by best match and keep the offers matching the profile, type a keyword and a minimum
rate that reach the address and the API, see the badge on Offers and a saved search's new offers, save the search
under a name, save an offer, follow the "Also on" link of a copy, open its details, add it to the pipeline and see
the link to its opportunity, hide another, switch to the saved view and open the saved search; in French on a phone they see the invitation to add skills to the profile, open the collapsed filters,
read the hint for a missing city, narrow the feed to full remote, clear the filters from the empty state and open the market snapshot without a
horizontal scroll. Job board scenarios follow the feed's link to **Job boards** in English, read a failing board and
its last error, mark a matching service as visited and forget the visit, keep a board out of the feed, read it on request and see when it can be read again, and see a refused
request's wait; in French on a phone they follow the feed's link to the sites to visit yourself, read the boards and the ones left out without a horizontal scroll, and the French phone scenario checks that the four views fit the screen. A dashboard
scenario shows the best new missions and opens the feed with them. The feed's mocked API is
`e2e/helpers/offer-feed.ts`.

Offer action scenarios (`e2e/offer-actions.spec.ts`): a keyboard scenario moves through the feed with J and K, saves,
shows the details of and hides an offer from the keyboard, and finds the feed keys in the shortcut help. A note
scenario adds a private note from an offer's menu, then opens it with `N`, leaves a change with `Esc` and saves
another with `Ctrl+Enter`. An application scenario records one by email from an offer's menu with a follow-up after
5 days, sees the card's badge and pipeline link, then undoes it from the applied view. A bulk scenario selects two
offers by checkbox and `X`, hides them and brings them back with **Undo**, then selects the page and adds it to the
pipeline. A blocklist scenario hides a
publisher from an offer's menu, then, in the profile's **Offer feed** tab, hides another by name and shows the first
one's offers again.

Offer page scenarios (`e2e/offer-page.spec.ts`): in English on a desktop, an offer's menu opens its page, which
shows its description, saves it and copies its address, an offer's rate reads against the market of its skill, an expired offer says when it expired, an offer no longer
collected explains why and links back to the feed, and the dashboard's widget opens an offer's page; in French on a
phone, an offer's page fits the screen.

Suggested search scenarios (`e2e/search-suggestions.spec.ts`): in English on a desktop, the feed without saved
searches opens the suggestions, previews one in the feed, saves another under a new name, dismisses a third and keeps
them closed after a reload; in French on a phone, a target daily rate changed on the profile offers **Les voir**, which
opens the feed with the suggestions shown beside a saved search.

Availability scenarios (`e2e/availability.spec.ts`, in English on a desktop and in French on a phone) type the
profile's **Available from** date in the interface's format, save it as an ISO day and find it again after a reload,
see an offer's match badge say the mission starts up to two weeks early, then clear the date.

Offer link scenarios (`e2e/offer-link.spec.ts`, in English on a desktop and in French on a phone) open **From an offer
link** on the Kanban board, paste a link with a tracking parameter, wait while the site is read, find the form filled
in with the new ESN offered as client, correct the role and save through the import, then paste, from the projects
list, the link of a mission already in the pipeline and open its opportunity.

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

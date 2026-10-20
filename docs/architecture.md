# Architecture Overview And ADR Index

This directory contains architecture decision records for Indezy. ADRs capture decisions that should remain understandable after the code has moved on.

## System Architecture At A Glance

```text
Angular web app
  -> HTTP /api
  -> Spring Boot backend
  -> JPA repositories
  -> PostgreSQL

GitLab CI
  -> tests and builds backend/frontend outputs
  -> publishes Generic Packages and immutable container images
  -> commits Kustomize image tags
  -> Argo CD syncs the exact Git revision to K3s
```

Runtime entry points:

- frontend: `indezy-web`
- backend: `indezy-server`
- database: PostgreSQL in local Docker Compose or shared infrastructure PostgreSQL in cluster
- ingress host: `indezy.swirlit.dev`

## Backend Architecture

The backend is a Spring Boot modular monolith organized by technical layer:

- `controller`: HTTP endpoints
- `service`: business logic and orchestration
- `repository`: persistence queries
- `model`: JPA entities and enums
- `dto`: API contracts
- `validation`: custom Bean Validation constraints for the DTOs (such as `@PasswordMaxBytes`)
- `mapper`: MapStruct conversions
- `pdf`: PDF documents (activity reports) drawn with Apache PDFBox, see [ADR 0008](./adr/0008-pdf-documents-with-pdfbox.md)
- `scraping`: the freelance offer collection (readers of the job boards in `scraping.reader`, the polite HTTP client, normalization, scheduler and retention), see [ADR 0009](./adr/0009-collecting-job-offers.md)
- `config`: CORS, security, OpenAPI, data initialization
- `exception`: error responses and exception mapping
- `util`: JWT, security context, the French public holiday calendar, French departments and regions, and text folding for search

This structure keeps early development simple while preserving clear places for domain behavior and tests.

## Frontend Architecture

The frontend is an Angular application using:

- standalone components
- lazy-loaded route groups
- Angular Material
- RxJS
- `@ngx-translate`
- service classes for API access
- route guards and HTTP interceptors

Routes are organized around the main product areas:

- auth
- dashboard
- profile
- projects
- clients
- contacts
- sources
- offers (the freelance offer feed)
- activity report validation (`/activity-report`, public: opened by clients and ESNs without an account)

## Key Runtime Lifecycles

### Authenticated frontend request

1. User logs in through the Angular auth service.
2. Backend validates credentials and returns a JWT.
3. Frontend stores the token.
4. `authGuard` protects authenticated routes.
5. `authInterceptor` adds `Authorization: Bearer <token>` to API calls.
6. Backend validates the token for non-public routes, then `AccessGuard` resolves the caller's freelance workspace and rejects any project, client, contact, source, interview step, or season that belongs to another workspace (404 for foreign records, 403 for a foreign workspace id).

### Project creation

1. User opens project form.
2. Frontend loads clients and sources for selection.
3. User submits project data.
4. Project service sends the request to `/api/projects`.
5. Backend maps DTO to entity, resolves related freelance/client/source records, persists project, and returns DTO.
6. Dashboard/list/Kanban views consume the updated project data.

### Activity report validation

1. The freelance records a month of an accepted mission on its project page and saves it (`PUT /api/projects/{id}/activity-reports/{year}/{month}`).
2. Submitting it locks the month and returns a random token once; the server stores only its SHA-256 hash, valid 30 days.
3. The freelance sends the link `https://<host>/activity-report#<token>`; the token after `#` never reaches a server log.
4. The validator's browser opens the public page, which skips the Keycloak sign-in and sends the token in the `X-Report-Token` header to `/api/public/activity-reports`, an ingress route without the SSO Middleware.
5. The validator approves, or rejects with a reason, signing with their name; the freelance sees the outcome on the mission and downloads the PDF printed with the ESN's (or final client's) template.

### Offer collection

1. `OfferScheduler` wakes every `indezy.offers.tick` (5 minutes) when `indezy.offers.collection-enabled` is on.
2. For each site, `OfferSiteRuns.claim` marks it running with a conditional update when it is due; a site another run holds, or not due yet, is skipped.
3. The site's `OfferReader` reads its pages through `SiteHttpClient`, which keeps to the site's domain, honours its `robots.txt` and waits 2 seconds between requests.
4. Each page goes through `OfferNormalizer` (freelance only, no title naming a CDI or CDD job, capped texts, rate and remote parsing (the remote policy read from the description, `OfferText.remoteInText`, when the site gives no work mode), department, region and coordinates, the commune read out of place names such as "Paris 13ème" or "La Défense et remote" by `PlaceNames`, geocoding cached in `geo_places`) and `OfferStore`, which adds new offers and refreshes known ones in one transaction.
5. A newest-first site stops at the first page of known offers; every site stops at the page budget (`indezy.offers.max-pages`, 10). The run's outcome sets the next run (the site's interval, doubled per consecutive failure up to a day).
6. Sites whose HTML list pages describe their offers extend `HtmlListReader`, which reads page after page, newest first, until a page holds no offer, the site has no further page (a 404 past the first) or the batch has enough; `PageFacts` reads their labelled facts ("TJM : 550 €"). Sites that describe each offer on its own page extend `DetailPagesReader`: the site's list (a sitemap read by `Sitemaps`, or an API) gives every offer, `OfferBatch.known` filters out the ones stored or skipped, and the unknown pages are read ten per batch, most recently changed first, their schema.org `JobPosting` read by `JobPostings` and their French dates by `FrenchDates`. A page gone (404 or 410) is handed on as closed.
7. `OfferNormalizer` gives each offer its copy keys (`OfferFingerprint`), and `OfferStore` puts an offer saved or refreshed in one `copyGroup` with its copies on other sites, merging their groups. The feed's specification lists one offer per group (the earliest the search finds) and reads workspace states across the group; `OfferSearchService` adds the other copies as `alsoOn`. Offers stored before copies were told apart get their keys from `OfferScheduler.keyStoredOffers` (30 seconds after startup, then every 6 hours, 500 offers per transaction), which groups them the same way.
8. Offers the normalizer sets aside (not freelance, older than the retention window, already expired) are recorded in `offer_skips`, so the next runs do not fetch them again.
9. `SavedOfferSearchService` counts each saved search's new offers with the feed's own specification plus "first seen after the search was last opened", and the badge's total with one `anyOf` query over the workspace's searches; the frontend's `OfferAlertsService` asks for it at sign-in, every five minutes and after a saved search changes.
10. `OfferSearchService.profile` reads the workspace owner's skills, target daily rate and city as an `OfferMatch.Profile`. `OfferMatch` scores each card's offer in Java, and builds the same points as a Criteria expression (a `LIKE` per profile skill on the folded search text, `CASE` on rate and squared distance) that the feed's specification sorts on (`sort=MATCH`) and filters on (`matchesProfile`); `JobOfferSpecificationsTest` checks that both agree.
11. `OfferMarketService` builds the market snapshot from the feed's own specification: `OfferMarketQueries` reads the rates, work mode, remote days, publisher and publication time of every offer it finds (dropping its order) and groups their skills by lower case in SQL; quartiles, rate bands, the remote split, the publishers and the days are computed in Java.
12. `OfferSiteHealthService` gives each site's `offer_site_statuses` row, its open offers and what it brought over a day and a week (`JobOfferRepository.collectionBySite`). A workspace's `offer_site_exclusions` leave the site out of the feed's specification (a `NOT EXISTS` in the feed view), so the feed, saved search counts and market snapshot follow it. `OfferSiteRefresher` reads a site on request: `OfferSiteRuns.requestRefresh` makes it due now with a conditional update that refuses when the site ran or was asked for in the last 15 minutes or a run holds it, then a task on Spring's `applicationTaskExecutor` claims it like the scheduler does and runs `OfferCollector.collect`.
13. Each offer keeps its publisher's folded words in `company_key`, set whenever its company is (`JobOffer.setCompany`); `OfferScheduler.keyStoredOffers` gives offers stored before it their key, 500 at a time after the last one looked at, then reads again the descriptions stored with markup a site escaped twice (`HtmlText.toText` reads such HTML twice from the start), reads the remote policy of offers stored without a work mode from their description, and `OfferRelocation` looks up again, 50 at a time, the offers that name a French place and have no coordinates (a lookup that timed out is not cached, and sites read page by page never read a stored offer again); `OfferNormalizer.relocate` only completes what an offer knows of its place. A workspace's `offer_publisher_blocks` hold the same key, and the feed view's specification leaves out offers whose `company_key` one of them names (`NOT EXISTS`), like an excluded site.
14. `JobBoardSources` gives a workspace the job boards (`OfferSite` and `OfferLeftOutSite`) as sources: `AuthService` calls it when it creates a workspace, and `JobBoardSourcesBackfill` for the workspaces created before, when the server is ready. `freelances.job_board_sources_seeded`, set by a conditional update, makes it happen once per workspace, whichever replica claims it.
15. Every night at 04:15 (Paris time), `OfferRetention` removes offers older than 90 days or expired, with their workspace states, unless a workspace saved or imported them, and forgets skips older than 30 days.

### Deployment

1. GitLab CI checks out the repository.
2. Required `01-build`, optional `02-test`, and required `03-package` package and validate backend/frontend outputs.
3. Optional manual E2E validates the critical browser flow; quality independently publishes non-blocking dependency and Sonar reports from the test artifacts, manually in standard mode and automatically in full mode.
4. Release consumes the required build outputs and publishes checksummed packages/images; deploy requires that release job, while full mode automates build, release, and deploy.
5. Release commits its version and image tags, creates the matching tag, and prepares the next minor version.
6. Argo CD syncs that exact revision to K3s; CI waits for healthy reconciliation and checks both services.

## Data Ownership Rules

Current practical rules:

- `Freelance` owns projects, clients, contacts, and sources.
- `Project` must have one final client and may have a middleman client.
- `Contact` belongs to a client and a freelance.
- `InterviewStep` belongs to a project.
- `User` owns account profile, preferences, and two-factor settings.
- `Season` belongs to a freelance and groups projects.
- `ActivityReport` belongs to a project (one per month) and is deleted with it.
- `JobOffer` is shared by every workspace; `JobOfferState` holds what one workspace did with an offer (saved, hidden, seen, imported project) and is never visible to another.

Future account work should clarify whether `User` and `Freelance` remain separate concepts or become a one-to-one account/workspace model.

## ADR Process

Add a new ADR when a decision:

- changes runtime architecture
- changes the data model or ownership rules
- introduces a new external dependency
- changes deployment or CI/CD behavior
- affects security, privacy, or operations
- constrains future product work

Use the next number in sequence and this filename shape:

```text
NNNN-short-title.md
```

## Accepted ADRs

- [ADR 0001: Split Documentation Out Of Root README](./adr/0001-documentation-structure.md)
- [ADR 0002: Spring Boot And Angular Modular Monolith](./adr/0002-spring-boot-angular-modular-monolith.md)
- [ADR 0003: Java 25, Angular 22, And PostgreSQL Stack](./adr/0003-java-angular-postgresql-stack.md)
- [ADR 0004: GitOps Deployment Through GitLab CI And ArgoCD](./adr/0004-gitlab-ci-argocd-gitops.md)
- [ADR 0005: Security Baseline And Hardening Direction](./adr/0005-security-baseline.md)
- [ADR 0006: Code Quality And Verification Gates](./adr/0006-code-quality-and-verification-gates.md)
- [ADR 0007: Explicit Delivery Jobs And Non-Blocking Verification](./adr/0007-explicit-delivery-jobs.md)
- [ADR 0008: Generate PDF Documents With Apache PDFBox](./adr/0008-pdf-documents-with-pdfbox.md)
- [ADR 0009: Collect Freelance Offers From Job Boards In The Backend](./adr/0009-collecting-job-offers.md)

## Proposed ADRs For Upcoming Work

Good candidates from the roadmap:

- versioned database migrations
- User/Freelance account boundary
- attachment storage and preview model
- email and calendar integration model
- AI provider and bring-your-own-key model
- mobile/offline synchronization
- feature flags and subscription entitlements
- observability baseline

## ADR Template

```markdown
# ADR NNNN: Title

- Status: Proposed | Accepted | Superseded
- Date: YYYY-MM-DD

## Context

What forces, constraints, and current facts made this decision necessary?

## Decision

What did we decide?

## Rationale

Why this option over the alternatives?

## Consequences

What becomes easier, harder, riskier, or more constrained?
```

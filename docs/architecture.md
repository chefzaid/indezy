# Architecture Overview And ADR Index

This document describes how Indezy is built and indexes its architecture decision records (ADRs), which capture decisions that should stay understandable after the code has moved on.

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

The pipeline and release flow are described in [Deployment](./deployment.md#delivery-pipeline).

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

Entities, their ownership and workspace scoping are described in [Data Model](./data-model.md#ownership-rules).

## Frontend Architecture

The frontend is an Angular application using standalone components, lazy-loaded routes, Angular Material, RxJS, `@ngx-translate`, service classes for API access, and route guards and HTTP interceptors. Routes and their guards are declared in `indezy-web/src/app/app.routes.ts`.

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

The `scraping` package fills the shared offer feed from the job boards ([ADR 0009](./adr/0009-collecting-job-offers.md)):

1. `OfferScheduler` wakes every `indezy.offers.tick` (5 minutes) while `indezy.offers.collection-enabled` is on, and `OfferSiteRuns` claims each due site with a conditional update, so one replica reads it at a time. A failing site waits its interval doubled per failure, up to a day.
2. The site's `OfferReader` reads its list pages (`HtmlListReader`) or, for sites that describe each offer on its own page, the unknown pages its sitemap or API lists (`DetailPagesReader`), through `SiteHttpClient`, which keeps to the site's domain, honours `robots.txt` and waits 2 seconds between requests. A run stops at known offers or at `indezy.offers.max-pages` (10).
3. `OfferNormalizer` keeps freelance missions only, caps texts, parses rate, remote policy and place (geocoding cached in `geo_places`) and computes the copy keys (`OfferFingerprint`); offers it sets aside are recorded in `offer_skips` so later runs skip them.
4. `OfferStore` adds new offers and refreshes known ones in one transaction, groups a mission's copies across sites (`copyGroup`) and records changes and appearances.
5. Every night `OfferRetention` (04:15 Paris time) removes offers older than 90 days or expired, unless a workspace saved, applied to or imported them; the rate medians and suspicion flags are recomputed at 04:45 (`OfferRateBenchmarkService`, `OfferSuspicionService`).
6. The feed (`OfferSearchService`), saved search counts, search suggestions and the market snapshot filter offers with the same specification (`JobOfferSpecifications`), and saved search emails read new offers through `OfferSearchService`; `OfferSiteRefresher` reads one site on request.

### Offer search at scale

How the feed's query stays fast on PostgreSQL; the measurements are in [Testing](./testing.md#search-load-test):

- Workspace states (hidden, saved, seen, noted), shared by a mission's copies, are two correlated subqueries (one on the offer, one on its copy group); excluded sites and blocked publishers are lists read once per query.
- `OfferSearchIndex` builds a GIN trigram index on the folded `search_text` (`idx_job_offers_search_text_trgm`) at startup with `CREATE INDEX CONCURRENTLY`, rebuilds one left invalid, and only logs a warning when `pg_trgm` is refused; it does nothing on H2.
- `OfferMentionIndexer` records which profile terms each offer mentions (`offer_terms`, `offer_mentions`) with the function the cards score with (`OfferMatch.mentions`), so the match score (`sort=MATCH`, `matchesProfile`) is an indexed lookup; a term not recorded yet falls back to the trigram index. Recording locks one `offer_terms` row, so replicas take turns.
- The keyword filter stays a `LIKE` on each offer, applied after the cheaper filters.
- The database setup hook sets `jit = off` and `random_page_cost = 1.1` on `indezy_user` (the local init script on its own role).
- Open offers per site and skill suggestions are counted once a minute (`ExpiringValue`); the market snapshot is kept a minute per workspace and filters (`ExpiringCache`). Feed pages and the dashboard are not cached, since they show the workspace's own states.
- When the feed reads another page or sort of the same filters it sends the total it already has, and `JobOfferRepository.findPage` reads the page without a count query.

Remaining work is tracked in [TODO.md](../TODO.md#job-board-integrations).

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

## ADR Index

| ADR | Status |
|---|---|
| [0001: Split Documentation Out Of Root README](./adr/0001-documentation-structure.md) | Accepted |
| [0002: Spring Boot And Angular Modular Monolith](./adr/0002-spring-boot-angular-modular-monolith.md) | Accepted |
| [0003: Java 25, Angular 22, And PostgreSQL Stack](./adr/0003-java-angular-postgresql-stack.md) | Accepted |
| [0004: GitOps Deployment Through GitLab CI And ArgoCD](./adr/0004-gitlab-ci-argocd-gitops.md) | Accepted |
| [0005: Security Baseline And Hardening Direction](./adr/0005-security-baseline.md) | Superseded |
| [0006: Code Quality And Verification Gates](./adr/0006-code-quality-and-verification-gates.md) | Superseded by ADR 0007 |
| [0007: Explicit Delivery Jobs And Non-Blocking Verification](./adr/0007-explicit-delivery-jobs.md) | Accepted |
| [0008: Generate PDF Documents With Apache PDFBox](./adr/0008-pdf-documents-with-pdfbox.md) | Accepted |
| [0009: Collect Freelance Offers From Job Boards In The Backend](./adr/0009-collecting-job-offers.md) | Accepted |
| [0010: Keep Opportunity Files In PostgreSQL](./adr/0010-attachments-in-postgresql.md) | Accepted |
| [0011: Organizations For The Teams Tier](./adr/0011-organizations-for-the-teams-tier.md) | Proposed |

## Decisions Still Open

These will need an ADR once decided:

- versioned database migrations
- the `User`/`Freelance` account boundary
- email and calendar integration model
- AI provider and bring-your-own-key model
- mobile app technology
- plans and subscription entitlements

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

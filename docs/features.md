# Features

This guide describes the product capabilities that exist in the current Indezy application. Planned work lives in [TODO.md](../TODO.md), so this file should stay focused on behavior users can already experience or that is already represented in the codebase.

## Product Shape

Indezy is a freelance opportunity tracker for the French tech market. It helps a freelancer keep one organized view of prospects, clients, ESNs, contacts, interview steps, rates, locations, and dashboard signals.

The core workflow is:

1. Create or register the freelancer profile.
2. Add clients, intermediaries, contacts, and sources.
3. Capture project opportunities with rate, work mode, location, duration, and source.
4. Track each opportunity through the pipeline and interview steps.
5. Use dashboard and filtering views to decide what needs attention.

## Authentication And Account Access

Implemented account flows:

- user registration through `/api/auth/register`
- user login through `/api/auth/login`
- JWT creation on successful authentication
- frontend route protection through `authGuard`
- bearer token attachment through the Angular auth interceptor
- Keycloak single sign-on in production (`/api/auth/sso`, see [Security](./security.md))
- optional TOTP two-factor authentication for local accounts, asked at login once enabled
- profile page for account details, avatar, preferences (language, theme, date format), notifications, password, two-factor authentication and a JSON export of every piece of account data

The backend stores a few account fields the UI does not use yet (timezone, currency, default view, items per page, notification flags without delivery). Verify the full user journey before presenting such a field as product-complete.

## Freelancer Profile

The freelancer model captures the working identity used by opportunities:

- first name, last name, email, phone, birth date, address, and city
- employment status: `FREELANCE`, `PORTAGE`, or `CDI`
- notice period and availability date
- reversion rate and income tax rate
- optional CV file path

The freelancer owns the main opportunity workspace: projects, clients, contacts, and sources are all attached back to a freelance profile.

## Projects And Opportunities

Projects are the central business object. A project represents an opportunity, mission, or job lead.

Implemented project fields include:

- role
- status
- description
- tech stack
- daily rate
- client daily rate (rate the end client pays the intermediary/ESN), with the per-day margin and margin percentage derived from the gap with the freelance daily rate
- daily rate negotiation history: asked and offered daily rates kept alongside the agreed daily rate
- work mode: onsite, remote, or hybrid
- remote and onsite days per month
- advantages
- start date
- duration and renewal timing
- days per year
- document paths
- original link
- personal rating
- notes
- lost/rejection reason (captured when a card is moved to LOST, surfaced as a dashboard breakdown)
- favorite flag (pins hot leads to the top of their Kanban column)
- client, optional middleman, source, optional job-hunting season, and freelance owner

The project form edits the pipeline stage (with the loss reason for lost opportunities), the final client, the optional ESN/intermediary, the source and the season. The project page shows the stage as a pill that can be changed in place and names the opportunity's season, and the projects list filters by stage and season and paginates results.

Opportunity statuses, one per Kanban column (French / English labels):

| Status | Column | Win probability |
| --- | --- | --- |
| `CONTACT` | Contact | 20 % |
| `INTERVIEW` | Tests & Entretiens / Tests & Interviews | 50 % |
| `OFFER` | Offre / Offer | 80 % |
| `WON` | Accepté / Accepted | 100 % |
| `LOST` | Perdu / Abandonné / Lost / Dropped | 0 % |

`CONTACT` replaced the former `IDENTIFIED` and `APPLIED` stages. On startup the API moves any
remaining opportunity in those stages to `CONTACT` and rebuilds the PostgreSQL status check
constraint (see [Data Model](./data-model.md#migration-reality)).

The project model also exposes helper behavior for total revenue and work-mode checks. Dashboard and list views build on this data.

## Clients, ESNs, And Contacts

Clients are companies or intermediaries associated with freelance opportunities. The `isFinal` flag distinguishes final clients from intermediaries such as ESNs. A client page lists its missions (as final client or intermediary) and its contacts; a client that is still linked to missions cannot be deleted (the API answers 409), so missions are never removed as a side effect.

Client data includes:

- company name
- address and city
- domain
- final-client flag
- notes
- quality rating (1-5) and blacklist flag with reason (track clients to avoid: payment delays, ghosting, bad process)
- freelance owner
- linked projects, middleman projects, and contacts

Contacts belong to both a client and a freelancer. A contact can store:

- first name
- last name
- email
- phone
- notes

These relationships make it possible to track who is attached to a client or ESN and which opportunities depend on that relationship.

## Sources

Sources describe where opportunities come from. They can represent job boards, social channels, email, calls, or SMS.

Implemented source fields include:

- name
- type: `JOB_BOARD`, `SOCIAL_MEDIA`, `EMAIL`, `CALL`, or `SMS`
- link
- listing flag
- popularity rating
- usefulness rating
- notes
- linked projects

Source data feeds filtering and the dashboard's source ROI ranking. Deleting a source keeps its opportunities, which simply lose their origin.

## Interview Steps

Interview steps capture the process attached to a project. Each step belongs to exactly one project.

Implemented step fields:

- title
- scheduled date and time
- status
- notes

Current step statuses:

- `TO_PLAN`
- `PLANNED`
- `CANCELED`
- `WAITING_FEEDBACK`
- `VALIDATED`
- `FAILED`

The project page lists the steps as a timeline: each step can be added or edited (title with suggestions, status, local date and time, notes), moved to another status from its status pill, or deleted, and the header summarizes how many steps are validated. The backend exposes interview-step endpoints for creation, updates, transitions, and project-level retrieval.

## Project Journal

Each project has a chronological journal of free-text notes (calls, emails, decisions) exposed under `/api/projects/{projectId}/notes` (list newest-first, add, delete). The project detail page shows the timeline with timestamps and lets the user add or remove entries. Notes are stored and rendered as plain text for now; Markdown support with live preview is a planned enhancement.

## Kanban And Pipeline View

The dashboard includes a Kanban mode for moving opportunities across high-level statuses. Drag-and-drop changes project status through the project and interview-step APIs. Cards can be pinned as favorites (`PATCH /projects/{id}/favorite`), which keeps hot leads at the top of their column. Within a column, cards can be dragged to set a manual priority, persisted through `PUT /projects/kanban/{freelanceId}/reorder` (favorites still pin above the manual order). Each card shows a card-aging indicator (days since last activity) and highlights opportunities with no activity for 14+ days so stale leads stand out. A quick-add button on each column opens a minimal dialog (role, client, daily rate) that creates an opportunity directly in that column's status, to be enriched later. Moving a card into the LOST column prompts for a loss reason, which feeds a "why opportunities were lost" breakdown on the dashboard. Cards that share the same client and role (case-insensitive) as another opportunity are flagged as possible duplicates for manual deduplication.

The board has five status-driven columns: Contact, Tests & Entretiens, Offre, Accepté and Perdu / Abandonné. Intermediate recruitment steps (technical test, client interview...) are tracked as interview steps inside the Tests & Entretiens column. The board follows the season selected on the dashboard (`GET /projects/kanban/{freelanceId}?seasonId=`), and cards quick-added to it join that season.

## Job-Hunting Seasons

A season is a bounded job-hunting period, like a sprint: "Autumn search 2026" from 1 August until the right mission is signed. Each season has its own pipeline and its own dashboard instead of one dashboard that is always on.

- A season has a name, a start date, an optional end date (empty while it runs), an optional target daily rate and a free-text objective.
- Starting a season (dashboard → season picker → New season) adopts the workspace's opportunities that are not in a season yet and were created within its dates. New opportunities join the season running on the day they are created, unless the form or the board picks another one.
- The dashboard season picker switches the whole dashboard (KPIs, charts, funnels, analytics, recent projects) and the Kanban board to one season, or to all seasons. The choice is remembered in the browser.
- A season dashboard opens with a summary banner: dates, days elapsed, objective, opportunities, success rate (accepted out of decided), accepted missions and average vs target daily rate, each compared with the all-season value.
- A season can be edited, closed (it then ends the day before, so new opportunities no longer join it) or deleted; deleting keeps its opportunities, which simply no longer belong to a season.
- Contact reminders (dormant contacts, anniversaries) stay workspace-wide.

API: `GET /seasons/by-freelance/{freelanceId}`, `GET|PUT|DELETE /seasons/{id}`, `POST /seasons`, plus the optional `seasonId` query parameter on `GET /projects/kanban/{freelanceId}` and `GET /projects/stats/dashboard/{freelanceId}`. Seasons are private to their workspace like every other record.

## Dashboard And Analytics

The dashboard provides an overview of the opportunity workspace with:

- total projects
- average daily rate
- estimated revenue
- forecast revenue (pipeline revenue weighted by each opportunity's win probability per status)
- active projects
- projects by status
- projects by work mode
- daily rate distribution
- source ROI ranking (signed contracts vs total opportunities and conversion rate per source)
- daily rate evolution by year (average asked vs obtained daily rate)
- bench time tracking (idle days and number of gaps between signed missions, with an estimated cost)
- pipeline conversion funnel (how many opportunities reach each stage, to reveal where they drop off), overall and broken down by source, client type (direct vs through an ESN) and ESN
- mission end-date reminders (signed missions ending within six weeks, so prospection can restart before the bench)
- stale-opportunity detection (active opportunities idle for 14+ days, surfaced to follow up or archive before they go cold)
- recent projects
- overview and Kanban display modes
- a season picker that scopes all of the above to one job-hunting season (see [Job-Hunting Seasons](#job-hunting-seasons))

The analytics are intentionally operational: they help a freelancer understand pipeline volume, activity, rate distribution, which sources actually yield signed contracts, where opportunities drop off in the funnel, and how negotiated rates trend over time.

## Filtering And Search Surfaces

Every list uses the same kind of inline filters, applied in the browser as you type:

- projects: search, work mode, rate range, status, season, dates, duration, client and tech stack, with sorting, pagination and filters collapsed on small screens
- clients: search, type (final client or ESN), city and sort
- contacts: search and client
- sources: search, type and minimum usefulness rating
- archive: full-text search across past opportunities

## Commute-Time Sorting

Commute support exists through a dedicated commute service and API surface.

Implemented behavior:

- home address and job location inputs
- Google Maps Distance Matrix API integration
- driving and transit modes
- graceful fallback when no Google Maps API key is configured
- commute DTOs for returning time and distance data to the frontend

Configuration is handled through `GOOGLE_MAPS_API_KEY` and `google.maps.api-key`. See [Development](./development.md) and [Deployment](./deployment.md) for setup details.

## Reversion Rate Calculator

Indezy includes reversion-rate oriented finance fields and calculator behavior for:

- net daily revenue
- monthly revenue
- yearly revenue
- configurable income tax
- max workable days with French public holidays

The projects list also offers an accountant CSV export (`GET /projects/export/csv/{freelanceId}?year=YYYY`): one row per project (role, status, client, source, work mode, start date, duration, daily rate, days per year, estimated revenue) plus a totals row, optionally filtered to a single year.

The next finance work should extend this with deductible expenses, social contributions, status-specific assumptions, and invoice support.

## Internationalization

The Angular app uses `@ngx-translate` with translation files in:

- `indezy-web/src/assets/i18n/fr.json`
- `indezy-web/src/assets/i18n/en.json`

The app component loads the saved language preference and exposes French and English language switching. New UI work should add translation keys in both files and avoid hard-coded user-facing strings.

## Current Product Boundaries

These capabilities are intentionally not treated as implemented yet, even if some model fields or dependencies hint at them:

- social login (Google, GitHub, Microsoft)
- password reset and email verification
- two-factor recovery codes
- self-service account deletion (identities live in Keycloak)
- job-board connectors
- Gmail, Outlook, or LinkedIn sync
- Chrome extension capture
- cross-platform mobile app
- AI parsing, ranking, or recommendation workflows
- invoice generation
- calendar sync

Keep this distinction sharp. It prevents the docs from promising features that exist only as model placeholders or backlog intent.

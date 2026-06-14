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
- profile surface for account, preferences, notifications, and security-related fields

The backend already stores richer user profile fields such as skills, languages, timezone, currency, theme, language preference, notification flags, session records, and security-question records. Not every stored account field is equally mature in the UI yet, so feature work should verify the full user journey before presenting a field as product-complete.

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
- favorite flag (pins hot leads to the top of their Kanban column)
- client, optional middleman, source, and freelance owner

Current opportunity statuses:

- `IDENTIFIED`
- `APPLIED`
- `INTERVIEW`
- `OFFER`
- `WON`
- `LOST`

The project model also exposes helper behavior for total revenue and work-mode checks. Dashboard and list views build on this data.

## Clients, ESNs, And Contacts

Clients are companies or intermediaries associated with freelance opportunities. The `isFinal` flag distinguishes final clients from intermediaries such as ESNs.

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

Source data feeds filtering, reporting, and future source ROI work.

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

The UI includes scheduling and action dialogs. The backend exposes interview-step endpoints for creation, updates, transitions, and project-level retrieval.

## Kanban And Pipeline View

The dashboard includes a Kanban mode for moving opportunities across high-level statuses. Drag-and-drop changes project status through the project and interview-step APIs. Cards can be pinned as favorites (`PATCH /projects/{id}/favorite`), which keeps hot leads at the top of their column. Each card shows a card-aging indicator (days since last activity) and highlights opportunities with no activity for 14+ days so stale leads stand out. A quick-add button on each column opens a minimal dialog (role, client, daily rate) that creates an opportunity directly in that column's status, to be enriched later.

The current Kanban implementation is status-driven. The roadmap in [TODO.md](../TODO.md) tracks a richer pipeline model where generic columns can coexist with custom intermediate recruitment steps.

## Dashboard And Analytics

The dashboard provides an overview of the opportunity workspace with:

- total projects
- average daily rate
- estimated revenue
- active projects
- projects by status
- projects by work mode
- daily rate distribution
- recent projects
- overview and Kanban display modes

The current analytics are intentionally operational: they help a freelancer understand pipeline volume, activity, and rate distribution. More advanced analytics such as conversion funnels, source ROI, and daily-rate evolution remain roadmap items.

## Filtering And Search Surfaces

The frontend includes reusable filter components for common list workflows:

- advanced search/filter panel
- comprehensive filter panel
- date range filter
- multi-select filter
- range slider filter
- filter presets

These components support the product direction of making projects, clients, contacts, and sources easy to scan and narrow down.

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

The next finance work should extend this with deductible expenses, social contributions, status-specific assumptions, invoice support, and accountant exports.

## Internationalization

The Angular app uses `@ngx-translate` with translation files in:

- `indezy-web/src/assets/i18n/fr.json`
- `indezy-web/src/assets/i18n/en.json`

The app component loads the saved language preference and exposes French and English language switching. New UI work should add translation keys in both files and avoid hard-coded user-facing strings.

## Current Product Boundaries

These capabilities are intentionally not treated as implemented yet, even if some model fields or dependencies hint at them:

- production-grade OAuth login
- password reset and email verification
- two-factor authentication
- full account session management
- job-board connectors
- Gmail, Outlook, or LinkedIn sync
- Chrome extension capture
- cross-platform mobile app
- AI parsing, ranking, or recommendation workflows
- invoice generation
- calendar sync

Keep this distinction sharp. It prevents the docs from promising features that exist only as model placeholders or backlog intent.

# Features

This guide describes the product capabilities that exist in the current Indezy application. Planned work lives in [TODO.md](../TODO.md), so this file should stay focused on behavior users can already experience or that is already represented in the codebase.

## Product Shape

Indezy is a freelance opportunity tracker for the French tech market. It helps a freelancer keep one organized view of prospects, clients, ESNs, contacts, interview steps, rates, locations, and dashboard signals.

The core workflow is:

1. Create or register the freelancer profile.
2. Add clients, intermediaries, contacts, and sources.
3. Capture project opportunities with rate, work mode, location, duration, and source, or add them from the freelance offer feed.
4. Track each opportunity through the pipeline and interview steps.
5. Use dashboard and filtering views to decide what needs attention.

## Authentication And Account Access

Implemented account flows:

- user registration through `/api/auth/register`
- password rules checked by the forms and the API: at least 6 characters at registration and 8 for a new password on the profile page, a confirmation that matches the new password, and at most 72 bytes, the BCrypt limit, where an accented letter counts as 2 and an emoji as 4 (see [Security](./security.md#password-storage))
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

Project pages and Kanban cards offer **Copy project link**. The copied
`/projects?project=<id>` URL opens that project's detail page; `/dashboard?project=<id>`
also works. Password login preserves the destination. These links require access to
the owning account: they do not make a project public. Invalid identifiers open the
not-found page, and unavailable projects return to the list with an error.

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
- activity report template: title, layout, PDF language, on-site / remote split and footer note (see [Monthly Activity Reports](#monthly-activity-reports-cra))
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

## Freelance Offer Feed

**Offers** in the navigation lists the freelance missions published on the job boards Indezy reads, newest first,
in one feed. The backend reads each board on its own schedule (see [Architecture](./architecture.md#offer-collection)
and [ADR 0009](./adr/0009-collecting-job-offers.md)); the feed shows what the boards published, never permanent (CDI)
or fixed-term (CDD) jobs: an offer is kept only when its site marks it as a freelance mission and its title does not
name a CDI or CDD job (some sites list "Tech Lead – CDI" among their freelance offers; "CDI/Freelance" is kept).

Sites read today:

| Site | What Indezy reads | Refreshed |
| --- | --- | --- |
| Free-Work | contractor offers of its public API; offers from Digistrat, Propulse IT and Omicrone are left out (fake listings) | every hour |
| Collective.work | the job pages' data, offers whose contract types include freelance | every hour |
| Cherry Pick | its freelance missions (`mission_type` short), not its salaried jobs | every 3 hours |
| LeHibou | its 10 newest missions; pro bono missions are left out | every 30 minutes |
| Yeets | missions still open (to start or to confirm) | every 6 hours |
| WorkDispo | the jobs of its sitemap, kept when their schema.org posting is a contractor one and their contract reads Freelance | every 2 hours |
| Next Mondays | the jobs of its jobs sitemap with their daily rate and duration | every 12 hours |
| Mindquest | the missions of its missions sitemap whose posting is a contractor one, with their start, rate and tags | every 3 hours |
| Freelance Factory | the missions of its WordPress API, with the place, daily rate, duration and description of their page | every 6 hours |
| LittleBig Connection | the open missions of its nine categories, with their daily budget; the site gives no date, so they are dated when first seen | every 6 hours |

Sites that describe each offer on its own page (WorkDispo, Next Mondays, Mindquest, Freelance Factory) are read a
few pages at a time: each run reads up to 100 offers Indezy does not hold yet, most recently changed first, so their
backlog fills in over the first runs. An offer set aside (a CDI or CDD job, an offer older than 90 days, a page
gone) is remembered for 30 days and not read again.

The other boards are planned in [TODO.md](../TODO.md#freelance-offer-feed-added-2026-10-19), with the ones left out
and why (LinkedIn forbids crawling; HiTechPros, Opteamis, Comet and others keep missions behind a sign-in).

Each offer shows its site, title, publisher, daily rate ("550 € – 650 €", "≤ 600 €" or "TJM non précisé"), place with
its department and the distance from the profile's city, work mode (on site, hybrid with remote days a week, full
remote), duration, start date or "as soon as possible", seniority, skills, an excerpt and how long ago it was
published (the hint says when a site gives no date and the offer is dated when Indezy first saw it). Unseen offers are
marked **New**.

- **Filters**, kept in the address so a search can be bookmarked or shared: keywords (every word must appear in the
  title, publisher, skills, place or description, accents ignored), daily rate minimum and maximum with "hide offers
  without a daily rate", regions, departments, distance from the profile's city (10 to 200 km), work modes, minimum
  remote days a week, duration, start by a date (missions starting as soon as possible stay listed), seniority,
  skills (suggested from the open offers), published within 24 hours, 3 days, a week or a month, sites, and unseen
  offers only. An offer that does not state its duration, start or seniority stays listed when filtering on them;
  one without a place, work mode or remote days is left out of filters on those.
- **Sort** by newest, highest daily rate (offers without a rate last) or closest (when the profile has a city).
- **Views**: all offers (the hidden ones and expired ones are left out), saved offers (expired ones stay, marked
  **Expired**) and hidden offers.
- **Actions** on each offer: open it on its site (it is marked seen), show its full description, save it, hide it
  (or show it again), and **Add to pipeline**: a new opportunity at the Contact stage with the offer's title, rate
  (the highest rate stated, else the profile's target daily rate), work mode and remote days a month, start date,
  duration, description, skills as tech stack and link. Its client is the offer's publisher (created as an ESN or
  intermediary when the workspace has no client of that name; the site itself when the offer names none), and its
  source is the job board: the workspace's source named after the site, created as a job board on first import. An
  offer is added once; the card then links to its opportunity, until that opportunity is deleted.
- What a workspace does with offers (saved, hidden, seen, imported) is private to it and part of the account data
  export. Offers older than 90 days or past their expiry are removed every night, unless a workspace saved or
  imported them.

API: `GET /offers` (filters, `view`, `sort`, `page`, `size`), `GET /offers/{id}`, `PUT /offers/{id}/state`,
`POST /offers/{id}/import`, `GET /offers/sites`, `GET /offers/skills?q=` and `GET /offers/regions`.

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

## Monthly Activity Reports (CRA)

An accepted mission's page (stage Accepté / Accepted) has an **Activity reports** card: the monthly activity report
(compte rendu d'activité, CRA) that ESNs and clients ask for before invoicing.

- The card opens on the current month, kept within the mission dates when they are known, with arrows to move between
  months and a chip per saved month showing its status (Brouillon / Draft, En attente de validation / Awaiting approval,
  Validé / Approved, Refusé / Rejected). Unsaved changes are confirmed before leaving a month.
- The calendar shows the month Monday first. Weekends and the French public holidays (named in the day descriptions)
  are shaded, and days outside the mission are dimmed. Choose what to record (full or half day on site, full or half
  day remote, full or half day absence, clear), then tap the days; tapping a day again with the same choice clears it.
  **Fill business days** records every empty business day of the mission as a full day, on site or remote according to
  the mission's work mode. Each day is a labelled button for screen readers (date, what was recorded, holiday, weekend).
- Totals update as you type: days worked, on site, remote, absences, the month's business days, and the amount
  excluding VAT (days worked times the mission's daily rate). A comment for the validator goes with the month.
- **Send for approval** saves the month, locks it and shows the validation link once, valid for 30 days, to copy or to
  send by email (a prefilled message with the month, the mission, the days worked and the link). Later, **Generate a
  new link** replaces a lost link; the old one stops working.
- The client or ESN opens the link without an Indezy account. The page shows the consultant, the client and ESN, the
  mission, the month's calendar and totals (the on-site / remote split only when the template shows it), the
  consultant's comment and a PDF download, in French or English (a switch at the top). The validator signs with their
  name and approves, or rejects with a required reason. A report is answered once; a second answer is refused.
- The mission page then shows who approved or rejected the month and when, with the comment or reason. A rejected month
  stays editable and keeps the reason until it is sent again. **Reopen to edit** returns a submitted or approved month
  to draft, after a confirmation, and the link stops working.
- **Download PDF** prints the month with the template of the mission's ESN, or of its final client when there is none,
  set on the client form under **Activity report template**: document title, layout (one day per line in portrait, or a
  month grid in landscape), PDF language (French or English), on-site / remote split, and a footer note such as where
  to send the signed report. The PDF lists the consultant, client, ESN, mission and period, the days, the totals, the
  comment and two signature boxes; an answered month prints "Validé électroniquement par … le … (heure de Paris)"
  ("Approved electronically by … on … (Paris time)") or the rejection with its reason.
- Activity reports are part of the account data export and are deleted with their mission.

API: `GET /projects/{projectId}/activity-reports`, `GET|PUT /projects/{projectId}/activity-reports/{year}/{month}`,
`POST .../{year}/{month}/submit`, `POST .../link`, `POST .../reopen` and `GET .../pdf`, private to the workspace like
every project resource. The validation link reaches `GET /public/activity-reports`, `GET /public/activity-reports/pdf`
and `POST /public/activity-reports/decision` with its token in the `X-Report-Token` header (see
[Security](./security.md#activity-report-validation-links)). Validated days are what the planned invoices will bill.

## Kanban And Pipeline View

The dashboard includes a Kanban mode for moving opportunities across high-level statuses. Drag-and-drop changes project status through the project and interview-step APIs. Cards can be pinned as favorites (`PATCH /projects/{id}/favorite`), which keeps hot leads at the top of their column. Within a column, cards can be dragged to set a manual priority, persisted through `PUT /projects/kanban/{freelanceId}/reorder` (favorites still pin above the manual order). Each card shows a card-aging indicator (days since last activity) and highlights opportunities after the account's configured inactivity period (14 days by default). A quick-add button on each column opens a minimal dialog (role, client, daily rate) that creates an opportunity directly in that column's status, to be enriched later. Moving a card into the LOST column prompts for a loss reason, which feeds a "why opportunities were lost" breakdown on the dashboard. Cards that share the same client and role (case-insensitive) as another opportunity are flagged as possible duplicates for manual deduplication.

Profile → Preferences lets the user set the inactivity period to 1–365 whole days.
The same saved value controls dashboard follow-up reminders and Kanban highlighting;
it counts elapsed days since the opportunity's last update, including the threshold day.
Saving other preferences leaves this setting unchanged when it is omitted. Existing
accounts without a saved value keep the 14-day default. This setting flags opportunities;
automatic archiving remains on the roadmap.

The board has five status-driven columns: Contact, Tests & Entretiens, Offre, Accepté and Perdu / Abandonné. Intermediate recruitment steps (technical test, client interview...) are tracked as interview steps inside the Tests & Entretiens column. The board follows the season selected on the dashboard (`GET /projects/kanban/{freelanceId}?seasonId=`), and cards quick-added to it join that season.

## Job-Hunting Seasons

A season is a bounded job-hunting period, like a sprint: "Autumn search 2026" from 1 August until the right mission is signed. Each season has its own pipeline and its own dashboard instead of one dashboard that is always on.

- A season has a name, a start date, an optional end date (empty while it runs), an optional target daily rate and a free-text objective. New seasons default to the profile's target daily rate; the form allows an override. Leaving a new season's target blank uses the profile default when one is set. Editing an existing season preserves its own target, which can be cleared independently.
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
- daily rate distribution by exact negotiated euro amount (for example 550, 600 and 650),
  sorted numerically with one count per opportunity; missing rates are omitted. The chart
  follows the selected season and provides localized labels and a screen-reader summary.
- profile target daily rate compared with the average recorded rate of accepted missions
  in the selected season or global view; unknown accepted rates are omitted from the average,
  and a dash is shown when no accepted rate is known
- source ROI ranking (signed contracts vs total opportunities and conversion rate per source)
- daily rate evolution by year (average asked vs obtained daily rate)
- bench time tracking (idle days and number of gaps between signed missions, with an estimated cost)
- pipeline conversion funnel (how many opportunities reach each stage, to reveal where they drop off), overall and broken down by source, client type (direct vs through an ESN) and ESN
- mission end-date reminders (signed missions ending within six weeks, so prospection can restart before the bench)
- stale-opportunity detection (active opportunities idle for the configured inactivity period, surfaced to follow up or archive before they go cold)
- recent projects
- overview and Kanban display modes
- a season picker that scopes all of the above to one job-hunting season (see [Job-Hunting Seasons](#job-hunting-seasons))

The analytics are intentionally operational: they help a freelancer understand pipeline volume, activity, rate distribution, which sources actually yield signed contracts, where opportunities drop off in the funnel, and how negotiated rates trend over time.

Profile → Personal information includes an optional target daily rate in whole euros.
It must be positive when set and can be cleared. Changing it affects the dashboard comparison
and defaults for future seasons; it never rewrites targets already saved on existing seasons.

## Filtering And Search Surfaces

Every list uses the same kind of inline filters, applied in the browser as you type:

- projects: search, work mode, rate range, status, season, dates, duration, client and tech stack, with sorting, pagination and filters collapsed on small screens
- clients: search, type (final client or ESN), city and sort
- contacts: search and client
- sources: search, type and minimum usefulness rating
- archive: full-text search across past opportunities

## Keyboard Shortcuts

Signed-in users can open shortcut help from the account menu or with `Alt+Shift+H`.
`Alt+Shift+N` opens a new project; `Alt+Shift+F` opens project search and focuses its
field, expanding the filters on phones. `Alt+Shift+M` opens the stage menu for the
current project, or opens the focused Kanban card with that menu ready. Choose a
stage to move it; moving to Lost still requires the existing loss-reason dialog.

Shortcuts do not run inside editable fields, menus, selection panels or dialogs,
or during composition and repeated key presses. They are available in both
French and English, and leave ordinary browser shortcuts unchanged.

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

The app component loads the saved language preference and exposes French and English language switching. New UI work should add translation keys in both files and avoid hard-coded user-facing strings, attribute text included (`aria-label`, `title`, `matTooltip`, `placeholder`), since screen readers announce it.

## Current Product Boundaries

These capabilities are intentionally not treated as implemented yet, even if some model fields or dependencies hint at them:

- social login (Google, GitHub, Microsoft)
- password reset and email verification
- two-factor recovery codes
- self-service account deletion (identities live in Keycloak)
- job boards other than the ten the offer feed reads (the next ones are planned in TODO.md)
- Gmail, Outlook, or LinkedIn sync
- Chrome extension capture
- cross-platform mobile app
- AI parsing, ranking, or recommendation workflows
- invoice generation (activity reports provide the validated days it will bill)
- qualified electronic signature: an activity report is approved with the validator's typed name and a timestamp, a simple electronic signature
- calendar sync

Keep this distinction sharp. It prevents the docs from promising features that exist only as model placeholders or backlog intent.

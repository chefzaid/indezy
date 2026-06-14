# TODO

## Pipeline & Kanban

Not all freelance jobs share the same recruitment process:

Some have 3 steps, others 5, with different styles and ordering. For example, a technical test can happen before or after an interview.

The current target model is generic columns: Contact => ESN => Client => Go => Chosen. Each Kanban item would keep a label for the intermediate step it is in and the scheduled next step while staying in the same column.

- [x] Interview step tracking per project
- [x] Step scheduling with date/time and notes
- [x] Step statuses: `TO_PLAN`, `PLANNED`, `CANCELED`, `WAITING_FEEDBACK`, `VALIDATED`, `FAILED`
- [x] Kanban mode for moving opportunities across high-level statuses
- [x] Drag-and-drop status changes from the Kanban board
- [ ] Customizable pipeline templates per client/ESN (save a recruitment process and reuse it)
- [ ] Bulk actions on Kanban cards (archive, change status, assign source)
- [x] Card aging indicators (highlight opportunities with no activity for X days)
- [ ] Drag-and-drop reordering within a column (manual priority)
- [ ] Quick-add card directly from the Kanban board (minimal form, enrich later)
- [x] Pinned/favorite opportunities (keep hot leads at the top, dedicated view)
- [ ] Lost/rejected reason tracking (rate too low, position filled, no response...) with stats
- [ ] Automatic stale-opportunity detection and suggested follow-up or archival
- [ ] Duplicate opportunity detection (same client + same role posted on several job boards), flagged for manual deduplication

## Opportunities & Projects

- [x] Freelancer profile model with identity, availability, employment status, reversion rate, income tax rate, and CV path fields
- [x] Freelance-owned workspace for projects, clients, contacts, and sources
- [x] Project/opportunity management with role, status, description, tech stack, daily rate, work mode, dates, duration, notes, documents, link, rating, client, middleman, source, and freelance owner
- [x] Opportunity statuses: `IDENTIFIED`, `APPLIED`, `INTERVIEW`, `OFFER`, `WON`, `LOST`
- [x] Work modes: `ONSITE`, `REMOTE`, `HYBRID`
- [x] Project helper behavior for total revenue and work-mode checks
- [x] Source management for opportunity origins
- [x] Source types: `JOB_BOARD`, `SOCIAL_MEDIA`, `EMAIL`, `CALL`, `SMS`
- [x] Source ratings for popularity and usefulness
- [x] Commute-time sorting support using home address and job location
- [x] Google Maps Distance Matrix API integration for driving and transit modes
- [x] Graceful commute fallback when no Google Maps API key is configured
- [ ] Attachments per project (job description PDF, contract, technical test brief) with inline preview (image, PDF) instead of forcing downloads
- [ ] Notes/journal timeline per project (every call, email, decision logged chronologically), with Markdown support and live preview
- [ ] Note templates (call debrief, interview debrief, negotiation summary) selectable when adding a note
- [ ] Voice memo after a call: record, transcribe, attach the transcript as a project note
- [ ] Skill tags on projects + match score against the freelancer's skill profile (with global tag rename across all projects)
- [ ] Daily rate negotiation history (asked / offered / agreed) per project
- [ ] Project archive view with full-text search across past opportunities
- [ ] Contract milestones tracking (start date, renewal date, notice period alerts)
- [ ] Mission end-date reminders to restart prospection N weeks before the bench
- [ ] Prospection campaigns: separate pipeline per job-search period (e.g. "2026 search"), each with its own stats, comparable against the global view
- [ ] Commute-aware opportunity scoring: enhance existing home-to-job commute sorting (driving/transit modes, Maps API fallback handling, caching, remote/hybrid rules)

## Clients, ESN & Contacts

- [x] Client management for final clients and intermediaries/ESNs
- [x] Client final/intermediary distinction through the `isFinal` flag
- [x] Contact management linked to both client and freelancer
- [x] Client rating and blacklist (payment delays, ghosting, bad process)
- [ ] ESN/intermediary margin tracking (rate paid by client vs rate received)
- [ ] Contact interaction history (last contacted, response rate, preferred channel)
- [ ] Org chart linking contacts to clients and ESNs (who works with whom)
- [ ] Reminder to nurture dormant contacts (no exchange in 6 months)
- [ ] "On this day last year": resurface contacts/opportunities from the same period to re-engage
- [ ] Import contacts from LinkedIn / vCard / CSV

## Finance

- [x] Reversion rate calculator for net daily, monthly, and yearly revenue
- [x] Configurable income tax and max workable days with French public holidays
- [ ] Revenue forecast based on signed contracts and pipeline probability
- [ ] TJM benchmark by skill/seniority/region (anonymized aggregated data from users)
- [ ] Expense tracking (deductible expenses feeding the reversion calculator)
- [ ] Expand existing reversion rate calculator customization (deductible expenses, social contributions, status-specific assumptions, custom tax settings)
- [ ] URSSAF/social contribution estimation per status (micro-entreprise, EI, EURL, SASU, portage)
- [ ] Invoice generation from project data (rate x days worked, French mentions légales)
- [ ] Invoice status tracking (draft, sent, paid, overdue) with payment delay alerts
- [ ] Yearly summary export for the accountant (CSV/Excel)
- [ ] Multi-currency support for international clients

## Calendar & Scheduling

- [ ] In-app calendar with interview reminders (already in roadmap - implement)
- [ ] Two-way sync with Google Calendar / Outlook Calendar and external notification systems
- [ ] Availability sharing link (let recruiters book a slot, Calendly-style)
- [ ] Interview preparation checklist attached to each scheduled step
- [ ] Timeline view of all upcoming steps across all opportunities

## Communications

- [ ] Email templates (follow-up, rate proposal, availability announcement) with variables
- [ ] AI polish of outgoing messages (grammar/tone fix before sending a follow-up or proposal)
- [ ] Gmail/Outlook inbox sync: auto-link emails to the matching project/contact
- [ ] Follow-up sequences (auto-remind me to relance after N days without reply)
- [ ] Email open/reply tracking on sent follow-ups
- [ ] Centralized message history per opportunity (email + notes + LinkedIn exchanges)

## Job Board Integrations

- [ ] Use OpenClaw to automate job posting for platforms without an API
- [ ] Web scraping and data fetching based on some criteria
- [ ] Freework / Malt / LinkedIn / CherryPick connectors (already in roadmap - implement)
- [ ] Unified job feed with dedup across boards
- [ ] Saved searches with notification on new matching postings
- [ ] One-click import of a job posting into the pipeline (URL paste -> parsed project)
- [ ] Chrome extension to capture postings from any site (already in roadmap - implement)

## AI & Automation

- [ ] AI parsing of job descriptions (PDF/URL/email -> structured project: rate, stack, location, duration, suggested skill tags)
- [ ] AI-suggested daily rate based on stack, seniority and market data
- [ ] AI job-fit analysis: which job is more adapted to your profile, pros vs cons per opportunity, including soft criteria in decision making (management style, team spirit, ...)
- [ ] Project recommendation engine combining peer-shared opportunities, skill match, market data, and soft criteria
- [ ] Auto-adapt CV to postulate for each job with adapted tech stack and experiences
- [ ] AI-generated personalized follow-up and application messages
- [ ] AI chat scoped to a project: the opportunity (description, notes, history) becomes the conversation context — interview prep, negotiation angles, post-mortem; history stored per project
- [ ] Semantic search across past opportunities and notes (by meaning, not just keywords)
- [ ] CV/Dossier de compétences generator tailored to a specific opportunity
- [ ] Automated status update suggestions from email content and user behavior (e.g. detect rejection, interview invite, or likely stale opportunity)
- [ ] Weekly digest: pipeline summary, actions due, market trends
- [ ] Bring-your-own API key support with per-user preferred model; different models per task type to optimize token cost
- [ ] Local LLM feature for more privacy

## Analytics & Reporting

- [x] Dashboard overview with total projects, average daily rate, estimated revenue, active projects, and recent projects
- [x] Dashboard charts for projects by status, projects by work mode, and daily rate distribution
- [x] Overview/Kanban display modes on the dashboard
- [ ] Dashboard period tagging with `YYYY-MM` labels, sprint-style but flexible so reporting periods can exist only when needed instead of forcing every month
- [ ] Conversion funnel by source, client type and ESN (where do opportunities die?)
- [ ] Average process duration per client/ESN (time from contact to signature)
- [ ] Daily rate evolution over time (asked vs obtained)
- [ ] Extend existing dashboard graphs and stats (projects by status, work mode distribution, daily rate chart, trend comparisons)
- [ ] Bench time tracking (gaps between missions, cost of bench)
- [ ] Source ROI ranking (which job board/contact yields signed contracts)
- [ ] Prospection activity heatmap (calendar view of applications/calls/interviews to spot active and quiet periods)
- [ ] Skill demand trends: which skills/stacks appear most in captured opportunities over time, and at what rates
- [ ] Personal yearly review (missions, revenue, top skills, network growth), exportable as a polished PDF report

## User Experience

- [x] Reusable filter components for advanced search, comprehensive filters, date ranges, multi-select filters, range sliders, and filter presets
- [x] French and English translations with `@ngx-translate`
- [x] Language switching and saved language preference loading
- [ ] Global search (projects, clients, contacts, notes) with keyboard shortcut and match highlighting in results
- [ ] Consistent error handling and user feedback (toasts instead of pop-ups, explicit loading and empty states)
- [ ] Composable filters (text + tags + status + date + archive) with state encoded in the URL for bookmarkable views
- [ ] Stable deep links to any project or Kanban card (?project=<id>) for sharing and reopening directly
- [ ] Multi-select mode on project/client/contact lists (bulk delete, tag, status change, archive)
- [ ] Internationalization hardening (French/English translation coverage, language switch persistence, missing-key checks)
- [ ] Dark mode
- [ ] Customizable dashboard widgets (drag, resize, show/hide)
- [ ] Onboarding wizard for first-time setup (profile, rate, address, pipeline template)
- [ ] Keyboard shortcuts for power users (new project, move card, search)
- [ ] Offline-tolerant PWA mode for consulting data on the go
- [ ] Accessibility pass (aria-labels, screen reader support, keyboard-only navigation)

## Data & Backup

- [ ] Data export/import (full account backup as JSON/CSV), with preview-first import: parsed items shown before commit, duplicates flagged, result reports imported/skipped
- [ ] Scheduled automatic backup to Google Drive / OneDrive / Dropbox (OAuth connect, skip upload when nothing changed, manual Sync Now)

## Mobile

- [ ] Responsive polish of all pages (Kanban usable on phone)
- [ ] Cross-platform mobile app (already in roadmap - implement)
- [ ] Push notifications (interview reminder, follow-up due, new matching posting)

## Social & Community

- [ ] Freelancer network: share opportunities you decline with peers
- [ ] Co-optation tracking (who referred whom, referral bonuses)
- [ ] Community TJM and client reviews (anonymized)
- [ ] Public profile page (shareable mini-portfolio with availability status)
- [ ] Feature request / community board with upvoting (already in roadmap - implement)

## Product & Website

- [ ] Landing page with features presentation, screenshots and sign-up call to action
- [ ] Terms of Service, Privacy Policy, and Contact/Support pages with FAQ
- [ ] Subscription/freemium model: premium features (AI, integrations) behind a paywall with free trial; billing management in profile
- [ ] Admin console (usage monitoring, user moderation, AI model/cost management, app settings)

## Platform & Security

- [x] User registration through `/api/auth/register`
- [x] User login through `/api/auth/login`
- [x] JWT creation on successful authentication
- [x] Frontend route protection through `authGuard`
- [x] Bearer token attachment through the Angular auth interceptor
- [x] Profile surface for account, preferences, notifications, and security-related fields
- [ ] Harden backend security config (currently permissive for non-auth routes)
- [ ] Authentication + Gmail / Outlook / LinkedIn authentication
- [ ] Email validation and password reset flows
- [ ] Two-factor authentication (TOTP)
- [ ] Account deletion (soft-delete with confirmation) and GDPR data export (droit à la portabilité)
- [ ] Audit log of account activity and active session management
- [ ] Rate limiting and brute-force protection on auth endpoints
- [ ] API tokens for third-party integrations (read-only scopes)
- [ ] Notification preferences per channel (email, push, in-app) and per event type

## Technical Improvements

- [x] Modernize codebase with latest Java and Angular versions/features
- [x] Split the docs into dedicated files and enrich them (development, deployment, ADRs, features, etc.), reusing existing content
- [ ] Complete the deployment setup (Kubernetes manifests, Jenkins pipeline, ...)
- [ ] Improve the devcontainer setup
- [ ] Measure and raise test coverage to at least 85% for both backend and frontend, with a focus on critical business logic and edge cases
- [ ] Playwright e2e tests for critical user flows (register, login, create project, move Kanban card, filter lists)
- [x] Add Spring Boot Actuator: K8s probes already reference /api/actuator/health but the actuator dependency is missing from pom.xml
- [ ] Richer database seeding for development (realistic projects, clients, pipeline states)
- [ ] Mask commands to reset/reseed the dev database with test data
- [ ] Caching of frequent requests (dashboard stats, job feed)
- [ ] Lazy loading of routes/components for faster initial load
- [ ] Feature flags to enable/disable features without redeploying
- [ ] Structured JSON logging, request tracing, monitoring and alerting
- [ ] Usage telemetry to learn which features are used and prioritize development
- [ ] Automated DB backups with point-in-time recovery; versioned migrations; connection pooling
- [ ] CI: tests on every PR, scheduled e2e runs, dependency vulnerability scanning, zero-downtime deployments
- [ ] Keep OpenAPI docs complete with request/response examples

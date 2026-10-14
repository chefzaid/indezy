# TODO

## Bugs to fix

Bugs, failing checks, security findings and code smells found while working on another task. Each entry names the file or area, what is wrong, and how to reproduce it.

- [ ] `indezy-web` test coverage counts only the files some spec imports, so untested files are invisible instead of reported at 0%. `project-list`, `project-detail`, `project-form`, `profile`, `dashboard`, `client-*`, `contact-*`, `source-*` and `login` components (about 40 of the 87 source files; `register` is counted since it gained a spec) are missing from `coverage/cobertura-coverage.xml`, so the reported 88.8% statements overstates real coverage. Reproduce: `npm run test:ci -- --code-coverage`, then `grep -c project-list.component.ts coverage/cobertura-coverage.xml` prints 0.
- [x] `GlobalExceptionHandler` (`indezy-server/.../exception/GlobalExceptionHandler.java`) logs client errors at `ERROR` and copies rejected values into the logs: `handleValidationExceptions` logs the full `MethodArgumentNotValidException` message (every rejected field value, such as emails and phone numbers), and `handleDataIntegrityViolation` logs the database message, which quotes the conflicting key values. Reproduce: post an invalid client to `/api/clients` and read the `Validation error: ... rejected value [...]` line in the server log. Fixed: client errors log at `WARN` with only the field and constraint, the parameter, or the database constraint and SQL state (an `IllegalArgumentException` by the method that threw it), never the exception or database message, while unexpected errors stay at `ERROR` with their stack trace; `GlobalExceptionHandlerTest` and `ContactControllerTest` check the captured logs and `docs/security.md` states the rule.
- [ ] `GlobalExceptionHandler.handleGenericException` (`indezy-server/.../exception/GlobalExceptionHandler.java`) also catches the Spring MVC request errors that have no dedicated handler (`HttpMediaTypeNotSupportedException`, `HttpMediaTypeNotAcceptableException`, `ServletRequestBindingException` such as a missing header, `HandlerMethodValidationException`), so these client errors return `500` and are logged at `ERROR` with a stack trace instead of keeping their 4xx status. Reproduce: `POST /api/contacts` with `Content-Type: text/plain` returns `500` and logs `ERROR ... Unexpected error: Content-Type 'text/plain' is not supported`.
- [x] `AuthController` (`indezy-server/.../controller/AuthController.java`) logs personal data and treats client errors as server faults: a wrong password logs `ERROR Login failed for email: <email>` with a full stack trace, a locked account logs the email at `WARN`, and a duplicate registration logs `ERROR Registration failed for email: <email>` with a stack trace. Reproduce: `POST /api/auth/login` with an existing email and a wrong password, then read the server log. Fixed: a wrong email, password or two-factor code, a locked account and an existing account at registration (now a dedicated `AccountAlreadyExistsException`) log at `WARN` without the email or a stack trace, any other `IllegalArgumentException` at registration at `WARN` naming only where it was thrown, a database constraint error from two requests creating the same account at `WARN` with the constraint name and SQL state (shared with `GlobalExceptionHandler` through `ExceptionLogDetails`) but without the database message, a rejected Keycloak SSO exchange at `WARN` with its fixed or token-validation reason, and only unexpected faults at `ERROR` with their stack trace; the auth debug lines no longer carry the email (the user ID once authenticated), responses and failed-attempt counting are unchanged, `AuthControllerTest` and `AuthLoggingIntegrationTest` check the captured logs, and `docs/security.md` states the rule.
- [ ] Production logs personal data at `DEBUG`: `application.yml` sets `dev.swirlit.indezy` to `DEBUG` and the `kubernetes` profile does not lower it, while `ContactService` logs contact first and last names and `ClientService`/`SourceService` the submitted company or source name (the auth flow no longer logs the email since the `AuthController` fix above). Reproduce: run the server with the `kubernetes` profile, create a contact, and read the `Creating new contact: <first name> <last name>` line.
- [x] `LoginRequest`, `RegisterRequest` and `PasswordChangeRequestDto` (`indezy-server/.../dto/`) use Lombok `@Data`, whose generated `toString` prints the password, and Spring MVC logs each deserialized request body at `DEBUG`: whenever `org.springframework.web` is at `DEBUG` (the `test` profile sets it in `application-test.properties`, and an operator may raise it to diagnose a deployment), the log receives the email and the first characters of the password (`Read "application/json;charset=UTF-8" to [LoginRequest(email=..., password=Password (truncated)...]`), and the test client's `Writing [...]` line prints the whole password. Production keeps `org.springframework.web` at `WARN`, so it is a latent leak. Reproduce: `mvn -B test -Dtest=WorkspaceIsolationIntegrationTest -Djacoco.skip=true`, then `grep -c 'LoginRequest(email=\|RegisterRequest(firstName=' target/surefire-reports/TEST-dev.swirlit.indezy.integration.WorkspaceIsolationIntegrationTest.xml` prints a non-zero count. Fixed: `LoginRequest`, `RegisterRequest`, `PasswordChangeRequestDto`, `TwoFactorSetupDto` and `LoginResponse` mark their passwords, two-factor code, TOTP secret, provisioning URI and JWT with `@ToString.Exclude`, and the auth requests and the login response's user also leave out the email and the names (a login is logged as `LoginRequest()`), with JSON unchanged; the `User` and `Freelance` entities generate no `toString`. `SensitiveToStringTest` scans the `dto` and `model` packages and fails when a class prints a field named like a password, hash, secret, token, two-factor or backup code or API key, or when such a class or one nested in it prints the email or a name; `AuthLoggingIntegrationTest` now runs with Spring MVC and the test client at `DEBUG`, posts the DTOs and checks that the whole log holds neither the email, the name nor the passwords; `WorkspaceIsolationIntegrationTest` posts a `RegisterRequest` instead of a map, so the reproduction prints 0 and its report no longer holds the password; `docs/security.md` states the rule.
- [x] `RegisterRequest.password` and `PasswordChangeRequestDto.newPassword` (`indezy-server/.../dto/`) have no maximum length, while BCrypt (Spring Security 7.1.1) throws `IllegalArgumentException("password cannot be more than 72 bytes")` when hashing a longer one. `AuthController.register` maps every `IllegalArgumentException` to `409 Conflict`, so registering a new email with an 80-character password returns `409` and the register page shows the "account already exists" error; a password change to such a value returns `400` with the BCrypt message instead of a validation error. Reproduce: `POST /api/auth/register` with a new email and an 80-character password returns `409` and logs `WARN Registration rejected at org.springframework.security.crypto.bcrypt.BCrypt...`. Fixed: a `@PasswordMaxBytes` constraint (new `validation` package) checks the UTF-8 byte length, not the character count, of `RegisterRequest.password` and `PasswordChangeRequestDto.newPassword`, so a password over 72 bytes (80 ASCII characters, or only 37 accented letters) gets a `400` validation error on its field before BCrypt is reached, and `AuthController.register` answers `400` instead of `409` for any other non-duplicate `IllegalArgumentException`; the register and profile password forms apply the same limit through a shared `passwordMaxBytes` validator with a translated FR/EN field error (`Maximum 72 bytes (é = 2, emoji = 4)`), and the minimum lengths are unchanged. `PasswordMaxBytesTest`, `PasswordLengthIntegrationTest`, `AuthControllerTest`, `register.component.spec.ts`, `password-max-bytes.spec.ts` and the FR/EN Playwright scenarios in `e2e/auth.spec.ts` cover it; `test-data.sql` now restarts the users identity after its seeded user, so a registration through the API in a reused test context no longer collides with id 1; `docs/security.md` states the password policy.
- [ ] `indezy-server` `mvn -B verify` fails at `jacoco:check` on `main`: bundle branch coverage is 70% against the 80% minimum in `pom.xml` (and the 85% target of the coding workflow). CI hides it because `infra/scripts/ci-verify.sh` passes `-Djacoco.haltOnFailure=false`, and locally, because `jacoco:check` runs before `spotbugs:check` in the `verify` phase, a full verify never reaches SpotBugs. The largest gaps are `ProjectService` (38 missed branches), `ContactImportService` (30), `UserMapperImpl` (22), `DashboardAnalytics` (19) and `ContactMapperImpl` (19). Reproduce: `mvn -B verify` ends with `Rule violated for bundle indezy-server: branches covered ratio is 0.70, but expected minimum is 0.80`.
- [ ] `indezy-web` function coverage is 80% (280/350), below the 85% target, even before the files that the coverage report leaves out (first entry of this list) are counted. Reproduce: `npm run test:ci -- --code-coverage` and read the `Functions` line of the summary.
- [ ] `UserController.verifyTwoFactor` and `disableTwoFactor` (`indezy-server/.../controller/UserController.java`) read their body as a `Map<String, String>`, whose `toString` Spring MVC logs at `DEBUG`, so the two-factor code reaches the log (`Read "application/json;charset=UTF-8" to [{code=782294}]`) whenever `org.springframework.web` is at `DEBUG` (the `test` profile, or an operator diagnosing a deployment). The code is short-lived but is a credential, and the auth DTOs no longer print theirs. Reproduce: `mvn -B test -Dtest=UserManagementIntegrationTest -Djacoco.skip=true`, then `grep -c 'to \[{code=' target/surefire-reports/TEST-dev.swirlit.indezy.integration.UserManagementIntegrationTest.xml` prints 2.
- [ ] `ContactDto`, `FreelanceDto` and `UserDto` (`indezy-server/.../dto/`) use Lombok `@Data`, so Spring MVC prints names, emails and phone numbers in its `DEBUG` body lines (`Writing [ContactDto(id=1, firstName=Jane, lastName=Smith, email=jane.smith@example.com, phone=0123456789, ...]`) whenever `org.springframework.web` is at `DEBUG`, against the `docs/security.md` rule to avoid logging request bodies that hold personal data. Production keeps it at `WARN`, so it is latent; the secrets and the auth DTOs no longer print. Reproduce: `mvn -B test -Dtest=FreelanceIntegrationTest -Djacoco.skip=true`, then `grep -c 'Writing \[FreelanceDto(id=[0-9]*, firstName=' target/surefire-reports/TEST-dev.swirlit.indezy.integration.FreelanceIntegrationTest.xml` prints a non-zero count.
- [ ] Spring MVC's `ExceptionHandlerExceptionResolver` logs every exception it resolves at `DEBUG` with its message (`Resolved [org.springframework.web.bind.MethodArgumentNotValidException: ... rejected value [...]]`), so each validation failure prints the rejected values, passwords and emails included, although `GlobalExceptionHandler` itself logs only the field and constraint. Production keeps `org.springframework.web` at `WARN`, so it is latent, like the `@Data` body lines above. Reproduce: `mvn -B test -Dtest=PasswordLengthIntegrationTest -Djacoco.skip=true`, then `grep -c 'rejected value \[aaaa' target/surefire-reports/TEST-dev.swirlit.indezy.integration.PasswordLengthIntegrationTest.xml` prints a non-zero count (the rejected 80-character password); a registration with a 5-character password prints that password the same way.
- [x] `PasswordChangeRequestDto.newPassword` (`indezy-server/.../dto/PasswordChangeRequestDto.java`) has no minimum length on the server: the profile form requires 8 characters, but the API accepts any non-blank new password, so the rule is client-side only (`docs/security.md`: frontend checks are not a security boundary). Reproduce: authenticate, then `POST /api/users/change-password` with `{"currentPassword": "<current>", "newPassword": "a", "confirmPassword": "a"}` returns `200 true` and the account's password becomes `a`. Fixed: `PasswordChangeRequestDto.newPassword` now has `@Size(min = 8)`, so a shorter new password answers `400` with a validation error on `newPassword` (`New password must be at least 8 characters long`) and the current password is kept, while `@PasswordMaxBytes` still caps it at 72 bytes; the server already rejected a confirmation that differs (`400`, `New password and confirmation do not match`), now covered over HTTP, and registration keeps its 6-character minimum (see Needs Input). The profile form and its error handling are unchanged: a server rejection shows the translated `Error changing password` toast and keeps the typed values. `PasswordMaxBytesTest` (next to the DTO's 72-byte cases), `PasswordLengthIntegrationTest` (`a` and 7 characters refused, the old password still signs in, 8 characters accepted; a mismatched confirmation refused) and the FR/EN Playwright scenario in `e2e/auth.spec.ts` cover it; `docs/security.md` states the policy.
- [x] `register.component.html` (`indezy-web/src/app/components/auth/register/`) gives the password visibility buttons the English literals `'Hide password'` and `'Hide confirm password'` as `aria-label`, so French screen-reader users hear English, while the login page uses the translated `auth.hidePassword`. Reproduce: open `/register` in French and read the accessible name of the eye button next to `Mot de passe`. Fixed: the buttons use the login page's pattern, the translated `auth.hidePassword` and a new `auth.hideConfirmPassword` (`Masquer la confirmation du mot de passe` / `Hide password confirmation`) as their name with `aria-pressed` reporting the state; `register.component.spec.ts` renders the page with the real French and English files and the FR/EN Playwright scenarios in `e2e/auth.spec.ts` find both buttons by role and translated name; `docs/features.md` extends the no-hard-coded-strings rule to attribute text.
- [ ] Two placeholders in `indezy-web` are literals instead of translation keys, found by scanning the templates for untranslated `aria-label`, `title`, `placeholder` and `matTooltip` text: `login.component.html` (`components/auth/login/`) shows `votre@email.com` in the email field in English too, and `project-form.component.html` (`components/projects/project-form/`) shows `Ex: React, Node.js, PostgreSQL` in the tech stack field in both languages, while the form's other placeholders use translated keys (`e.g.: Full Stack Developer` / `Ex: Développeur Full Stack`). Reproduce: switch the app to English, open `/login` and read the email field placeholder, then open `/projects/new` and read the tech stack placeholder.
- [ ] `profile.component.ts` (`indezy-web/src/app/components/profile/`) reports every failed password change with the generic `profile.errors.changingPassword` toast (`Error changing password` / `Erreur lors du changement de mot de passe`), so a user who mistypes the current password is not told why, although `POST /api/users/change-password` answers `400` with `Current password is incorrect`; the API only tells this case apart from a rejected confirmation by the English message text, so the fix needs a stable error code from the server and a translated FR/EN field error. Reproduce: open `/profile`, Security tab, enter a wrong current password and a valid new password twice, submit, and read the toast.
- [ ] `indezy-server` Checkstyle prints 27 warnings that `mvn verify` does not fail on (0 violations, so they go unnoticed): 4 unused imports (`CommuteController`, `InterviewStepService`, `ModelHelpersTest`, `UserServiceTest`), 18 operators wrapped at the start of a line instead of the end (`OperatorWrap`, in `AccessGuard`, `AuthService`, `ContactImportService`, `ContactService`, `DashboardAnalytics`, `JwtAuthenticationFilter`, `KeycloakSsoConfig`, `ProjectExportService`, `ProjectRepository`, `ProjectStatusMigration` and their tests), 3 inner assignments in `ContactImportService` and 2 methods with more than 7 parameters (`AccessGuard`, `ProjectExportServiceTest`). Reproduce: `mvn -B checkstyle:check` and count the `[WARN]` lines.

## Needs Input

Parts of tasks that cannot advance without the owner's decision or information. Only the part needing input is set aside here; the rest of the task keeps moving. Answer under the item, and the next agent run applies the answer and removes the entry.

- [ ] Should registration also require at least 8 characters? A password change now requires 8 on the server, as the profile form does, while registration (`RegisterRequest` and the register form) still accepts 6, so a new account can have a password the profile page would refuse as a new one. Options: (a) keep 6 for registration and 8 for a password change; (b) raise both to 8 (`RegisterRequest`, the register form and its FR/EN message), existing accounts keep signing in with their current password until they change it; (c) another minimum for both (please give the value). *(task: Bugs to fix › password change minimum length; asked by: Coding Agent)*
  - Answer:

## Pipeline & Kanban

Not all freelance jobs share the same recruitment process:

Some have 3 steps, others 5, with different styles and ordering. For example, a technical test can happen before or after an interview.

The board uses generic columns: Contact => Tests & Entretiens => Offre => Accepté => Perdu / Abandonné. Each Kanban item keeps the intermediate step it is in (interview steps) and the scheduled next step while staying in the same column.

- [x] Interview step tracking per project
- [x] Step scheduling with date/time and notes
- [x] Step statuses: `TO_PLAN`, `PLANNED`, `CANCELED`, `WAITING_FEEDBACK`, `VALIDATED`, `FAILED`
- [x] Kanban mode for moving opportunities across high-level statuses
- [x] Five generic Kanban columns: Contact, Tests & Entretiens, Offre, Accepté, Perdu / Abandonné (existing opportunities migrated automatically)
- [x] Drag-and-drop status changes from the Kanban board
- [ ] Customizable pipeline templates per client/ESN (save a recruitment process and reuse it)
- [ ] Bulk actions on Kanban cards (archive, change status, assign source)
- [x] Card aging indicators (highlight opportunities with no activity for X days)
- [x] Drag-and-drop reordering within a column (manual priority)
- [x] Quick-add card directly from the Kanban board (minimal form, enrich later)
- [x] Pinned/favorite opportunities (keep hot leads at the top, dedicated view)
- [x] Lost/rejected reason tracking (rate too low, position filled, no response...) with stats
- [x] Automatic stale-opportunity detection and suggested follow-up or archival
- [x] Configurable inactivity period: the user sets in their preferences after how many days without activity an opportunity counts as stale (14 days by default, shared by backend reminders and the Kanban board)
- [ ] Automatic archiving of opportunities with no contact for that period (instead of only flagging them), with an unarchive action from the archive view and the project page that puts the opportunity back in its pipeline column
- [x] Duplicate opportunity detection (same client + same role posted on several job boards), flagged for manual deduplication

## Opportunities & Projects

- [x] Freelancer profile model with identity, availability, employment status, reversion rate, income tax rate, and CV path fields
- [x] Freelance-owned workspace for projects, clients, contacts, and sources
- [x] Project/opportunity management with role, status, description, tech stack, daily rate, work mode, dates, duration, notes, documents, link, rating, client, middleman, source, and freelance owner
- [x] Opportunity statuses: `CONTACT`, `INTERVIEW`, `OFFER`, `WON`, `LOST`
- [x] Work modes: `ONSITE`, `REMOTE`, `HYBRID`
- [x] Project helper behavior for total revenue and work-mode checks
- [x] Source management for opportunity origins
- [x] Source types: `JOB_BOARD`, `SOCIAL_MEDIA`, `EMAIL`, `CALL`, `SMS`
- [x] Source ratings for popularity and usefulness
- [x] Commute-time sorting support using home address and job location
- [x] Google Maps Distance Matrix API integration for driving and transit modes
- [x] Graceful commute fallback when no Google Maps API key is configured
- [ ] Attachments per project (job description PDF, contract, technical test brief) with inline preview (image, PDF) instead of forcing downloads
- [x] Notes/journal timeline per project (every call, email, decision logged chronologically), with Markdown support and live preview
- [x] Note templates (call debrief, interview debrief, negotiation summary) selectable when adding a note
- [ ] Voice memo after a call: record, transcribe, attach the transcript as a project note
- [x] Skill tags on projects + match score against the freelancer's skill profile (with global tag rename across all projects)
- [ ] Richer skill profile: free-text skills (not only a fixed list), each with an optional level and years of experience, editable from the profile page
- [ ] Skill normalization: group equivalent spellings and aliases (e.g. "Spring Boot" / "SpringBoot" / "spring-boot", "JS" / "JavaScript") under one canonical skill, suggest merges when a similar skill is added, and apply the grouping to the profile, project tags, match score and skill trends
- [x] Daily rate negotiation history (asked / offered / agreed) per project
- [x] Project archive view with full-text search across past opportunities
- [x] Contract milestones tracking (start date, renewal date, notice period alerts)
- [x] Mission end-date reminders to restart prospection N weeks before the bench
- [x] Job-hunting seasons (prospection campaigns): separate pipeline and dashboard per job-search period (e.g. "Autumn search 2026"), each with its own stats, objective and target rate, comparable against the global view
- [ ] Commute-aware opportunity scoring: enhance existing home-to-job commute sorting (driving/transit modes, Maps API fallback handling, caching, remote/hybrid rules)

## Clients, ESN & Contacts

- [x] Client management for final clients and intermediaries/ESNs
- [x] Client final/intermediary distinction through the `isFinal` flag
- [x] Contact management linked to both client and freelancer
- [x] Client rating and blacklist (payment delays, ghosting, bad process)
- [x] ESN/intermediary margin tracking (rate paid by client vs rate received)
- [ ] Contact interaction history (last contacted, response rate, preferred channel)
- [ ] Org chart linking contacts to clients and ESNs (who works with whom)
- [x] Reminder to nurture dormant contacts (no exchange in 6 months)
- [x] "On this day last year": resurface contacts/opportunities from the same period to re-engage
- [x] Import contacts from LinkedIn / vCard / CSV

## Finance

- [x] Reversion rate calculator for net daily, monthly, and yearly revenue
- [x] Configurable income tax and max workable days with French public holidays
- [x] Revenue forecast based on signed contracts and pipeline probability
- [ ] TJM benchmark by skill/seniority/region (anonymized aggregated data from users)
- [x] Target daily rate (TJM) as a profile setting, used as the default for new seasons and shown against obtained rates on the dashboard
- [ ] Expense tracking (deductible expenses feeding the reversion calculator)
- [ ] Expand existing reversion rate calculator customization (deductible expenses, social contributions, status-specific assumptions, custom tax settings)
- [ ] URSSAF/social contribution estimation per status (micro-entreprise, EI, EURL, SASU, portage)
- [ ] Invoice generation from project data (rate x days worked, French mentions légales)
- [ ] Invoice status tracking (draft, sent, paid, overdue) with payment delay alerts
- [x] Yearly summary export for the accountant (CSV/Excel)
- [ ] Multi-currency support for international clients

## Calendar & Scheduling

- [ ] In-app calendar with interview reminders (already in roadmap - implement)
- [ ] Two-way sync with Google Calendar / Outlook Calendar and external notification systems
- [ ] Availability sharing link (let recruiters book a slot, Calendly-style)
- [ ] Interview preparation checklist attached to each scheduled step
- [x] Timeline view of all upcoming steps across all opportunities

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
- [x] Finer daily rate chart: plot the actual TJM values the freelancer negotiated (e.g. 550, 600, 650 €) instead of wide ranges (0-300, 300-500, ...), as one freelancer's rates vary within a narrow band
- [x] Overview/Kanban display modes on the dashboard
- [ ] Dashboard period tagging with `YYYY-MM` labels, sprint-style but flexible so reporting periods can exist only when needed instead of forcing every month
- [x] Conversion funnel by source, client type and ESN (where do opportunities die?)
- [x] Average process duration per client/ESN (time from contact to signature)
- [x] Daily rate evolution over time (asked vs obtained)
- [ ] Extend existing dashboard graphs and stats (projects by status, work mode distribution, daily rate chart, trend comparisons)
- [x] Bench time tracking (gaps between missions, cost of bench)
- [x] Source ROI ranking (which job board/contact yields signed contracts)
- [x] Prospection activity heatmap (calendar view of applications/calls/interviews to spot active and quiet periods)
- [x] Skill demand trends: which skills/stacks appear most in captured opportunities over time, and at what rates
- [ ] Personal yearly review (missions, revenue, top skills, network growth), exportable as a polished PDF report

## User Experience

- [x] Consistent inline filters on every list (search, type/status/season, sort), collapsible on small screens
- [x] French and English translations with `@ngx-translate`
- [x] Language switching and saved language preference loading
- [ ] Global search (projects, clients, contacts, notes) with keyboard shortcut and match highlighting in results
- [x] Consistent error handling and user feedback (toasts instead of pop-ups, explicit loading and empty states)
- [ ] Composable filters (text + tags + status + date + archive) with state encoded in the URL for bookmarkable views
- [x] Stable deep links to any project or Kanban card (?project=<id>) for sharing and reopening directly
- [ ] Multi-select mode on project/client/contact lists (bulk delete, tag, status change, archive)
- [x] Internationalization hardening (French/English translation coverage, language switch persistence, missing-key checks)
- [x] Dark mode (light, dark, or system theme from the profile preference and a toolbar toggle)
- [ ] Customizable dashboard widgets (drag, resize, show/hide)
- [ ] Onboarding wizard for first-time setup (profile, rate, address, pipeline template)
- [x] Keyboard shortcuts for power users (new project, move card, search)
- [ ] Offline-tolerant PWA mode for consulting data on the go
- [ ] Accessibility pass (aria-labels, screen reader support, keyboard-only navigation)

## Data & Backup

- [ ] Data export/import (full account backup as JSON/CSV), with preview-first import: parsed items shown before commit, duplicates flagged, result reports imported/skipped
- [ ] Scheduled automatic backup to Google Drive / OneDrive / Dropbox (OAuth connect, skip upload when nothing changed, manual Sync Now)

## Mobile

- [x] Responsive polish of all pages (Kanban usable on phone)
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
- [x] Harden backend security config (currently permissive for non-auth routes)
- [ ] Authentication + Gmail / Outlook / LinkedIn authentication
- [ ] Email validation and password reset flows
- [x] Two-factor authentication (TOTP) — currently stubbed (random UUID secret, placeholder QR, length-only code check); implement real secret generation, QR/URI, and code validation
- [x] Replace hardcoded user/freelance IDs with the authenticated principal from `SecurityContext` (backend `UserController.CURRENT_USER_ID = 1`, and `freelanceId = 1` in the Angular contact service) once authentication is wired through
- [x] Account deletion (soft-delete with confirmation) and GDPR data export (droit à la portabilité)
- [ ] Audit log of account activity and active session management
- [x] Rate limiting and brute-force protection on auth endpoints
- [ ] API tokens for third-party integrations (read-only scopes)
- [ ] Notification preferences per channel (email, push, in-app) and per event type
- [ ] Social login (Google, Microsoft, GitHub) through Keycloak identity providers in the `swirlit` realm, so the buttons appear on the Keycloak login page and accounts reach Indezy through the existing SSO exchange, with no login code in the app
- [ ] Custom Keycloak login theme matching Indezy (logo, colors, typography, light/dark, French and English texts) instead of the default Keycloak look

## Technical Improvements

- [x] Modernize codebase with latest Java and Angular versions/features
- [x] Split the docs into dedicated files and enrich them (development, deployment, ADRs, features, etc.), reusing existing content
- [ ] Complete the deployment setup (Kubernetes manifests, GitLab CI pipeline, ...)
- [ ] Improve the devcontainer setup
- [ ] Measure and raise test coverage to at least 85% for both backend and frontend, with a focus on critical business logic and edge cases
- [ ] Playwright e2e tests for critical user flows (register, login, create project, move Kanban card, filter lists)
- [x] Add Spring Boot Actuator: K8s probes already reference /api/actuator/health but the actuator dependency is missing from pom.xml
- [x] Richer database seeding for development (realistic projects, clients, pipeline states)
- [x] Mask commands to reset/reseed the dev database with test data
- [ ] Caching of frequent requests (dashboard stats, job feed)
- [ ] Lazy loading of routes/components for faster initial load
- [ ] Feature flags to enable/disable features without redeploying
- [ ] Structured JSON logging, request tracing, monitoring and alerting
- [ ] Usage telemetry to learn which features are used and prioritize development
- [ ] Automated DB backups with point-in-time recovery; versioned migrations; connection pooling
- [ ] CI: tests on every PR, scheduled e2e runs, dependency vulnerability scanning, zero-downtime deployments
- [ ] Keep OpenAPI docs complete with request/response examples
- [ ] Ensure that all API endpoints are effectively used in the UI (either the app or the admin console). Implement the frontend that would use them if necessary, or remove the endpoints.

## Product Direction (added 2026-10-04)

Indezy tracks a French tech freelancer's mission search. Generic CRMs, job boards and invoicing tools each cover one piece; nothing covers the whole French freelance-IT cycle. The niche to own is **the operating system of French IT freelancers**: find the mission, win it at the right rate, then run it — monthly activity reports (CRA), the compliance documents ESNs require, invoices that meet the 2026–2027 e-invoicing rules, social contributions and taxes — with market data no single freelancer has. A Teams tier serves the organizations around freelancers: collectives, portage companies and boutique ESNs managing a pool of consultants.

Differentiators, each covered below:

1. French-specific end to end: ESN processes, TJM, CRA, vigilance certificates, Factur-X, URSSAF.
2. Market intelligence from anonymized aggregates: rates, ESN process speed, payment delays.
3. Freelancer-owned data, exportable at any time, hosted in the EU.
4. A Teams tier with shared pipelines and consultant availability.

### Mission Operations (after signature)

- [ ] Monthly activity report (CRA) per mission: calendar of full and half days, on-site or remote, public holidays and absences; validation by the client or ESN through a signed link; PDF in the ESN's template; validated days feed the invoice.
- [ ] Compliance document vault: URSSAF vigilance certificate (renewed every six months), company registration extract, professional liability insurance, bank details and ID, with expiry reminders and a one-link pack to send to an ESN.
- [ ] Purchase orders and amendments per mission (number, amount or day ceiling, period), consumption tracked against CRA days, with an alert before the ceiling.
- [ ] Electronic signature of contracts, amendments and CRAs through an EU qualified trust provider.

### Invoicing and Compliance

- [ ] Factur-X invoices (PDF/A-3 with embedded structured data) from validated CRA days and mission rates, with French mandatory mentions, VAT or VAT-exemption wording and gapless numbering (extends the invoice items above).
- [ ] French B2B e-invoicing: every company must receive electronic invoices from 2026-09-01, and SMEs and micro-enterprises must issue them from 2027-09-01. Connect through a registered partner platform (PDP), send and receive invoices, follow their lifecycle statuses and handle e-reporting where the status requires it.
- [ ] Payment reconciliation through a regulated bank-account information provider or manual matching, with statutory late-payment interest and the fixed recovery indemnity computed on overdue invoices.
- [ ] Accounting exports and connectors for common French accounting tools.
- [ ] Declaration calendar per legal status (URSSAF declarations, VAT returns, CFE, income-tax instalments) with amounts estimated from invoices (builds on the URSSAF estimation item above).

### Market Intelligence

- [ ] **ADR — aggregated data:** opt-in contribution, publication only of aggregates over a minimum number of freelancers, no client or contact identities, GDPR legal basis and withdrawal. Feeds the TJM benchmark and community reviews above.
- [ ] ESN scorecard: response time, process length, offer rate, payment delay and margin range per ESN from contributed data.
- [ ] Quarterly public market report of rates by skill, region and seniority.

### Teams Tier (collectives, portage companies, boutique ESNs)

- [ ] **ADR — organizations:** organization accounts with members and roles (owner, manager, consultant) and explicit data boundaries: what a consultant shares with the organization (availability, CV, selected pipeline items) and what stays private.
- [ ] Consultant availability board and shared opportunity pool: dispatch an opportunity to available members, see who is positioned where, and prevent two members being submitted to the same client.
- [ ] Organization dashboards: occupancy, bench, margins, renewals due, CRA and invoice status across consultants.
- [ ] Organization single sign-on through identity-provider brokering, with member provisioning and removal.
- [ ] White-label option for portage companies: logo, colors and custom domain.

### Commercial and Enterprise Foundation

- [ ] Plans and entitlements enforced server-side: free (pipeline), Pro (CRA, invoicing, e-invoicing, AI, integrations), Teams per seat (completes the subscription item above).
- [ ] GDPR for third-party data: recruiter and client contacts are personal data. Record the legal basis, provide a privacy notice usable in outgoing emails, anonymize contacts dormant beyond a retention period, and handle access and erasure requests from contacts.
- [ ] Public API and webhooks (opportunity, CRA and invoice events) with scoped tokens and a published OpenAPI description (extends the API tokens item above).
- [ ] EU hosting statement, processor list (AI, e-invoicing, banking, e-signature) and a data processing agreement for Teams customers.
- [ ] Help center and support with response targets per plan; a status page for paid users.

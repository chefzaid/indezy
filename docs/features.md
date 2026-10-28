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

The navigation lists, in this order: **Tableau de bord** (Dashboard), **Calendrier** (Calendar), **Offres** (Offers),
**Opportunités** (Opportunities, the pipeline of opportunities at `/projects`), **Clients**, **Contacts**, **Sources**,
**Bilan annuel** (Year in review), **Factures** (Invoices), **Documents**, **Archives**, then **Administration** for
administrators. Its icons and the app's Roboto font are bundled with the app rather than loaded from Google Fonts: the
service worker fetches what a page loads under the policy's `connect-src 'self'`, so a font from another host would
fail once it runs and the icons would show their names (see [Security](./security.md#web-server-headers)).

## Presentation Page

`https://indezy.swirlit.dev` opens the presentation page, in French at `/` and in English at `/en`: a first visit to
`/` from an English browser goes to `/en`, and **FR** / **EN** keep the language chosen. It shows the positioning (find
the mission, win it at the right rate, then run it) with a Kanban scene, what Indezy does today (the offer feed, the
pipeline, clients and contacts, activity reports, invoices and documents, the dashboard), how it works in five steps,
what is true of the data (only the account sees its workspace, everything can be exported, shared links show only what
is chosen), screens of the Kanban board, the offer feed, an activity report and the dashboard (in the page's language
and the visitor's light or dark theme, taken from a demo workspace whose people and offers are invented) and a few
questions. Signed out, **Sign in** opens Keycloak (the sign-in page outside production, plus
**Create my account** there: sign-up in production waits on the decision in TODO.md, Needs Input); signed in, **Open
the dashboard**. The page never sends a visitor to sign in by itself, shows without the app's menu, and an old shared
`/?project=<id>` link still opens its opportunity.

For search engines and link previews, each language has its title and description, its canonical address and the
other language's (`hreflang`), an Open Graph and Twitter card with `assets/brand/og-image.png`, and schema.org
`SoftwareApplication` data. After the build, `scripts/prerender-home.mjs` writes `home-fr.html` and `home-en.html`: the
built `index.html` with these tags and the page's text, which nginx serves for `/` and `/en`, so the content is there
without JavaScript; the app replaces it once it starts. `robots.txt` and `sitemap.xml` list these two pages only.
The screens are taken again with `npx playwright test -c pw.screenshots.config.ts` (in `indezy-web`): it opens the app
on the demo workspace of `scripts/screenshots/demo-workspace.ts` and writes `src/assets/screens/<screen>-<language>-<theme>.jpg`.

## Authentication And Account Access

Implemented account flows:

- user registration through `/api/auth/register`
- password rules checked by the forms and the API: at least 8 characters at registration and for a new password on the profile page, a confirmation that matches the new password, and at most 72 bytes, the BCrypt limit, where an accented letter counts as 2 and an emoji as 4 (see [Security](./security.md#password-storage))
- user login through `/api/auth/login`
- JWT creation on successful authentication
- frontend route protection through `authGuard`
- bearer token attachment through the Angular auth interceptor
- Keycloak single sign-on in production (`/api/auth/sso`, see [Security](./security.md))
- optional TOTP two-factor authentication for local accounts, asked at login once enabled
- profile page for account details, avatar, preferences (language, theme, date format), notifications, password, two-factor authentication and a JSON export of every piece of account data
- **Import data** (Profile → Account): an Indezy export brought into the workspace, preview first (see below)

### Billing Identity

**Profile → Billing** holds what the workspace's invoices say of the seller: the legal name (the profile's name until
set) and form, the SIRET, the VAT number and rate or the VAT exemption (franchise en base, art. 293 B du CGI, the
default), the share capital and registration for a company, the billing address (the profile's address until set),
the IBAN and BIC, the payment term (30 days by default, 60 at most) and the prefix of the invoice numbers. Identifiers
can be typed in groups or lower case; an IBAN with wrong check digits, a SIRET without 14 digits or a malformed VAT
number or BIC is refused. The tab says whether invoices can be issued: a SIRET, an address and an IBAN, plus a VAT
number when VAT applies; with portage, the portage company invoices the clients, so Indezy does not. A client's
**Billing** section holds its SIREN and VAT number. The API is `GET` and `PUT /api/users/billing`; the identity is part
of the data export (`billing`).

### Invoices

A workspace invoices its clients from drafts:

- **From an activity report**: a month with days worked becomes a draft billed to the mission's ESN (else its
  client), in the language of that client's activity reports, with one line of the days worked at the mission's daily
  rate ("Prestation : Développeur Java — novembre 2026"), the month as the service period, and VAT at the billing
  identity's rate unless exempt. The month need not be validated through Indezy's link (clients often approve in their
  own tools), but a rejected month cannot be invoiced, nor one without days worked. A report is invoiced once.
- **By hand**: a client of the workspace (and one of its missions, optionally), a service period, the language (French
  or English), notes and up to 50 lines, each a description, a quantity (days, to the hundredth), a unit price before VAT
  and a VAT rate.

**Invoices** (in the navigation, `/invoices`) lists them newest first with their number (or "Draft"), client, mission,
period, due or payment date, status and total, filtered by drafts, awaiting payment, overdue and paid, above the totals
awaiting payment and overdue; it says when the billing identity is incomplete. **New invoice** writes a draft in a
dialog (client, mission among the client's, period, language, lines with live totals, notes). **From an activity
report**, with the number of months waiting, lists the months of activity reports not invoiced yet, latest first, with
their mission, whom they bill, their status, and the days worked times the daily rate with the amount before VAT
("2,5 j × 600 € = 1 500,00 € HT"); a month whose mission lacks a daily rate or a client says so and cannot be picked.
**Create the invoice** on a month drafts it and adds it to the list; deleting that draft frees the month again
(`GET /api/invoices/invoiceable-reports`). Each invoice's menu
edits, issues (asking the issue date, today or earlier), marks paid (asking the date) or unpaid, downloads the PDF or
deletes a draft. On a mission's page, a saved month's activity report with days worked, unless rejected, offers
**Create the invoice**, which drafts it and opens the invoices. The server's refusals (incomplete billing identity, client without address, a date before
the last invoice, a month invoiced already or rejected, a mission without a daily rate or a client) are told in the
interface language.

A draft changes freely, can be deleted, and its PDF says in capitals that it is not an invoice. **Issuing** needs a
complete billing identity and the client's address; it takes the issue date (today by default, never later, and never
before the last invoice issued, so numbers follow the dates), gives the next number of that year with no gap
(`<prefix><year>-0001`, the workspace's counter locked meanwhile), sets the due date from the payment term, and freezes
the seller and the buyer as they are: changing the billing identity or the client later changes no issued invoice. An
issued invoice can then only be marked paid (on a date) or unpaid; it reads as overdue past its due date, which the dashboard's reminders
(first, with how many days late) and the weekly digest point out until it is paid. It cannot be
changed or deleted, and it stays when its mission or activity report is deleted (French law keeps invoices ten years).

The PDF (A4, French or English) states the seller (legal name, followed by "EI" for an individual entrepreneur, address,
SIRET, legal form with share capital and registration for a company, VAT number when VAT applies), the number and the
issue, due and service dates, the client (name, address, SIREN and VAT number when known), the lines with their VAT
rate, the totals before VAT, VAT and with VAT, or "TVA non applicable, art. 293 B du CGI" and the amount due when
exempt, then the payment term and date, the IBAN and BIC, that there is no discount for early payment, and the late
payment penalties and the €40 recovery fee (art. L441-10 du Code de commerce).

An issued or paid invoice is corrected or cancelled with a **credit note** (avoir): **Create a credit note** in its menu
drafts one for the same client, mission, period, language and lines, and opens it to reduce for a partial credit (its
client cannot change). The draft is issued like an invoice, in the same numbering and under the same date rules; it
refers to the invoice's number and date, has no due date and is never paid. It cannot credit more than what the
invoice's issued credit notes leave of it, and once they cover it in full the invoice reads as **Credited**: it can no
longer be credited or marked paid, and it no longer counts as overdue. In the list a credit note shows its amounts
negative and is counted off what is awaiting payment; its PDF is titled "Avoir" or "Credit note", shows negative totals,
names the invoice it credits and leaves out the payment terms (`avoir-<number>.pdf`, `credit-note-<number>.pdf`).

The API: `GET /api/invoices/by-freelance/{freelanceId}`, `GET /api/invoices/{id}`, `POST /api/invoices` (a draft by
hand), `POST /api/invoices/from-report` (`{ projectId, year, month }`), `PUT` and `DELETE /api/invoices/{id}` (drafts),
`POST /api/invoices/{id}/issue` (`{ issueDate }`, optional), `POST /api/invoices/{id}/credit-note` (a draft credit note), `PUT /api/invoices/{id}/payment` (`{ paidOn }`, absent to
take it back) and `GET /api/invoices/{id}/pdf`; another workspace's invoice answers `404`. Invoices are part of the data
export.

### Factur-X

Every issued invoice and credit note downloads as a **Factur-X** file: a PDF/A-3B (fonts embedded, sRGB output intent,
XMP metadata) that prints as before and carries its structured data as an attached `factur-x.xml`, a Cross Industry
Invoice (UN/CEFACT D16B) of the EN 16931 profile, related to the PDF as its alternative. The XML states the number, the
type (380 for an invoice, 381 for a credit note, which also names the invoice it credits and its date), the issue date,
the notes, each line (description, days billed as `DAY`, unit price, VAT category and rate, amount), the seller (name,
SIRET, address split into postcode and city when its last line reads `75002 Paris`, VAT number, or else its SIREN as tax
registration under the franchise) and the buyer (name, SIREN, address, VAT number), the VAT per rate (`S` with its rate,
or `E` with the reason: "TVA non applicable, art. 293 B du CGI" and code `VATEX-FR-FRANCHISE` under the franchise), the
service period, the due date and terms, the IBAN and BIC (SEPA transfer, code 58; not on a credit note) and the totals,
the same as the PDF prints. A draft stays a plain PDF, since it is not an invoice. The four cases (with VAT, under the
franchise, invoice and credit note) were checked with the Mustang validator (PDF/A-3, Factur-X XSD and EN 16931
schematron). Sending them through an approved e-invoicing platform is the e-invoicing item of TODO.md.

### Accounting Export

**Export for the accountant** on the Invoices page (shown once an invoice is issued) offers each year holding an issued
invoice or credit note, in two forms; drafts are left out, since they are not accounting documents:

- **Journal entries (FEC)**: the file French accounting tools import (Pennylane, Tiime, Indy, Sage, Cegid, EBP, ACD,
  Quadra…), named `<SIREN>FEC<year>1231.txt` as the tax administration asks (without the SIREN until the billing
  identity holds a SIRET). Tab-separated UTF-8 with the 18 columns of art. A47 A-1 du LPF, dates as `YYYYMMDD` and
  amounts with a decimal comma. Each invoice is an entry of the sales journal (`VE`) on its issue date: the client
  (`411000`, with the client's own account `C<id>` and name) debited with the total, the services (`706000`) and the
  collected VAT (`445710`, left out when there is none) credited; a credit note is the same the other way round. Each
  payment of the year is an entry of the bank journal (`BQ`) on its date: the bank (`512000`) debited and the client
  credited with what was paid, the invoice less the credit notes issued by then. Entries are numbered in date order.
- **Invoice register (spreadsheet)**: one row per invoice and credit note of the year by date, with its number, type,
  date, client, SIREN, VAT number, mission, amounts before VAT, VAT and with VAT (negative for a credit note), due date,
  payment date, status and the invoice a credit note credits, then the totals. In French it is `factures-<year>.csv`
  with semicolons, decimal commas and `dd/mm/yyyy` dates, in English `invoices-<year>.csv` with commas and ISO dates,
  following the interface language; it starts with a byte order mark so spreadsheets read its accents, and text that a
  spreadsheet would run as a formula (starting with `=`, `+`, `-` or `@`) is prefixed with an apostrophe.

The API: `GET /api/invoices/export/{year}?format=FEC|REGISTER&language=FR|EN` (a year from 2000 to 2100; FEC and French
by default), for the signed-in workspace only. Pushing the entries straight into an accounting tool's API is not offered
yet (TODO.md).

### Notifications

**Profile → Notifications** holds the notifications the account can choose, each saved as soon as it is switched:

- **Weekly digest** (email, off by default): see below. Turned on, it lists its sections (the pipeline, overdue
  invoices, the week's interviews, follow-ups due, opportunities gone quiet, missions ending, new offers matching the
  profile), each kept or left out, at least one staying. When the server has no email key, the tab says the digest
  starts once it can send.
- **Booked calls** (email, on by default): an email as soon as a recruiter books a call through the
  [booking link](#booking-link), with when (Paris time), the recruiter's name, company, email and message, and a link to
  the calendar.
- **New offers** (in the app, on by default): the count of the saved searches' new offers next to **Offers** in the
  menu; turned off, the menu shows none and stops counting. Each saved search's own emails are set on the search.
- **Reminders**: interviews coming up, follow-ups due and opportunities gone quiet always show on the dashboard.

Push notifications wait on the choice of a mobile technology (TODO.md, Needs Input). The settings are read and changed
through `GET` and `PUT /api/users/notifications` (`weeklyReports`, `weeklyDigestSections`, `bookingEmails`,
`newOffersBadge`, and the read-only `emailsAvailable`); a `PUT` changes only the settings it names.

### Weekly Digest

Every Monday from 8:00, Paris time, an account with the digest on gets one email, in its interface language, about its
week:

- the pipeline: open opportunities by stage (first contact, interviewing, with an offer);
- the overdue invoices, the interviews of the next 7 days, the follow-ups due, the opportunities gone quiet and the missions ending, five of
  each at most, as on the dashboard;
- the offers Indezy found in the last 7 days that match the profile's skills, best match first, five with their facts
  and links, and how many more.

Sections with nothing to say, or left out in **Profile → Notifications**, are left out, and a week with nothing at all
sends nothing. Each digest is claimed before
it is sent (the account's `weeklyDigestSentOn` moves to the week in a transaction its version guards), so two replicas
never send it twice, given back when Resend refuses it, and sent with the idempotency key `weekly-digest/<account>/<week>`;
a replica started later on Monday, or during the week, sends what is still due (checked every 15 minutes). It carries a
one-click unsubscribe (`List-Unsubscribe`, RFC 8058, posting to `/api/public/weekly-digest/unsubscribe/<token>`) and a
link to the notification settings, and no tracking. Nothing is sent while no Resend key is set
([Deployment](./deployment.md#saved-search-email-credentials)).

### Sessions And Account Activity

**Profile → Security** also shows where the account is signed in and what happened to it.

- **Where you're signed in**: every open session (each sign-in by password, Keycloak or registration opens one), with
  its browser and system read from the User-Agent ("Firefox on Windows"; no address is kept), how it was opened, when,
  and when it was last used (noted every 5 minutes at most). This browser is marked; any other is signed out with
  **Sign out**, and **Sign out everywhere else** (after a confirmation) ends them all. A token stops working as soon as
  its session ends.
  In production the app exchanges the Keycloak sign-in on every load; that continues the browser's session rather than
  opening a new one.
- **Sign out** in the menu ends the browser's own session on the server too, so a copied token is useless afterwards.
- A new password signs out every other session; an administrator disabling the account ends all of them.
- **Account activity**: sign-ins, sign-ins refused for a wrong password or authenticator code, sign-outs, sessions
  ended, password and two-factor changes, data exports and imports, and an administrator disabling or enabling the
  account, newest first, 20 at a time, with the browser each came from. Events are kept a year and ended sessions 30
  days (removed nightly at 04:05 Paris time); both are in the data export (`sessions`, `accountActivity`).

The API is `GET /api/users/sessions`, `DELETE /api/users/sessions/{id}`, `DELETE /api/users/sessions/current` (sign
out), `POST /api/users/sessions/end-others` and `GET /api/users/activity?page=`; another account's session answers
`404`.

### API Tokens

**Profile → Security → API tokens** creates personal tokens that let another tool (a script, a spreadsheet, an
automation service) read the workspace through the API, at most 10 per account. A token is named after its tool and
lasts 30, 90 (the default) or 365 days, or does not expire; it is shown once, when created, with how to send it
(`Authorization: Bearer idz_…`) and a copy button, then listed only by its name and first characters with when it was
created, last used (noted every 5 minutes at most) and expires. **Revoke** (after a confirmation) cuts the tool off at
once. Creating and revoking one appear in the account activity.

A token only reads: `GET` (and `HEAD`) requests on the workspace's records (opportunities, clients, contacts, sources,
steps, seasons, activity reports, offers...), with the same workspace checks as the app. Any other method, and every
path under `/users` (the account, its sessions and tokens, the export), `/admin` and `/auth`, answer `403` "API tokens
only read workspace data". The API is described at `/api/swagger-ui.html`.

A token reads the whole workspace, or only the parts ticked when it is created (**It reads**): opportunities and
interviews (`/projects`, `/interview-steps`, `/seasons`, `/pipeline-templates`), activity reports and purchase orders
(`/projects/{id}/activity-reports`, `/projects/{id}/purchase-orders`, `/purchase-orders`), clients, contacts and
sources, invoices, compliance documents, or offers. A path outside its parts answers `403`, and so does a path of no
part (the global search, the profile) for a token limited to some. The list shows what each token reads; tokens made
before scopes read everything.

The tokens are managed through `GET /api/users/api-tokens`, `POST /api/users/api-tokens` (`{ "name", "expiresInDays" }`,
answered with the token this once) and `DELETE /api/users/api-tokens/{id}`, from a signed-in session only.


### Webhooks

**Profile → Security → Webhooks** tells the freelancer's own tools (Zapier, Make, n8n, a script) of the workspace's
events, at most 5 webhooks per workspace. A webhook is an HTTPS address on a public host (no user name or password in
it) and the events it listens to: an opportunity added or moved to another stage, an activity report sent, approved or
rejected, an invoice (or credit note) issued or paid, a call booked through the booking link. Its secret is shown once, when it is added, with a copy button.

Each event is written in the same transaction as the change it tells of, then posted within about twenty seconds:
`POST` with a JSON body `{ id, event, createdAt, data }` and the headers `X-Indezy-Event`, `X-Indezy-Delivery` (the
delivery's id), `X-Indezy-Timestamp` (seconds) and `X-Indezy-Signature: sha256=<hex HMAC-SHA256 of "<timestamp>.<body>"
with the secret>`. `data` holds ids and the business fields: an opportunity's id, role, status and previous status,
client and daily rate; a report's id, mission, month, days worked and status; an invoice's id, kind, number, status,
client, dates, total and the invoice a credit note credits; a booked call's id, start, end and company (not the
recruiter's name or email). A 2xx answer within ten seconds counts as received;
otherwise it is tried again after 1, 5 and 30 minutes, 2 and 12 hours, then given up. Redirects are not followed. A
webhook failing 20 times in a row turns itself off; **Turn on** brings it back with its failures forgotten. **Send a
test** posts a `PING`; **Latest deliveries** lists the last 20 with their state and answer. Deliveries are forgotten 30
days after they settle, and with their webhook.

API: `GET` and `POST /api/users/webhooks` (`{ url, events }`, answering the secret once), `PUT` and `DELETE
/api/users/webhooks/{id}` (`active` turns it off or on), `POST /api/users/webhooks/{id}/ping` and `GET
/api/users/webhooks/{id}/deliveries`. Being under `/users`, they refuse API tokens.
### Public Profile

**Public profile** (a tab of the profile page) shows a page anyone with its link can read, at `/p/<address>`, without
an account: the name, title, city, presentation, skills with their level and years, languages, website, LinkedIn and
GitHub links (web addresses only), the photo, and whether the freelancer is available now or from which date
(**Available from** of the profile). The email, phone, address and every workspace record stay private.

- It is off until turned on, and needs an address: 3 to 40 lower-case letters, digits and single dashes, offered from
  the name the first time; one taken by another account is refused. The link is copied or opened from the tab.
- A hidden profile and an unknown address both answer "No profile here", so neither can be told from the other.
- API: `GET|PUT /api/users/public-profile` (`enabled`, `slug`) and, without a token, `GET /api/public/profiles/{slug}`.
  The account export includes the settings (`publicProfile`).

### Data Import

Choosing an export file (up to 10 MB) lists, section by section, what it would add: sources, clients and ESNs,
contacts, seasons, opportunities with their interview steps and preparation checklists, and journal notes, each record
**new**, **already here** or **left out** with why; **Import** then saves the new ones and reports what was added.
Nothing is written until then, and an import that fails saves nothing.

- A record already in the workspace is left as it is: a source, client or season with the same name (case, accents and
  punctuation aside), a contact with the same email (or, without one, the same name at the same company), an
  opportunity with the same role, client and start date. Imported records that point at it point at the existing one,
  so importing the same file twice adds nothing the second time.
- A record that breaks the API's rules (a client without a city, an opportunity without a daily rate, a step without a
  title) or points at a company or opportunity the file does not hold is left out; journal notes come with the
  opportunities the import adds, and are dated the day of the import.
- Contacts come without their manager, and the export's other sections (profile, activity reports, offers, templates,
  attachments, exchanges and relations) are not imported.

API: `POST /api/users/import/preview` and `POST /api/users/import`, each with the file as the multipart `file` part,
answer the report (`applied`, then per section `kind`, `total`, `added`, `duplicates`, `invalid` and the first 200
`items` with their `label`, `status` and `reason`); a file that is not an export answers `400`.

### Welcome Steps

A new account, registered or signed in through Keycloak for the first time, opens on **Welcome to Indezy** (`/welcome`)
instead of the dashboard, which leads back to it until it is finished or skipped:

1. **About you**: first and last name, title, skills (added with Enter or a comma, once whatever their spelling, up to
   30 here), address and city, filled in from the profile.
2. **Rate and availability**: the target daily rate (a whole number of euros above zero) and the **Available from**
   date; leaving this step saves both steps to the profile, keeping everything else it holds.
3. **Recruitment process**: a starter template to apply to opportunities, **Through an ESN** (call with the ESN,
   technical test, client interview, offer and contract) or **Direct client** (discovery call, client interview,
   proposal), each step with its preparation checklist, saved in the interface language
   (`POST /api/pipeline-templates/steps`), or **Not now**. Coming back to the step does not save it twice.
4. **Ready**: **Add an opportunity**, **Browse freelance missions** or **Open the dashboard**.

**Skip the setup**, at any step, and the last step's buttons end the welcome steps for good
(`POST /api/users/onboarding/complete`); signing in reports whether they are pending (`onboardingPending` in the login
response). Accounts created before the welcome steps never see them.

The backend stores a few account fields the UI does not use yet (timezone, currency, default view, items per page, notification flags without delivery). Verify the full user journey before presenting such a field as product-complete.

## Freelancer Profile

The freelancer model captures the working identity used by opportunities:

- first name, last name, email, phone, birth date, address, and city
- employment status: `FREELANCE`, `PORTAGE`, or `CDI`
- notice period and availability date (the availability date is edited as **Available from** on the profile page, and the offer feed's profile match compares offers' start dates with it)
- reversion rate and income tax rate
- skills written freely (suggestions while typing, up to 50 of 60 characters each, one skill once whatever its spelling, see [Skill Names](#skill-names)), each with an optional level (beginner, intermediate, advanced, expert) and years of experience (0 to 60), edited in the profile's **Personal information** tab
- optional CV file path

The freelancer owns the main opportunity workspace: projects, clients, contacts, and sources are all attached back to a freelance profile.

### Skill Names

Spellings of one skill are one skill: case, accents, spaces, dots and dashes aside ("Spring Boot", "SpringBoot",
"spring-boot"), and a list of known aliases names the skill they stand for ("JS" and "ECMAScript" for JavaScript, "K8s"
for Kubernetes, "Postgres" for PostgreSQL, "Golang" for Go, about thirty skills; `GET /skills/aliases`). It applies to:

- the profile: adding another spelling of a listed skill says under which name it is listed, typing an alias suggests
  the skill ("k8" offers Kubernetes), and skills listed twice or under an alias are offered to merge under the usual
  name (**Use JavaScript**), keeping the higher level and the most years of experience;
- the profile match: a skill counts once, however many spellings the profile lists, and an offer naming any of its
  spellings mentions it;
- project tags: the dashboard's skill trends count the spellings of a skill together under its usual name, the bulk
  **Add a tag** adds nothing to a stack already naming the skill, and renaming a tag folds a spelling already there.

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
- files: PDF, images, Word, OpenDocument and text files of up to 10 MB, 20 per opportunity, attached from the
  **Files** part of its **Documents and links** card; PDF and images open in the page, the others download (see
  [Security](./security.md#file-uploads-and-attachments))
- original link
- personal rating
- notes
- lost/rejection reason (captured when a card is moved to LOST, surfaced as a dashboard breakdown)
- favorite flag (pins hot leads to the top of their Kanban column)
- client, optional middleman, source, optional job-hunting season, and freelance owner

The project form edits the pipeline stage (with the loss reason for lost opportunities), the client (final clients and ESNs are listed in two groups, an ESN being the client when the final client is unknown, as for missions added from the offer feed), the optional ESN/intermediary, the source and the season. The project page shows the stage as a pill that can be changed in place and names the opportunity's season, and the projects list filters by stage and season and paginates results.

The projects list's filters combine: words searched, daily rate range, work mode, technologies (typed or picked as
chips), stage, season, start and end dates, duration, client and **Archive** (all, those in the pipeline or the
archived ones), with the sort. They live in the page's address (`/projects?q=java&min=500&archived=active&tags=Java,Kafka`,
nothing for a filter left at its default), which is kept up to date as they change without adding to the browser's
history, so a filtered view can be bookmarked, shared or reloaded; parameters that mean nothing are left out.

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
- why their details are kept (legal basis): legitimate interest (the default), consent or contract

These relationships make it possible to track who is attached to a client or ESN and which opportunities depend on that relationship.
Emptying a field in the form clears it.

### Contact Privacy

The people a freelancer works with are personal data, and the freelancer is their controller (Indezy keeps them on the
freelancer's behalf). A contact's page has a **Their data** card:

- **Why you keep their details**: the legal basis chosen in the form, legitimate interest unless set otherwise;
- **Download their data**: a JSON file of what the workspace holds about them (details, legal basis, since when and last
  changed, exchanges, and the companies and notes of whom they work with, without those people's names), to hand them
  on request (GDPR article 15);
- **Erase on their request**: once confirmed, the contact goes with their exchanges and links (GDPR article 17).

Both requests are kept in the account's activity (Profile → Security) as `CONTACT_DATA_EXPORTED` and `CONTACT_ERASED`
with the contact's number only, so the freelancer can show a request was handled without keeping who made it. The email
templates' `{{privacyNotice}}` variable writes, in the interface language, who keeps the recipient's details, why, and
how to see, correct or erase them (GDPR article 14), for a first message.

**Profile → Preferences → Anonymize contacts without exchange for** sets a retention period: never (the default), 1, 2,
3 or 5 years (the CNIL advises 3 for prospects). Every night at 03:45 Paris time, a contact with no exchange, no change
and no opportunity with their company changed for that long loses their name (shown as "Anonymized contact"), email,
phone and notes, their exchanges' summaries and their links' notes; the contact stays, with the dates and channels of
its exchanges, so the counts and org charts keep their shape, and its page says when it was anonymized. The account's
activity records how many were (`CONTACTS_ANONYMIZED`). `INDEZY_CONTACTS_RETENTION_ENABLED=false` stops the nightly run.

API: `GET /api/contacts/{id}/personal-data` (an attachment, `Cache-Control: no-store`) and `POST
/api/contacts/{id}/erase`; contacts carry `legalBasis` and `anonymizedAt`; the preferences carry `contactRetentionYears`
(0 for never).

### Contact Network And Org Charts

A contact's form has **Reports to**: whom the contact reports to, at the same company or another (nobody by
default). It cannot be the contact itself nor anyone it manages, directly or not (the API answers `400` on
`managerId`). The contact's page shows a **Network** card: whom it reports to, its team (who reports to it) and whom it
**works with**: links between two contacts, often across a client and its ESN, each with an optional note of up to 100
characters on how they work together; a contact has up to 100 links. A link goes both ways, is listed on both
contacts' pages, and goes with either contact; a manager's team reports to nobody once the manager is deleted.

The client's **Contacts** tab draws them as an **Org chart**: those reporting to nobody at the client at the top (with
whom they report to elsewhere, if anyone), their teams nested below, by name, and beside each person the people they
work with at other companies, linked to their pages. API: contacts carry `managerId` and `managerName`;
`GET /contacts/{id}/relations`, `PUT /contacts/{id}/relations` (`otherId`, `note`: links, or changes the note),
`DELETE /contacts/{id}/relations/{otherId}` and `GET /clients/{id}/contact-relations`. Links are part of the account
data export.

### Contact Interaction History

A contact's page keeps its **Exchanges**: each one has a day (today at the latest), a channel (email, phone, LinkedIn,
meeting, other), who started it (**From me** / **From them**), for one the freelancer started whether it was answered
(waiting, answered, no answer), and an optional summary of up to 500 characters. They are listed newest first, can be
changed or deleted, and a contact keeps up to 500. Above them the page reads:

- **Last contact**: the day of the latest exchange, either way; the contacts list shows it too ("3 weeks ago");
- **Response rate**: the exchanges the freelancer started that were answered, out of those whose answer is known
  (waiting ones are left out), as "50% (1 of 2)";
- **Preferred channel**: the channel the contact answers or writes first on most, the latest used on a tie.

An exchange counts as activity for the dashboard's dormant contacts, so a contact written to recently is not listed
there. When a contact's latest exchange is one the freelancer started that still waits for an answer, it shows in the
dashboard's **Follow-ups due** once it has waited the follow-up delay (5 days unless set otherwise in Profile →
Preferences, from 1 to 60), longest waiting first, with the channel used; recording the answer, or any newer exchange,
takes it off (`followUpsDue` in the dashboard statistics, `followUpDays` in the preferences). Exchanges are deleted with their contact (also when its client or account goes) and are part of the account
data export. API: `GET /contacts/{id}/interactions` (the exchanges with `lastContactedOn`, `asked`, `answered`,
`responseRate` and `preferredChannel`), `POST /contacts/{id}/interactions`, `PUT` and `DELETE
/contacts/{id}/interactions/{interactionId}`; contacts carry `lastContactedOn`.

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

Every workspace starts with the job boards Indezy knows as sources: the boards of the offer feed and the sites it does
not read (to visit yourself, matching services, and the ones left out), each with its home link, as job boards with
the listing flag (matching services without it) and LinkedIn as social media. A workspace gets them once, when it is
created, and the workspaces created before got them on the first start of the version that knows them: a board
deleted since stays deleted, and a source already named like a board (case aside) is kept as it is. Adding an offer
of the feed to the pipeline uses the board's source by its name.

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
| Silkhom | the freelance cards of its single offers page (CDI cards are left out), with the city, day and description | every 6 hours |
| LeGratin | the missions page with the daily rate, context, duration, city and remote days; each mission is dated by its id | every 3 hours |
| Freelance-Informatique | its freelance offers page after page, with skills, department, start and duration, dated by their reference | every 2 hours |
| Espace Freelance | its missions page after page, with the trade, agency, reference, start and daily rate | every 12 hours |
| Beyond Skills IT | the offers labelled Freelance on its offers page, each read from its own page with place, work mode, daily rate, skills and description, dated by the sitemap | every 12 hours |
| Koïno | the missions of its sitemap whose applications are open, with the daily rate, place and description | every 12 hours |
| Sixième Homme | the missions its home page showcases (the only public ones), with start, duration, rate, place and remote work | every 12 hours |
| FreelanceRepublik | the missions of its sitemap, from their schema.org contractor posting, with place and full description | every 12 hours |
| EasyPartner | the jobs of its job sitemap whose posting is a contractor one ("Prestation"; its CDI jobs are left out), with the daily rate and the page's description | every 12 hours |
| Castor Network | Alten's freelance missions page after page (engineering as well as IT), with the client, duration, start, place and experience | every 12 hours |
| KatchMe | the open freelance offers of its WordPress API (its CDI jobs and filled missions are left out), with the place, daily rate, remote policy and description of their page | every 6 hours |
| OuiDesk | the missions of its sitemap, each page calling itself a freelance mission, with place, start, duration and description; the site gives no date, so they are dated when first seen (applying takes an account, joined by referral) | every 12 hours |
| Kicklox | the missions of its sitemap whose contract is "Prestation", read from the API its pages load (on kicklox.io), with the daily rate, remote policy, place, start, duration, skills and texts; its partners' permanent and temporary jobs and its own generic offers (standing ads for no mission in particular) are left out | every 3 hours |

Sites that describe each offer on its own page (WorkDispo, Next Mondays, Mindquest, Freelance Factory, Koïno,
Sixième Homme, FreelanceRepublik, EasyPartner, KatchMe, Beyond Skills IT, OuiDesk, Kicklox) are read a few pages at a time: each run reads up to 100 offers Indezy does not hold yet, most
recently changed first, so their backlog fills in over the first runs. An offer set aside (a CDI or CDD job, an offer older than 90 days, a page
gone) is remembered for 30 days and not read again.

The other boards are planned in [TODO.md](../TODO.md#freelance-offer-feed-added-2026-10-19), with the ones left out
and why (LinkedIn forbids crawling; HiTechPros, Opteamis, Comet and others keep missions behind a sign-in); the
**Job boards** page lists them too (see [Site Health](#site-health)).

Each offer shows its site, title, publisher, daily rate ("550 € – 650 €", "≤ 600 €" or "TJM non précisé"), place with
its department and the distance from the profile's city, work mode (on site, hybrid with remote days a week, full
remote; read from the title and description when the site gives none, from phrases about how the mission is worked
only), duration, start date or "as soon as possible", seniority, skills, an excerpt and how long ago it was
published (the hint says when a site gives no date and the offer is dated when Indezy first saw it). Unseen offers are
marked **New**.

- **Filters**, kept in the address so a search can be bookmarked or shared: keywords (every word must appear in the
  title, publisher, skills, place or description, or in the workspace's note on the offer, accents ignored), daily rate minimum and maximum with "hide offers
  without a daily rate", regions, departments, distance from the profile's city (10 to 200 km), work modes, minimum
  remote days a week, duration, start by a date (missions starting as soon as possible stay listed), seniority,
  skills (suggested from the open offers), published within 24 hours, 3 days, a week or a month, sites, unseen
  offers only, and offers matching the profile (see [Profile Match](#profile-match)). An offer that does not state its duration, start or seniority stays listed when filtering on them;
  one without a place, work mode or remote days is left out of filters on those.
- **Sort** by newest, highest daily rate (offers without a rate last), closest (when the profile has a city) or best
  match (when the profile names skills).
- **Views**: all offers (the hidden ones and expired ones are left out), saved offers (expired ones stay, marked
  **Expired**), offers applied to (expired ones stay) and hidden offers. On a phone the four views sit two by two.
- **Actions** on each offer: open it on its site (it is marked seen), show its full description, save it, hide it
  (or show it again), and **Add to pipeline**: a new opportunity at the Contact stage with the offer's title, rate
  (the highest rate stated, else the profile's target daily rate), work mode and remote days a month, start date,
  duration, description, skills as tech stack and link. Its client is the offer's publisher (created as an ESN or
  intermediary when the workspace has no client of that name; the site itself when the offer names none), and its
  source is the job board: the workspace's source named after the site, created as a job board on first import. An
  offer is added once; the card then links to its opportunity, until that opportunity is deleted. The card's
  **More actions** menu also records an application (see below), hides every offer from its publisher (see the
  blocklist below) and keeps a note on it.
- **Applications**: **I applied** in an offer's menu asks how (the site's form, email or phone) and on which day (today
  by default, never later) the workspace applied, and whether to plan a follow-up (after 7 days by default, 1 to 60;
  one due already is planned for the next morning). The mission joins the pipeline at the Contact stage, as **Add to
  pipeline** does, unless it already is there, and its opportunity gets an "Application sent" step waiting for
  feedback, dated that day, plus a planned "Follow up on the application" step at 9:00 that the calendar's **Upcoming** list shows;
  steps are titled in the interface's language. The card then reads **Applied 3 days ago** (its tooltip says how and
  on which day) and links to the opportunity, and the **Applied** view lists those offers. A mission is applied to
  once, whichever copy; **Undo the application** forgets it and leaves the opportunity and its steps in the
  pipeline. Offers a workspace applied to are kept past the 90 days.
- **Private notes**: **Add a note** (or **Edit the note**) in an offer's menu, or `N` on the focused offer, opens a
  text field under the excerpt for whatever the workspace wants to remember (who called, the rate to ask, why the
  mission is not for it), up to 2,000 characters. `Ctrl+Enter` or **Save the note** keeps it, `Esc` or **Cancel**
  closes the field unchanged, and **Delete the note** (or saving it blank) removes it. The card shows the note under
  its excerpt, the keyword filter finds offers by the words of their note, the note is shared by every copy of the
  mission, and **Add to pipeline** adds it to the new opportunity's notes.
- **Several offers at once**: each card has a checkbox (or `X` on the focused offer). Once one is ticked, a bar held at
  the bottom of the screen counts them, selects the whole page or none, and saves (unsaves in the saved view), hides
  (shows again in the hidden view), marks seen or adds to the pipeline every selected offer at once. Changes show at
  once and are put back if the server refuses them; hidden offers leave the feed with an **Undo** in the notification
  for 8 seconds. Adding skips the offers already in the pipeline, adds each mission once (two copies of one mission make
  one opportunity) and says how many were added. A new page or new filters clear the selection.
- **Offer page** (`/offers/{id}`): **Open its page** in an offer's menu, or an offer of the dashboard's widget, opens
  the offer on its own page, which marks it seen: its full description, facts, copies on other sites and match, the
  same actions as its card in the feed, **Open on Free-Work** and **Copy the link** (also in the card's menu), which
  copies the page's address to share it with whoever has an Indezy account (each one sees the offer with their own
  workspace's state). An expired offer says when it expired; an address of an offer no longer collected explains that
  offers leave the feed 90 days after their publication or once they expire or leave their site, unless saved, applied
  to or added to the pipeline, and links back to the feed. Hiding an offer from its page keeps it on the page, which
  says it is out of the feed.
- **One card per mission**: boards republish each other's missions (WorkDispo republishes Cherry Pick, Collective.work
  and Free-Work republish Mon Consultant Indépendant, ESNs post on several boards), so offers of different sites whose
  titles read the same and whose publisher or opening text is the same are one mission. The feed shows its earliest
  copy that matches the filters, with **Also on** links to the others (one per site, still open). Saving, hiding,
  marking seen or adding one copy to the pipeline does it for the mission: the other copies never come back as new.
- What a workspace does with offers (saved, hidden, seen, imported, its notes and applications) is private to it and
  part of the account data export. Offers older than 90 days or past their expiry are removed every night, unless a
  workspace saved, applied to or imported them.

- **Saved searches**: **Save this search** keeps the current filters and sort under a name (up to 20 per workspace,
  names unique). They sit above the feed, each with the offers Indezy found for it since it was last opened ("3 new":
  collected after that moment, one per mission); opening one shows its offers and starts counting afresh. The
  **Offers** item of the menu carries a badge with the offers new for any saved search, counted again every five
  minutes and after each change; a failed count is not reported.
- **Saved search emails**: the bell beside a saved search sets how often it emails the offers Indezy finds for it:
  never (the default), as they come (grouped every 30 minutes) or once a day at a chosen hour, Paris time. The emails
  go to the workspace's address, in the language the app was in when they were turned on, and list up to 20 offers
  (the search's order) with their publisher, place, remote policy, daily rate, duration and match score, each linking
  to its page in Indezy, then how many more there are. They carry no tracking pixel: an offer opened from an email
  counts once its page is open in the app, signed in. After 60 days of emails without an offer opened from them,
  sending stops and a last email says so, as does the bell (turning them on again gives 60 more days). Each email has
  a **Stop these emails** link, whose page names the search and stops it with one button, without an account, and the
  one-click unsubscribe of mailboxes (RFC 8058). Nothing is sent while the server has no Resend key, and the dialog says
  so (see [Deployment](./deployment.md#saved-search-email-credentials)).
- **Suggested searches** (see [Suggested Searches](#suggested-searches)): searches drawn from the profile and the
  pipeline, to preview, save in one click or dismiss.

- **Blocklist**: **Hide every offer from …** in an offer's menu, or a name typed in the profile's **Offer feed** tab,
  keeps a publisher's offers (an intermediary's or a client's) out of the workspace's feed, saved searches' new offers
  and market snapshot, whatever the spelling (case, accents and punctuation aside: "EXEMPLE-conseil" is "Exemple
  Conseil"); a copy of the mission from another publisher then stands for it, and saved or hidden offers stay in
  their views. The tab lists the hidden publishers, each with a button to show its offers again, up to 200 per
  workspace, and links to **Job boards**. The list is private to the workspace and part of the account data export.
- **Market snapshot**: **Market** opens, above the offers, a picture of the offers the current filters and view find
  (each mission once), drawn again when they change but not for another page or sort, and left open or closed in that
  browser. It gives the offer count, the median daily rate, the middle half of the rates (first to third quartile)
  and the share of offers stating a rate, with an offer's rate taken as the middle of the range it states. With a
  target daily rate in the profile it says what share of the offers stating a rate reach it with their highest rate,
  as the profile match reads them, and highlights its band on a histogram of the rates (bands of 50 €, twice as wide
  as often as needed to keep 16 at most, over the rates between the 5th and 95th percentiles, rarer ones gathered in
  an open band below and one above). It also shows the split between full remote, hybrid (with their remote days a
  week on average), on site and not stated, the ten skills the offers name most (case aside; a click adds one to the
  filters), the ten publishers naming the most offers (intermediaries or clients, spellings that differ only by case,
  accents or punctuation counting as one) and the offers published each of the last 30 days, Paris time. A workspace's
  snapshot of given filters is computed once a minute at most (about a second with 40,000 open offers), so an offer
  saved or hidden, or a target rate changed, shows in it up to a minute later.

API: `GET /offers` (filters, `matchesProfile`, `view` = `FEED`, `SAVED`, `APPLIED` or `HIDDEN`, `sort` = `NEWEST`, `RATE`, `DISTANCE` or `MATCH`, `page`,
`size`), `GET /offers/market` (the same filters and view), `GET /offers/{id}`, `PUT /offers/{id}/state`, `POST /offers/{id}/import`
(as the offer says, or as its body, an opportunity corrected in the project form, says; see [From An Offer Link](#from-an-offer-link)),
`POST /offers/link` (`url`),
`PUT /offers/states` (`offerIds`, 100 at most, with `saved`, `hidden` or `seen`), `POST /offers/import` (`offerIds`, 50
at most; answers the offers `imported`, `alreadyInPipeline` with their opportunity, and `missing`),
`POST /offers/{id}/application` (`appliedOn`, `via` = `SITE_FORM`, `EMAIL` or `PHONE`, `followUpInDays`, `language` =
`FR` or `EN`; `409` once applied, `400` for a later day) and `DELETE /offers/{id}/application`, `GET /offers/sites`,
`GET /offers/skills?q=`, `GET /offers/regions`, the site health (`GET /offers/sites/health`,
`PUT /offers/sites/{site}/exclusion`, `POST /offers/sites/{site}/refresh`, and `PUT` or `DELETE
/offers/sites/left-out/{site}/visit`), the blocklist (`GET /offers/blocked-publishers`,
`POST /offers/blocked-publishers` with a `name`, which answers the existing block for a name read the same, and
`DELETE /offers/blocked-publishers/{id}`) and the saved searches: `GET /offers/searches`, `POST /offers/searches`,
`POST /offers/searches/{id}/open`, `DELETE /offers/searches/{id}`, `PUT /offers/searches/{id}/emails` (`frequency`
`OFF`, `INSTANT` or `DAILY`, `hour` 0 to 23 and `language` `FR` or `EN`), `POST /offers/searches/{id}/emails/opened`,
`GET /offers/searches/alerts`,
`GET /offers/searches/suggestions` (`suggestions`, each with its `key`, `filters`, `place`, `offers`, `goodMatches` and
`newThisWeek`, and the profile fields `missing`) and `POST /offers/searches/suggestions/dismissals` (the filters). Each offer carries
its `match` (score, skills, rate and place fit) and each page says whether distances (`distanceAvailable`) and matches
(`matchAvailable`) can be computed.

### Suggested Searches

**Suggested searches**, beside **Save this search** above the feed, opens a list of searches drawn from the profile
and the pipeline; it opens by itself while the workspace has no saved search (until it is closed once in that browser),
and when the profile's job title, skills, target daily rate or city change, the profile's confirmation offers **See
them**. The searches tried, all at the target daily rate or more (the median daily rate of the pipeline when the profile
sets none):

- the job title as keywords, within 30 km of the profile's city;
- each of the profile's first two skills, within 30 km of its city, and the pair among its first four skills that
  most open offers ask for together;
- the first skill full remote, in the city's region, hybrid with two remote days a week or more (within 80 km), and
  starting within 30 days after the profile's availability date;
- the two skills the opportunities added in the last 90 days or won name most and the profile does not, within 30 km.

Each is counted in the workspace's feed (its hidden offers, excluded sites and blocked publishers left out): searches
finding no offer, with the filters of a saved search, or dismissed are left out, and at most five are shown, those
finding the most offers that match the profile (60 or more) first, then the most offers. Each one reads as its filters
("Java · 600 €/day or more · Lyon + 30 km"), with its open offers, those matching the profile and those published in
the last seven days. **Preview** shows its offers in the feed without saving anything; **Save** offers its words as the
name of a saved search; dismissing it means the same filters (whatever their sort or order) are never suggested again,
up to the 200 most recent dismissals, which are part of the account data export. The list says which empty profile
fields (skills, job title, city, target daily rate) would bring more, with a link to the profile. A workspace's
suggestions are counted again after 10 minutes, or at once when the profile or the pipeline they come from change.

### From An Offer Link

**From an offer link**, next to the Kanban board's title and the projects list's **New project**, opens the new
opportunity form with the cursor in its **Offer link** field, which the form always offers on a new opportunity.
Pasting the link of an offer (any copy of the mission, with or without `https://`, tracking parameters, fragments and
trailing slashes aside) and **Fill in** (or `Enter`) looks the offer up, on one of the sites the feed reads or on any
other job site:

- **Found in the feed**: the form is filled in with what **Add to pipeline** would make of it (role, description, tech
  stack, daily rate, work mode and remote days a month, start date, duration and link), its client is the offer's
  publisher and its source the site. A publisher or a site the workspace does not have yet is offered as
  "Exemple Conseil (new ESN, created on save)" and created only when the form is saved. Every field can be corrected,
  the stage picked in the form (Contact by default) stays, and nothing is created before
  **Create**, which adds the offer to the pipeline: the feed's card then shows **In the pipeline**, and the workspace's
  note on the mission joins the opportunity's notes. A banner names the offer, with **Unlink** to keep what was filled
  in as an opportunity of its own; an expired offer is pointed out.
- **Already in the pipeline** (by this offer or one of its copies): the field says so and links to its opportunity.
- **Not collected yet**: Mindquest, WorkDispo, Koïno, Next Mondays, Sixième Homme, FreelanceRepublik, EasyPartner, KatchMe, Beyond Skills IT, OuiDesk and Kicklox describe each offer on its own
  page (Kicklox through its API), so that offer is read at once, through the collection's polite client, and the offer joins the feed. Each
  workspace reads at most 20 offers an hour this way (`429` with `Retry-After` beyond). The other sites are read on
  request, as the **Job boards** page does (at most once every 15 minutes per site), which finds the offers published
  since their last run; the field says it is reading the site and looks again every 5 seconds for 2 minutes. When the
  site was read moments ago, the field says when it can be read again.
- **On another site** (a company's careers page, an applicant tracking system, a board the feed does not read): the
  page is read once, for its schema.org `JobPosting` (which most job sites publish: title, description, publisher,
  place, daily rate, dates) or else its title and summary, and the form is filled in the same way, its client named
  after the posting's publisher (else the site) and its source after the site (a board Indezy knows by its name, such
  as Welcome to the Jungle, any other by its host), both created on save when missing. Nothing joins the feed. The read
  counts against the same 20 an hour; the same link again opens the opportunity it made instead of reading the page.
- **Refused**, with the reason: a page without an offer or gone, a job that is not freelance (CDI or CDD, or a posting
  that states a salaried contract and no `CONTRACTOR`), an offer expired or older than 90 days, a site that cannot be
  reached (its `robots.txt` included, or an address off the public internet), or an address that is not a web page.
  Collection off on the server finds collected offers only.

### Browser Extension

The **Indezy: add this offer** extension for Chrome and the browsers built on Chromium (`indezy-web/extension`) adds the
offer being read, on any site, to the pipeline: its toolbar button opens Indezy's new opportunity form with the page's
address in the **Offer link** field, looked up at once as above (`/projects/new?from=link&url=<address>`), and a
right-click on a link offers **Add this link to Indezy** for the linked page. The form opens in the browser's own
Indezy session, so nothing is created before **Create** and the extension holds no password or token. Its options set
the Indezy address (`https://indezy.swirlit.dev` by default; another HTTPS address, or `http://localhost` in
development). It reads nothing on the pages visited: its permissions are the active tab (the address of the page whose
button was clicked), the context menu and its own storage. The browser's own pages and local files are ignored. In
English and French, following the browser.

### Profile Match

Once the profile names skills, every offer is scored out of 100 against the profile's skills, target daily rate, city
(the workspace's city when the profile has none) and availability date (**Available from** on the profile, the
workspace's next availability date):

| Part | Points | How |
| --- | --- | --- |
| Skills | up to 60 | 20 per profile skill the offer mentions, full marks from three (or from every skill of a profile with fewer). A skill counts when its words appear whole in the title, publisher, place, skills or description, case and accents aside ("Java" is not found in "JavaScript"); skills shorter than three characters ("Go", "R", "C#") count only when the offer lists them, since in a text they are ordinary words. A skill is found under any of its spellings, and spellings of one profile skill count once ([Skill Names](#skill-names)). Up to 20 profile skills are compared. |
| Daily rate | 25 | 25 when the highest rate the offer states meets the target, 15 when it is less than 10 percent below, 10 when the offer states no rate, 0 below that. |
| Place | 15 | 15 when the mission is full remote or within 30 km, 10 within 80 km, 5 farther with three remote days a week or more, 5 when the offer states no place, 0 otherwise. |
| Availability | 10 | 10 when the mission starts once the freelance is available, 5 when it starts up to two weeks before (missions can often wait that long), 5 when the offer states no start, 0 earlier. A mission starting as soon as possible, or on a day already past, starts today (Paris time); an availability date already past means available today. |

The score is the share of the points the profile can earn: a profile without a target rate, a city or an availability
date is scored on the other parts (out of 60 to 110 points). The card shows the score as a badge (green from 60, amber
from 35), whose tooltip lists the skills found and how the rate, place and start compare, and puts the profile's skills first among the offer's,
highlighted. **Matches my profile** keeps the offers scoring 60 or more, and **Best match** sorts by score, newest first
among equal scores; both are computed by the database with the same rules as the cards and also apply to saved
searches. Until the profile names skills, the feed invites to complete it and the filter and sort are unavailable.

### Rate Versus Market

Each card stating a daily rate whose first skill is common enough shows, next to the rate, how it compares with the
market: "+12% vs. Java in Île-de-France", "−8% vs. Java in France" or "At the median for Java in Île-de-France", green
from 5% above, amber from 5% below. The offer's rate is the middle of the range it states; the market is the median
rate of the open offers stating a rate and naming the same skill first (one per mission, copies on other sites
aside), in the offer's region when ten offers or more make it, else in all of France; with fewer than ten, nothing is
shown. Its tooltip names the median and how many offers it is taken over. The medians are computed every night at
04:45 (Paris time), after old offers are removed, and once when a server starts with none.

### Mission History

When a site publishes an offer Indezy holds with other details, Indezy keeps what changed: a daily rate stated or
changed, a start stated or moved, a description rewritten (a detail the site stops stating is not a change). The card
shows the latest change and, when the site published the mission (its title and publisher) under more than one
reference in the last 60 days, how many times: "Rate raised from €500 to €550 · Published 3 times in 60 days", a sign
of a mission hard to fill and room to negotiate. The offer's page lists every change, the latest first, up to ten.
Changes are removed with their offer; the references a mission was published under are kept 90 days even once its
offers are removed. Sites read through their sitemap read each offer once, so only the sites read through a list or an
API (Free-Work, Collective.work, Cherry Pick, LeHibou, Yeets and the HTML lists) report changes.

### Publisher Page

A card's publisher name opens what that publisher (an intermediary or a client, however it is written) puts in the feed
(`/offers/publishers/{name}`): its open missions (each once, copies on other sites aside), their median daily rate and
age, how many of its missions of the last 90 days it published again under a new reference (a sign of a mission hard to
fill), the sites it posts on, its newest open offers (up to 20, each opening its page), the workspace's client record of
that name (linked, and marked ESN when it is one) and **Hide its offers** or **Show its offers again**, the same block
as the card's menu. A mission several intermediaries publish says so after the publisher's name, with the spread of
the best rates they state: "3 intermediaries, €520 – €600".

API: `GET /offers/publishers/{name}` (`404` when Indezy holds none of its offers).

### Market Trends

The **Market** panel ends with **Trends**: for one of the profile's skills (the first one preselected, any other
chosen from a list) it shows the median daily rate and the open offers naming that skill first on the latest night,
and how each moved over 30 days, 90 days and a year, in the region the feed is narrowed to (when exactly one is
chosen) or in all of France. The figures are those of [Rate Versus Market](#rate-versus-market), kept every night for
400 days; a span shows "—" until a night stands for it (one at most a week earlier than the span), and a skill with
fewer than ten offers, or none yet, says it has no history. A profile without skills is invited to add some.

API: `GET /offers/market/trends` (`skill`, the profile's first one by default; `region`, all of France by default).

### Suspicious Offers

A card whose offer looks like a fake or spam listing carries a **Looks suspicious** badge, whose tooltip says why, and
**Hide suspicious offers** in the filters leaves such offers out of the feed, its market snapshot and saved searches
saved with it. An offer looks suspicious when:

| Rule | When |
| --- | --- |
| No publisher, no description | the offer names no publisher and has no description |
| Rate far from the market | its daily rate (the middle of its range) is below 40% or above 250% of the median of its first skill (see [Rate Versus Market](#rate-versus-market)) |
| Same text, many publishers | four publishers or more post an offer with the same title and the same opening text |
| Known for fake listings | its publisher is Digistrat, Propulse IT or Omicrone, whose Free-Work offers Indezy drops as fake listings |

The rules are read again for every open offer every hour, three minutes after a server starts and after the nightly
medians, so a new offer is flagged within the hour.

### Site Health

**Job boards**, linked from the feed's summary (which says how many boards fail, when some do), lists every board
Indezy reads, failing ones first, each with its status (working, failing with its failed readings in a row, reading
now, or not read yet), its last successful reading, its next one, the offers it brought over 24 hours and 7 days
(first seen then), its open offers, its newest offer (flagged when nothing new came for a month: the board may be
dying), what its last reading found (flagged when a successful reading found no offer: its pages may have changed)
and its last error. Below come the sites to visit yourself and the boards left out
and why.

- **Sites to visit yourself** (also linked from the feed's summary): the sites Indezy does not read but where missions
  are found independently, each with what it takes and why. Missions to see in person need a paid membership
  (HiTechPros), an account (Opteamis, LookingForTechs), or are open to people but closed to
  crawlers (Capgemini TalentNet, HelloWork); matching services take a profile once and call back (Malt, Freelance.com,
  WheelOfWork, Comet, Amazing.dev, Crème de la Crème; FreelanceRepublik and EasyPartner match profiles too, and the
  feed reads the missions they publish). Opening a site, or **Mark as
  visited**, records the day of the workspace's last visit, shown as "Visited 3 days ago" until it is forgotten; visits
  are part of the account data export. The boards left out for other reasons (LinkedIn, Mon Consultant Indépendant,
  Nexoris, Welcome to the Jungle and recruitment firms with CDI jobs only) are listed after them.

- **Show in my feed**: switched off, the board's offers leave the workspace's feed, its saved searches' new offers and
  market snapshot, and its copies leave the **Also on** links (another site's copy of a mission then stands for it);
  saved and hidden offers stay in their views. The feed's site filter and its counts leave the board out too. Each
  workspace chooses for itself, and the choice is part of the account data export.
- **Read now** reads the board at once, in the background, at most once every 15 minutes, whoever asks: 15 minutes
  after its last reading or request, and never while a reading runs (the server answers `429` with `Retry-After`
  otherwise, and the page says when it is possible again). It is off where collection is off (tests, one-off runs).

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

The project page lists the steps as a timeline: each step can be added or edited (title with suggestions, status, local date and time, notes), moved to another status from its status pill, or deleted, and the header summarizes how many steps are validated.

Each step can carry a preparation checklist (up to 30 items of 200 characters, kept in their order). The step dialog
adds items, removes them, ticks them and offers suggested ones in the interface language (reread the job description,
research the client, prepare project examples and questions, decide the rate to defend, test the video call); an item
already listed, whatever its case, is not added twice. On the timeline the checklist shows how much is ready
(**Preparation · 2/4 ready**) and each item is ticked in place, saved with the step and put back if the save fails.
The checklist travels in `InterviewStepDto.checklist`; a step saved without one has none.

Recruitment processes can be reused: **Templates** on the steps card saves the opportunity's
steps, in their timeline order and with their checklist items, as a named template kept for
its ESN (proposed first, since an ESN keeps its process across clients), its client, or any
client. The same menu lists the workspace's templates, the opportunity's ESN's and client's
first, each with how many steps it holds; picking one adds its steps to the opportunity, to
plan, without dates, their checklists unticked, after the steps it already has. A template is
deleted from the menu after a confirmation; the steps it added stay. A workspace keeps up to
50 templates with distinct names (case aside), deleting a client keeps its templates for any
client, and the GDPR export includes them (`/pipeline-templates`). A template can also be written out step by step
(`POST /pipeline-templates/steps`, up to 20 steps of 20 checklist items), as the welcome steps' starter processes are. The backend exposes interview-step endpoints for creation, updates, transitions, and project-level retrieval.

## Emails From Templates

**Write an email** on an opportunity's page opens a composer: a template, a recipient among the contacts (with an
email address) of the opportunity's ESN and client, the ESN's first, then a subject and a message filled from them and
free to edit. **Open in my mail app** opens a `mailto:` message to the contact, **Copy** copies the subject and the
message. The follow-up, rate proposal and availability announcement are always offered, in the interface language;
Profile → **Emails** adds, changes and deletes the workspace's own templates (up to 50, each name once, case aside),
their variables inserted by clicking them.

Variables, written `{{name}}`: `firstName`, `lastName` (the recipient), `company` (the recipient's client or ESN),
`role` and `rate` (the opportunity's daily rate), `availability` (the profile's availability date), `myName` and
`myPhone` (the profile), `privacyNotice` (who keeps the recipient's details and why, see
[Contact Privacy](#contact-privacy)). A variable without a value stays as written, and the composer says the message still has some
to complete. Templates are stored per workspace (`/email-templates`) and included in the GDPR export; nothing is sent by
Indezy.

## Calendar

**Calendar** in the navigation has two views, **Month** and **Upcoming**, switched at the top and kept in the address
(`/calendar?view=upcoming`).

**Upcoming** lists the planned interview steps and the calls booked through the booking link from now on, soonest
first, each with its day and time: a step with its opportunity, which it opens, a call as "Call with <name>" with the
company or email. It replaced the former **Timeline** page, which listed the planned steps only; `/timeline` opens it.

**Month** shows a month, Monday first, of every dated interview step across the opportunities:
each day lists up to three steps by time, coloured by status, then how many more, and the picked day (today at first)
lists all of them below the grid with their opportunity, which they open. The arrows and **Today** change the month,
kept in the address (`/calendar?month=2026-10`). On a phone, days show how many steps they hold and the list below
gives them. Calls booked through the booking link show among the steps (in teal), in the day's list as "Call with
<name>" with the company or email.

## Booking Link

The **Booking link** card under the calendar lets recruiters book a call without trading emails, Calendly-style.

- **Settings**: on or off, the page's title, a call's length (15 to 60 minutes), the gap kept around each call (0 to
  30 minutes), the minimum notice (none to 48 hours), how far ahead calls can be booked (7 to 60 days), and one range
  of hours per weekday, in Paris time. The first save gives the link its address (`/book/<16 letters and digits>`);
  **New link** gives it another one, and the old address stops working at once.
- **Free times**: each start a call's length apart within the open hours, from the minimum notice to the last day
  ahead, leaving out French public holidays and any time that would overlap, with the gap, a call already booked or a
  planned interview step (taken as one hour; cancelled and failed steps leave their time free).
- **The public page**, opened without an account, gives the free times day by day in the visitor's time zone; the
  visitor picks one and gives a name, an email, and optionally a company and a message. A time taken meanwhile is
  refused and the times shown again; a link off or replaced says it does not work.
- **Calls booked**: the freelancer gets an email for each, within about a minute (unless turned off in **Profile →
  Notifications**), and webhooks listening to `CALL_BOOKED` are told. The card lists the calls to come with their
  details, each cancellable (Indezy sends the recruiter no email: the freelancer tells them). The calendar shows them on
  their day.
- The page's settings and the calls are part of the account data export.

API: `GET` and `PUT /api/users/booking-page`, `POST /api/users/booking-page/new-link`, `GET /api/bookings` (from 30 days
ago), `POST /api/bookings/{id}/cancel`; without an account, `GET` and `POST /api/public/booking/{link}` (see
[Security](./security.md#booking-link)).

## Project Journal

Each opportunity's page has a **Journal**: its history in one place, newest first.

- Notes (calls, decisions, impressions) are written in Markdown with a live preview, can start from a template (call
  debrief, negotiation summary...), and can be deleted (`/api/projects/{projectId}/notes`: list newest first, add,
  delete).
- The exchanges with contacts recorded about the opportunity (emails, calls, LinkedIn messages, meetings, see
  [Contact Interaction History](#contact-interaction-history)) appear among the notes by their day, with whom, the
  channel, who started it, whether it was answered and its summary, linked to the contact's page.
- **Record an exchange** notes one with a contact of the client or its ESN, tied to the opportunity; it is changed or
  deleted from the contact's page, whose exchange form can also tie any exchange to one of the opportunities in the
  pipeline. `GET /projects/{projectId}/interactions` lists the exchanges about an opportunity; deleting the opportunity
  keeps them, about none.

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
[Security](./security.md#activity-report-validation-links)). A month with days worked, unless rejected, can be invoiced
from the mission's page or from **Invoices** (see [Invoices](#invoices)).

## Purchase Orders

A won mission's page shows its **Purchase orders** under the activity reports: the client's orders (bons de commande)
and their amendments (avenants), each order followed against the days of the mission's activity reports.

- **Add a purchase order** asks its number, start, optional end (open-ended without one), a ceiling in days, in euros
  before VAT, or both, and notes. **Add an amendment**, in the order's menu, asks the amendment's number and what it
  adds: days, euros and a later end (at least one). An amendment adds its days and euros to its order's ceilings, and
  the latest end of the order and its amendments is when the order ends. Orders and amendments can be changed and
  deleted; deleting an order deletes its amendments.
- **Usage** counts the days worked in the mission's activity reports (any status; half days count for 0.5, absences not
  at all) between the order's start and its end, and their amount at the mission's daily rate. The card shows the days
  and euros used against each ceiling with a progress bar of the closest one, and the days left (or over) before it;
  without a daily rate, a ceiling in euros cannot be followed and the card says to set the rate.
- **Alert**: from 80 % of a ceiling the order says it is time to ask for an amendment, and over 100 % that it no longer
  covers the work. The dashboard's reminders list the running orders (started, not ended) at 80 % or more, the most
  used first, with their mission and the days left, linking to the mission.
- Purchase orders are part of the account data export and are deleted with their mission.

API: `GET` and `POST /projects/{projectId}/purchase-orders` (an amendment names its order in `amendsId`), `PUT` and
`DELETE /purchase-orders/{id}`; each write answers with the order as it now stands, with its amendments and its usage.
Another workspace's mission or order answers `404`; the dashboard statistics carry `purchaseOrderAlerts`.

## Offline Use

In production the app installs as a progressive web app (the browser's **Install** offer) and keeps working without a
connection for what it already showed. A service worker (`ngsw-config.json`) keeps the app itself, and the latest
answers of the workspace's main pages (opportunities and the board, a mission with its notes, activity reports, files
list and purchase orders, interviews, clients, contacts, sources, seasons, invoices, documents list, profile and
preferences) for seven days: online, each page asks the server first and falls back to the kept answer after six
seconds; offline, it shows the kept answer. A banner says when the browser is offline, and a change attempted meanwhile
says it needs a connection. Downloads (PDFs, exports, files and documents), the account's sessions, API tokens and data
export, the admin console and the public pages are never kept. Signing out forgets the kept answers, so the next person
on the browser cannot read them. The service worker and its manifest are always revalidated (`nginx.conf`), so a new
release reaches the browser on its next visit.

## Compliance Documents

**Documents** (`/documents`) keeps the papers ESNs ask for before and during a mission, and shares them through one link
instead of email attachments.

- **Add a document**: its type (URSSAF vigilance certificate, company registration extract (Kbis), professional
  liability insurance, bank details (RIB), identity document, or another document, which needs a label), an optional
  label, the issue and expiry dates, and a PDF, PNG or JPEG file of 10 MB at most (the type is read from the content;
  a web page renamed `.pdf` is refused). Without an expiry date, an URSSAF certificate expires six months after its
  issue and a registration extract three months after. Up to 30 documents; each can be downloaded, changed (not its
  file) and deleted.
- **Validity**: valid, expires soon (within 30 days), expired, or without expiry (bank details). The documents expired
  or expiring within 30 days are among the dashboard's reminders (**Documents to renew**, the soonest first), linking
  to the page.
- **Share**: who the link is for (an ESN, a recruiter), the documents (the expired ones unticked at first) and how long
  it works (7, 14 or 30 days). The link (`/documents-pack#<token>`) is shown once, to copy or send by email; the page
  lists each link with its documents, until when it works, and how many times it was opened, and **Revoke** closes it
  for good. Deleting a document takes it out of the links that share it.
- **The shared page**, opened without an account, says who shares the documents with whom and until when, and offers
  each document to download. An unknown, expired or revoked link says it no longer works.
- Documents (without their content) and links (without their token) are part of the account data export.

API: `GET` and `POST /api/compliance-documents` (multipart: `file`, `type`, `label`, `issuedOn`, `expiresOn`), `PUT` and
`DELETE /api/compliance-documents/{id}`, `GET /api/compliance-documents/{id}/content`, `GET` and `POST
/api/compliance-packs` (`{ recipient, documentIds, days }`, answering the token once), `POST
/api/compliance-packs/{id}/revoke`; without an account, `GET /api/public/compliance-packs` and `GET
/api/public/compliance-packs/documents/{id}` with the token in the `X-Pack-Token` header (see
[Security](./security.md#shared-compliance-documents)). The dashboard statistics carry `expiringDocuments`.

## Kanban And Pipeline View

The dashboard includes a Kanban mode for moving opportunities across high-level statuses. Drag-and-drop changes project status through the project and interview-step APIs. Cards can be pinned as favorites (`PATCH /projects/{id}/favorite`), which keeps hot leads at the top of their column. Within a column, cards can be dragged to set a manual priority, persisted through `PUT /projects/kanban/{freelanceId}/reorder` (favorites still pin above the manual order). Each card shows a card-aging indicator (days since last activity) and highlights opportunities after the account's configured inactivity period (14 days by default). A quick-add button on each column opens a minimal dialog (role, client, daily rate) that creates an opportunity directly in that column's status, to be enriched later. Moving a card into the LOST column prompts for a loss reason, which feeds a "why opportunities were lost" breakdown on the dashboard. Cards that share the same client and role (case-insensitive) as another opportunity are flagged as possible duplicates for manual deduplication.

Profile → Preferences lets the user set the inactivity period to 1–365 whole days.
The same saved value controls dashboard follow-up reminders and Kanban highlighting;
it counts elapsed days since the opportunity's last update, including the threshold day.
Saving other preferences leaves this setting unchanged when it is omitted. Existing
accounts without a saved value keep the 14-day default.

Opportunities can be archived: they leave the Kanban board and the stale reminders but keep
their status, so the statistics and funnel still count them. **Archive** on an opportunity's
page (contact, interviews or offer) archives it by hand; Profile → Preferences → **Archive idle
opportunities every night** (off by default) lets the server archive, at 03:30 Paris time,
every pipeline opportunity with no update for the inactivity period: exactly the ones the
board flags. Archived opportunities are listed in the **Archive** view with the won and lost
ones, by when they were archived, and marked **Archive** in the projects list; **Restore**,
there or on the opportunity's page, puts it back in its column and counts as activity, so the
inactivity period starts again. `INDEZY_PROJECTS_ARCHIVING_ENABLED=false` stops the nightly
run.

Each card has a checkbox, next to its favorite star, that picks it without opening it; the
projects list has one on each card too, and one beside the results count that picks the whole
page. While opportunities are picked, a bar above the board or the list counts them and acts on
all of them at once: **Move to** a column (Lost asks for the reason first), **Source** gives them
one of the workspace's sources, **Add a tag** adds a typed tag to each tech stack that lacks it
(case aside; a comma becomes a space, and a tech stack that would pass 255 characters is left
as it is), **Archive** archives them, **Delete** deletes them once confirmed (with their notes,
files and activity reports, as a single delete does), and the close button clears the
selection. The change goes through `PATCH /projects/bulk` (`projectIds`, up to 200, `action`
`STATUS`, `SOURCE`, `TAG`, `ARCHIVE` or `DELETE`, with `status`, `lostReason`, `sourceId` or
`tag`), which checks that every opportunity and the source belong to the caller's workspace and
applies it in one transaction, answering how many changed; the page then reloads, selection
cleared.

The clients and contacts lists have a checkbox column too (its header picks the page). Their bar
counts the picked rows and deletes them once confirmed: `POST /clients/bulk-delete` and
`POST /contacts/bulk-delete` (`ids`, up to 200, all in the caller's workspace) answer how many
were `deleted` and `kept`. A client still linked to missions is kept, as a single delete
refuses it, and the message says how many; a client goes with its contacts, a contact with its
exchanges.

The board has five status-driven columns: Contact, Tests & Entretiens, Offre, Accepté and Perdu / Abandonné. Intermediate recruitment steps (technical test, client interview...) are tracked as interview steps inside the Tests & Entretiens column. The board follows the season selected on the dashboard (`GET /projects/kanban/{freelanceId}?seasonId=`), and cards quick-added to it join that season.

## Job-Hunting Seasons

A season is a bounded job-hunting period, like a sprint: "Autumn search 2026" from 1 August until the right mission is signed. Each season has its own pipeline and its own dashboard instead of one dashboard that is always on.

- A season has a name, a start date, an optional end date (empty while it runs), an optional target daily rate and a free-text objective. New seasons default to the profile's target daily rate; the form allows an override. Leaving a new season's target blank uses the profile default when one is set. Editing an existing season preserves its own target, which can be cleared independently.
- Starting a season (dashboard → season picker → New season) adopts the workspace's opportunities that are not in a season yet and were created within its dates. New opportunities join the season running on the day they are created, unless the form or the board picks another one.
- The dashboard season picker switches the whole dashboard (KPIs, charts, funnels, analytics, recent projects) and the Kanban board to one season, or to all seasons. The choice is remembered in the browser.
- A season dashboard opens with a summary banner: dates, days elapsed, objective, opportunities, success rate (accepted out of decided), accepted missions and average vs target daily rate, each compared with the all-season value.
- A season can be edited, closed (it then ends the day before, so new opportunities no longer join it) or deleted; deleting keeps its opportunities, which simply no longer belong to a season.
- Contact reminders (dormant contacts, anniversaries) stay workspace-wide.

API: `GET /seasons/by-freelance/{freelanceId}`, `PUT|DELETE /seasons/{id}`, `POST /seasons`, plus the optional `seasonId` query parameter on `GET /projects/kanban/{freelanceId}` and `GET /projects/stats/dashboard/{freelanceId}`. Seasons are private to their workspace like every other record.

## Dashboard And Analytics

**Customize** (above the overview) arranges its six blocks for the account: key figures, charts, reminders, new
offers, analytics and recent opportunities. Each is shown or hidden with its checkbox and moved by dragging its handle
or with its arrows (from the keyboard too); **Usual layout** puts them back. The layout is saved with the account's
preferences (`dashboardWidgets` of `GET`/`PUT /api/users/preferences`), so it follows the account to every browser; a
block added later appears, shown, at the end. Resizing blocks is not offered: each takes the page's width and arranges
its own cards for the screen.

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
- trends (**Last 90 days**, first among the analytics): opportunities added, interviews held (cancelled ones left out),
  missions won (on their latest validated interview step, or else their start) and the average daily rate they were
  won at, each over the last 90 days against the 90 days before, with the change in percent (up in green, down in red;
  "nothing to compare with" when the earlier period has none) and the earlier value. They follow the selected season
  (`trends` in the dashboard statistics).
- profile target daily rate compared with the average recorded rate of accepted missions
  in the selected season or global view; unknown accepted rates are omitted from the average,
  and a dash is shown when no accepted rate is known
- source ROI ranking (signed contracts vs total opportunities and conversion rate per source)
- daily rate evolution by year (average asked vs obtained daily rate)
- bench time tracking (idle days and number of gaps between signed missions, with an estimated cost)
- pipeline conversion funnel (how many opportunities reach each stage, to reveal where they drop off), overall and broken down by source, client type (direct vs through an ESN) and ESN
- interviews of the week (steps planned in the next seven days, soonest first, with their time and opportunity), first among the reminders
- mission end-date reminders (signed missions ending within six weeks, so prospection can restart before the bench)
- stale-opportunity detection (active opportunities idle for the configured inactivity period, surfaced to follow up or archive before they go cold)
- new missions from the offer feed: the five offers not seen yet that match the profile best (or the five newest, with
  an invitation to add skills, when the profile names none), each with its publisher, rate, place, site and score;
  opening one on its site marks it seen, and **Open the feed** shows them all; a feed that cannot be read hides it
- recent projects
- overview and Kanban display modes
- a season picker that scopes all of the above to one job-hunting season (see [Job-Hunting Seasons](#job-hunting-seasons))

The analytics are intentionally operational: they help a freelancer understand pipeline volume, activity, rate distribution, which sources actually yield signed contracts, where opportunities drop off in the funnel, and how negotiated rates trend over time.

Profile → Personal information includes an optional target daily rate in whole euros.
It must be positive when set and can be cleared. Changing it affects the dashboard comparison
and defaults for future seasons; it never rewrites targets already saved on existing seasons.

## Yearly Review

**Year in review** (in the navigation, `/review`) sums up one calendar year of the workspace, the current one first;
the arrows move to the years before, back to 2000 and never past the current year.

- **Key figures**: revenue, days worked, the average daily rate they make (revenue over the days of missions with a
  rate) and the number of missions, with the revenue change on the year before (or "Nothing in" that year).
- **Missions of the year**: each opportunity won whose start date falls before the year's end and whose end (start plus
  its duration, open-ended without one) falls after its start, earliest first, with its client, period, days, daily
  rate and revenue. Days come from the mission's activity reports of that year when it has any; otherwise they are
  estimated from the calendar days it covers in the year, as a share of the year's length, times its days per year
  (218 when it states none), rounded to half days, and marked with an asterisk. Phones leave out the period column.
- **Opportunities**: those added during the year, how many of them were won and lost, the won share of the decided
  ones, and why the lost ones were lost, most frequent reason first.
- **Skills most asked for**: the five skills listed most often by the opportunities added during the year, with
  aliases merged as in [Skill Names](#skill-names).
- **Network**: final clients, ESNs and contacts added during the year, and the exchanges recorded in it
  (see [Contact Interaction History](#contact-interaction-history)).

**Download PDF** exports the same review as a one-page A4 report in the interface language
(`indezy-year-in-review-YYYY.pdf` in English, `indezy-bilan-YYYY.pdf` in French), with the freelancer's name, the
figures, the missions table and its estimate footnote, the opportunities, the skills and the network.

The review is served by `GET /api/projects/review/{freelanceId}?year=` and its PDF by
`GET /api/projects/review/{freelanceId}/pdf?year=&language=FR|EN`; both answer `403` for another workspace and `400`
for a year outside 2000–2100.

## Filtering And Search Surfaces

Every list uses the same kind of inline filters, applied in the browser as you type:

- projects: search, work mode, rate range, status, season, dates, duration, client and tech stack, with sorting, pagination and filters collapsed on small screens
- clients: search, type (final client or ESN), city and sort
- contacts: search and client
- sources: search, type and minimum usefulness rating
- archive: full-text search across past and archived opportunities, archived ones restored to their pipeline column

## Keyboard Shortcuts

Signed-in users can open shortcut help from the account menu or with `Alt+Shift+H`.
`Alt+Shift+N` opens a new project; `Alt+Shift+F` opens project search and focuses its
field, expanding the filters on phones. `Alt+Shift+M` opens the stage menu for the
current project, or opens the focused Kanban card with that menu ready. Choose a
stage to move it; moving to Lost still requires the existing loss-reason dialog.

In the offer feed, single keys act while one of its offers has the focus (Tab to
the list, one tab stop for all offers): `J` and `K` (or the arrow keys) move to the
next and previous offer, `O` opens it on its site, `S` saves it, `H` hides it, `A`
adds it to the pipeline, `D` shows or folds its details, `N` opens its note and `X` selects it; when an offer leaves the
list, the next one takes the focus. The shortcut help lists them, and screen readers
hear them from the list's description.

`Alt+Shift+S`, or the search button in the top bar, opens the **global search**: typed words (two characters at
least) are looked up in the workspace's opportunities (role, client, tech stack, description, notes), clients (name,
city, website, notes), contacts (name, company, email, phone, notes) and journal notes, accents and case aside; a record
matches when one of its fields holds every word. Up to 8 hits of each kind are listed under their heading, with the
words highlighted and, when they were found in a longer text, the passage around them. The arrow keys move through the
hits (wrapping round) and Enter opens one; a note opens its opportunity. API: `GET /search?q=` answers the
`projects`, `clients`, `contacts` and `notes` found, each hit with its `title`, `subtitle`, `excerpt` and, for a
note, `projectId`.

Shortcuts do not run inside editable fields, open menus, selection panels or dialogs
(lists of chips shown in the page, such as the feed's filters, do not stop them),
or during composition and repeated key presses. They are available in both
French and English, and leave ordinary browser shortcuts unchanged.

Every page works from the keyboard alone, with a visible focus in the order the page reads. Cards and rows opened by a
click open with Enter once focused (Kanban cards, archived opportunities); Enter on a control inside
them, such as a card's favourite star, stays with that control. Table rows opened by a click (clients, a client's
opportunities and contacts) carry their name as a link to tab to. On the Kanban board, where cards are dragged between
stages with the mouse, a focused card's stage menu (`Alt+Shift+M`) or its checkbox and the selection bar move it. The
calendar's focused day has its own double ring, distinct from the selected day's outline, and the email templates'
variables are buttons that insert their placeholder.

## Commute-Time Sorting

**Sort by → Commute time** on the opportunities list orders the missions by what their commute costs a month, the
cheapest first, by car or public transport:

- one trip's time and distance from the profile's address to the client's, from Google's Distance Matrix;
- the days on site a month: none for a remote mission (shown as "Remote: no commute", first, nothing looked up), the
  mission's own days on site, else 20 less its remote days, else 20 on site and 10 hybrid;
- the month's commute, there and back each day on site (`1 hour (60 km) · 16 h a month there and back (8 days on site)`);
- a mission without a time says why, last: no home address (with a link to the profile), a client without an address,
  no Google Maps key on the server, no route found, or Google not reachable right now.

Destinations are asked 25 at a time, and each answer is kept a week per address and mode, so sorting again asks Google
nothing; a failed lookup is not kept. Without `GOOGLE_MAPS_API_KEY` (`google.maps.api-key`) nothing is asked. See
[Development](./development.md) and [Deployment](./deployment.md) for setup details.

API: `GET /api/commute/projects/{freelanceId}?travelMode=DRIVING|TRANSIT` answers each mission with its `commute`
(`durationInSeconds`, `distanceInMeters`, `remote`, `onsiteDaysPerMonth`, `monthlySeconds`, `unavailable`), sorted.

## Reversion Rate Calculator

Indezy includes reversion-rate oriented finance fields and calculator behavior for:

- net daily revenue
- monthly revenue
- yearly revenue
- configurable income tax
- max workable days with French public holidays

The projects list also offers an accountant CSV export (`GET /projects/export/csv/{freelanceId}?year=YYYY`): one row per project (role, status, client, source, work mode, start date, duration, daily rate, days per year, estimated revenue) plus a totals row, optionally filtered to a single year.

The next finance work should extend this with deductible expenses, social contributions, status-specific assumptions, and invoice support.

## Admin Console And Feature Flags

**Admin** (in the navigation of administrators only, `/admin`) is opened by the accounts the deployment names in
`INDEZY_ADMIN_EMAILS` (comma-separated emails, case aside; none by default, so nobody is an administrator until one is
named). Every other account is sent back to the dashboard, and the API answers `403`.

- **Overview**: the accounts, the sign-ups of the last 30 days, the disabled accounts, and how many workspaces use each
  feature (or how many accounts turned each setting on) with their share of the accounts, most used first: the counts
  behind the Grafana **Feature Adoption** panel, read at most once an hour.
- **Accounts**: every account, newest first, 20 a page, found by email, first or last name. **Disable** (after a
  confirmation) signs the account out at its next request and refuses its password and Keycloak sign-ins until
  **Enable**; its data is kept. An administrator's account cannot be disabled, and a deleted account shows as such.
- **Features**: turns a feature off or on again for every account, without a redeploy. A feature turned off leaves the
  menu and its pages (opening one goes to the dashboard), and its API answers `403` "This feature is turned off":
  - **Offer feed**: the offers, saved searches and their alerts (`/offers/**`);
  - **Public profiles**: the public page and its profile tab (`/public/profiles/**`, `/users/public-profile`);
  - **Data import**: the import on the profile's **Account** tab (`/users/import/**`);
  - **Yearly review**: `/review` and its PDF (`/projects/review/**`).

  Every feature is on until turned off. The states are stored in the database and read again by each server at most
  every 30 seconds, so a change reaches every replica within that time. Each account reads what it may open from
  `GET /api/users/capabilities` (`{ admin, features }`) when it signs in.

The console's API is `GET /api/admin/overview`, `GET /api/admin/accounts?query=&page=`,
`POST /api/admin/accounts/{id}/disable|enable`, `GET /api/admin/features` and `PUT /api/admin/features/{flag}`
(`{ "enabled": false }`). Each change is logged with the administrator's and the account's ids, never their emails.

AI model and cost management waits on the choice of an AI provider (see TODO.md, Needs Input).

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
- job boards other than the seventeen the offer feed reads (the next ones are planned in TODO.md)
- Gmail, Outlook, or LinkedIn sync
- Chrome extension capture
- cross-platform mobile app
- AI parsing, ranking, or recommendation workflows
- invoice generation (activity reports provide the validated days it will bill)
- qualified electronic signature: an activity report is approved with the validator's typed name and a timestamp, a simple electronic signature
- calendar sync

Keep this distinction sharp. It prevents the docs from promising features that exist only as model placeholders or backlog intent.

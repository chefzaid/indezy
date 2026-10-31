# Features

What a user can do in Indezy today: for each feature, where it lives and the rules that are not obvious from the
screen. Planned work lives in [TODO.md](../TODO.md). The REST API is described by its OpenAPI document, served as
Swagger UI at `/api/swagger-ui.html` (see [Development](./development.md)); the protection of public links, uploads and
tokens is in [Security](./security.md).

## Product Shape

Indezy is a freelance opportunity tracker for the French tech market: prospects, clients, ESNs, contacts, interview
steps, rates, missions, invoices and their paperwork in one place. The core workflow: fill in the profile; find
missions in the offer feed or add opportunities by hand or from a link; track them through the pipeline and their
interview steps; run won missions (activity reports, purchase orders, invoices); let the dashboard say what needs
attention.

The menu lists **Dashboard**, **Calendar**, **Offers**, **Opportunities**, **Clients**, **Contacts**, **Sources**,
**Year in review**, **Timesheets** (CRA), **Invoices**, **Documents** and **Archive**, then **Admin** for administrators.

## Presentation Page

`/` presents Indezy in French and `/en` in English (a first visit from an English browser goes to `/en`): what it does,
how it works, how the data is kept, and screens from a demo workspace whose people and offers are invented. Signed out,
**Sign in** opens Keycloak; **Create my account** shows outside production only, as sign-up in production waits on a
decision (TODO.md, Needs Input). Signed in, **Open the dashboard**. The page never sends a visitor to sign in, and an old
`/?project=<id>` link still opens its opportunity. Both languages are prerendered, so search engines and link previews
read them without JavaScript.

## Authentication And Account Access

Local accounts register and sign in with an email and a password of 8 characters to 72 bytes (an accented letter counts
as 2, an emoji as 4), and can turn on TOTP two-factor authentication. In production, sign-in goes through Keycloak. The
profile page has the tabs **Personal information**, **Preferences**, **Notifications**, **Security**, **Billing**,
**Emails**, **Public profile**, **Offer feed** and **Account**, where **Export my data** downloads every piece of the
account's data as JSON.

### Sessions And Account Activity

**Profile → Security** lists every open session with its browser and system (no address is kept), how it was opened
and when it was last used; any other session can be signed out, or all with **Sign out everywhere else**. A token stops
working as soon as its session ends: **Sign out** ends it on the server, a new password ends every other one, and an
administrator disabling the account ends all. **Account activity** lists sign-ins (and refused ones), sign-outs, ended
sessions, password and two-factor changes, exports and imports, contact data requests and administrator actions, kept a
year; ended sessions are kept 30 days.

### API Tokens

**Profile → Security → API tokens** creates personal tokens for a script, a spreadsheet or an automation service: up to
10 per account, lasting 30, 90 (the default) or 365 days, or never expiring. A token is shown once, then listed by its
name and first characters with its last use; **Revoke** cuts it off at once.

- A token only reads (`GET`, `HEAD`), with the app's workspace checks, and never reaches the account, its sessions,
  tokens or export, the admin console or sign-in (`403`).
- **It reads** limits a token to some parts: opportunities and interviews (with seasons and templates), activity reports
  and purchase orders, clients, contacts and sources, invoices, compliance documents, or offers. A limited token cannot
  use what belongs to no part (the global search, the profile); a token with nothing ticked reads everything.

### Webhooks

**Profile → Security → Webhooks** tells the freelancer's tools (Zapier, Make, n8n, a script) of the workspace's events:
an opportunity added or moved, an activity report sent, approved or rejected, an invoice or credit note issued or paid,
a call booked. Up to 5 per workspace, each an HTTPS address on a public host; its secret is shown once.

- Each event is posted within about twenty seconds as JSON (`{ id, event, createdAt, data }`, ids and business fields,
  never a recruiter's name or email), signed in `X-Indezy-Signature: sha256=<hex HMAC-SHA256 of "<timestamp>.<body>">`
  with the timestamp in `X-Indezy-Timestamp`.
- A 2xx answer within ten seconds counts; otherwise it is retried after 1, 5 and 30 minutes, 2 and 12 hours. Redirects
  are not followed. After 20 failures in a row the webhook turns off until **Turn on**.
- **Send a test** posts a `PING`; **Latest deliveries** shows the last 20, kept 30 days.

### Notifications

**Profile → Notifications** switches, each saved at once: the [weekly digest](#weekly-digest) (email, off by default,
each section kept or left out), an email for each call booked through the [booking link](#booking-link) (on), and the
count of the saved searches' new offers beside **Offers** in the menu (on). Interview, follow-up and quiet-opportunity
reminders always show on the dashboard; saved searches set their own [emails](#saved-search-emails). The tab says when
the server cannot send email.

### Weekly Digest

Every Monday from 8:00 Paris time, in the interface language: open opportunities by stage, then up to five overdue
invoices, interviews of the next 7 days, follow-ups due, quiet opportunities and missions ending, and the five offers
of the week matching the profile best. Empty sections are skipped and an empty week sends nothing. Each account gets
one digest a week whatever the number of servers; the email has a one-click unsubscribe and no tracking.

### Data Import

**Profile → Account → Import data** previews an Indezy export (up to 10 MB) section by section: sources, clients and
ESNs, contacts, seasons, opportunities with their steps and checklists, and journal notes, each record **new**,
**already here** or **left out** with why. **Import** saves the new ones in one go; nothing is written before.

- Existing records are kept and reused, so a file imported twice adds nothing: sources, clients and seasons match by
  name (case, accents and punctuation aside), contacts by email (else name and company), opportunities by role, client
  and start date.
- Invalid records, or records pointing outside the file, are left out. Journal notes come with the opportunities the
  import adds, dated that day. Contacts' managers, the profile, activity reports, offers, templates, attachments,
  exchanges and contact links are not imported.

### Welcome Steps

A new account opens on **Welcome to Indezy** until it is finished or skipped (**Skip the setup**): about you (name,
title, up to 30 skills, address), rate and availability (saved to the profile), a starter recruitment process
(**Through an ESN** or **Direct client**, with checklists, or **Not now**), then links to add an opportunity, browse
missions or open the dashboard. Accounts older than the welcome steps never see them.

## Freelancer Profile

**Profile → Personal information** holds the identity opportunities use: name, contact details, address and city,
company and title, presentation, website, LinkedIn and GitHub, photo, languages, skills, target daily rate (a positive
whole number of euros) and **Available from**. The city feeds offer distances and the match; the availability date the
match; the target rate the match, suggested searches, the dashboard and new seasons' default target.
**Days to bill per year** (1 to 366, optional) puts a **Billed days** card on the dashboard: the days of this year's
validated activity reports against the target, with the share reached, and the year at the pace of its completed months
(none in January), saying how many days above or short of the target that pace ends. The card shows without a target
once a report of the year is validated.
**Longest drive** and **Longest public transport trip** (1 to 600 minutes, one way at rush hour, optional) flag the
missions whose commute by that mode takes longer, in the sorted list, on the opportunity's commute card and on its
Kanban chip, and set what the offer match calls near: an offer whose estimated trip fits either limit is near, one
within one and a half times it reachable (instead of 30 and 80 km).

- Skills are free text with suggestions: up to 50 of 60 characters, each once whatever its spelling, each with an
  optional level (beginner to expert) and years (0 to 60).
- **Profile → Preferences** sets the language and theme, the inactivity period, the follow-up delay, nightly archiving
  and the contacts' retention period. The reversion and income tax rates are set from the opportunities list.

### Skill Names

Spellings of a skill are one skill, case, accents, spaces, dots and dashes aside ("Spring Boot", "spring-boot"), and
about thirty skills have aliases ("JS" for JavaScript, "K8s" for Kubernetes, "Postgres" for PostgreSQL). The profile
says when a spelling is already listed, suggests skills from aliases and offers to merge duplicates (keeping the higher
level and years); the profile match counts a skill once; the dashboard's skills in demand, the yearly review, the bulk
**Add a tag** and tag renaming fold spellings together. The opportunities list's technology filter still compares text.

### Public Profile

**Profile → Public profile** publishes, once turned on, a page at `/p/<address>` that anyone with the link can read:
name, title, city, presentation, skills with levels, languages, links, photo and availability. The email, phone,
address and workspace records stay private. The address has 3 to 40 lower-case letters, digits and single dashes, one
per account. A hidden profile and an unknown address both answer "No profile here".

## Projects And Opportunities

**Opportunities** (`/projects`) lists the workspace's opportunities and missions. Beyond the role, stage, description,
tech stack, dates, duration, work mode and remote days, an opportunity holds:

- its **client** (the final client, or the ESN when the final client is unknown, as for offers added from the feed), an
  optional ESN or intermediary, a source and a season;
- the client daily rate, from which the margin is shown, and the asked and offered rates beside the agreed one;
- who refused it (the recruiter or the freelancer, asked on every move to Refusé) and optionally why;
- **Files** in its **Documents and links** card: PDF, images, Word, OpenDocument or text, up to 10 MB each and 20 per
  opportunity.

Its page changes the stage in place and shows the tech stack as tags with how well they match the profile (a tag can
be renamed across every opportunity), the steps, journal and emails, and once won the activity reports and purchase
orders. **Copy project link** copies an address that opens the page for the owning account only.

The list's filters (words, rate range, work mode, technologies, stage, season, dates, duration, client, **Archive**) and
sort live in the page's address, so a view can be bookmarked or shared. **Export summary** downloads the opportunities
starting this year as CSV.

| Stage | Column (French / English) |
| --- | --- |
| `CONTACT` | Contact |
| `INTERVIEW` | Tests & Entretiens / Tests & Interviews |
| `OFFER` | Offre / Offer |
| `LOST` | Refusé / Declined |
| `WON` | Accepté / Accepted |

### Non-Solicitation Clauses

An opportunity with an ESN takes the months of the ESN's non-solicitation clause (1 to 36, optional, often 12). Once
that mission is won, every other opportunity for its end client, sent directly or by another ESN, carries a notice on
its page (`Non-solicitation: you worked for Acme through ESN Conseil; taking this mission without ESN Conseil breaches its
clause until March 31, 2027.`, linking the mission) and a gavel on its Kanban card, until the months have passed after
the mission's last day (an open-ended mission: "until 12 months after that mission ends"). An archived opportunity is
not flagged. `GET /api/projects/non-solicitation/{freelanceId}` lists them.

### Contract Checklist

A won mission's page holds a **Contract checklist** of the points to check in the ESN's or client's contract before
signing: payment terms, notice period, non-solicitation, intellectual property, liability and insurance, rate revision and
place of work. Each is ticked and noted (up to 500 characters, an example as placeholder), saved as it is ticked or its
note left, with the count checked (`2/7`). `PUT /api/projects/{id}/contract-checks` replaces it whole, each point at most
once; editing the mission leaves it as it is, and the account export carries it with the mission.

## Kanban And Pipeline View

The dashboard's Kanban mode shows the pipeline in the five stage columns and follows the selected season.

- Dragging a card changes its stage; into Refusé it asks who refused and why, then shows **Refusé par le recruteur** or
  **Refusé par moi**. Within a column, cards keep a manual order under the favorites (the star).
- A card shows its days since last activity and is highlighted once idle for the inactivity period (14 days by
  default, 1 to 365 in **Profile → Preferences**), which also drives the dashboard's stale opportunities.
- A column's quick add creates an opportunity from a role, client and rate. Cards sharing a client and role with
  another (case aside) are flagged as possible duplicates.

**Archive** on an opportunity's page takes it off the board and the reminders but keeps its stage for the statistics.
**Archive idle opportunities every night** (Preferences, off by default) archives at 03:30 Paris time every opportunity
the board highlights. The **Archive** page lists and searches them with the won and refused ones; **Restore** puts one
back in its column and restarts its inactivity period.

Checkboxes on the board's cards and the list's cards open a bar acting on up to 200 opportunities: **Move to**,
**Source**, **Add a tag**, **Archive** and **Delete** (with their notes, files and activity reports). The clients and
contacts lists delete picked rows the same way, keeping clients still linked to missions.

## Interview Steps

An opportunity's steps form a timeline: title, status (to plan, planned, cancelled, waiting for feedback, validated,
failed), local date and time and notes, the status changed from its pill.

- A step carries a **preparation checklist** of up to 30 items of 200 characters, with suggested items in the interface
  language, ticked in place on the timeline.
- **Templates** saves the steps and their checklists as a recruitment process for the ESN, the client or any client,
  and applies one to another opportunity (steps added after the existing ones, undated, unticked). Up to 50 templates
  with distinct names; deleting a client keeps its templates for any client.

## Project Journal

An opportunity's **Journal** lists, newest first, its Markdown notes (with a preview, optionally from a template such
as a call debrief) and the exchanges with contacts recorded about it. **Record an exchange** adds one with a contact of
the client or its ESN. Deleting the opportunity keeps its exchanges, tied to none.

## Emails From Templates

**Write an email** on an opportunity picks a template and a recipient among the ESN's and the client's contacts, then
fills a subject and message to edit; **Open in my mail app** opens a `mailto:` link, **Copy** copies them. Indezy sends
nothing itself. A follow-up, a rate proposal and an availability announcement are always offered; **Profile → Emails**
manages the workspace's own (up to 50). Variables are written `{{name}}`: `firstName`, `lastName`, `company`, `role`,
`rate`, `availability`, `myName`, `myPhone` and `privacyNotice`; one without a value stays as written and is pointed out.

## Calendar

**Calendar** shows **Upcoming** (planned interview steps and booked calls from now on, soonest first) or **Month** (the
month's dated steps by status, with booked calls, and the picked day's list below), the view kept in the address.

## Booking Link

The **Booking link** card under the calendar lets recruiters book a call on a public page, `/book/<16 letters and
digits>`, without an account.

- Settings: on or off, a title, a call's length (15 to 60 minutes), a gap around calls (0 to 30 minutes), the minimum notice (up
  to 48 hours), how far ahead (7 to 60 days) and one range of hours per weekday, Paris time. **New link** replaces the
  address and the old one stops working.
- Free times leave out French public holidays and anything that would overlap with the gap, a booked call or a planned
  interview step (taken as one hour). The page shows them in the visitor's time zone and asks a name, an email, and
  optionally a company and a message. The link takes 5 bookings an hour from one address and 20 a day at most.
- Each call is emailed to the freelancer (unless turned off), sent to webhooks, listed on the card to cancel (Indezy
  does not email the recruiter) and shown on the calendar.

## Job-Hunting Seasons

A season is a bounded job-hunting period with its own pipeline and dashboard, created from the dashboard's season
picker: a name, a start, an optional end (empty while it runs), an optional target daily rate (the profile's by
default) and an objective.

- Saving a season adopts the opportunities without a season created within its dates; a new opportunity joins the
  running season that started last unless another is picked.
- The picker scopes the dashboard and the board to one season or all, remembered in the browser; a season's dashboard
  opens with its dates, objective, success rate and average against target rate, compared with all seasons.
- Closing a season ends it the day before; deleting one keeps its opportunities. Contact reminders stay workspace-wide.

## Clients, ESNs, And Contacts

A **client** is a final client or an intermediary such as an ESN, with its address, domain, notes, a 1 to 5 rating, a
blacklist flag with its reason, its SIREN and VAT number for invoices, and its
[activity report template](#monthly-activity-reports-cra). A client still linked to missions cannot be deleted; deleting
one deletes its contacts. **Import contacts** on a client's page adds the contacts of pasted CSV (a LinkedIn export, for
example) or vCard content, skipping emails the workspace already has.

A **contact** belongs to one client and holds a name, email, phone, notes and the legal basis for keeping their
details. The list shows each contact's last exchange; emptying a field in the form clears it.

### Company Lookup

Typing three characters or more of a new client's name (or its SIREN) offers up to five companies of the French public
directory (`recherche-entreprises.api.gouv.fr`, free and keyless, asked through `GET /api/companies/search?q=`), each with
its SIREN, legal form, town and NAF code, a closed one marked **closed**. Picking one fills in the legal name, the SIREN,
the head office's address and town, and the intra-EU VAT number computed from the SIREN (`FR` + key + SIREN); what the
directory does not give stays as typed. Answers are kept a day; an unreachable directory offers nothing.

### Company Health Check

A client or ESN with a SIREN is checked on its page and on its opportunities' pages: closed according to the public
directory, or under collective proceedings (safeguard, receivership, liquidation) according to its latest BODACC
announcement of the last three years (the DILA's open data, keyless), the page then says so with the announcement's date
(`Acme is under insolvency proceedings (receivership). Announced in the BODACC on May 3, 2026.`). Only the SIREN is sent,
answers are kept a day, and a company without a SIREN or that neither source answers for is not flagged
(`GET /api/clients/{id}/health`).

### Contact Interaction History

A contact's **Exchanges** record a day (today at the latest), a channel (email, phone, LinkedIn, meeting, other), who
started it (**From me** / **From them**), whether the freelancer's message was answered, a summary of up to 500
characters and optionally the opportunity it was about; up to 500 a contact. The page reads from them the **Last
contact**, the **Response rate** (answered out of the freelancer's messages whose answer is known) and the **Preferred
channel**.

A recent exchange keeps a contact off the dashboard's contacts to reconnect with. A contact whose latest exchange is an
unanswered message from the freelancer appears in **Follow-ups due** after the follow-up delay (5 days by default, 1 to
60 in Preferences), until an answer or a newer exchange is recorded.

### Contact Network And Org Charts

**Reports to** on a contact's form names their manager, at any company, never the contact itself or someone it
manages. A contact's page shows its manager, team and whom it **Works with**: two-way links, often across a client and
its ESN, each with a note of up to 100 characters, up to 100 per contact. A deleted manager's team reports to nobody.
The client's **Contacts** tab draws the **Org chart**, with each person's links to other companies.

### Contact Privacy

The freelancer is the controller of their contacts' personal data; Indezy keeps it on their behalf. A contact's **Their
data** card shows the legal basis (legitimate interest by default, consent or contract), **Download their data** (a
JSON file of what the workspace holds about them, without other people's names) and **Erase on their request** (the
contact goes with their exchanges and links). Both requests are recorded in the account's activity by the contact's
number only. The `{{privacyNotice}}` email variable tells a recipient who keeps their details, why, and how to see,
correct or erase them.

**Profile → Preferences → Anonymize contacts without exchange for** sets a retention period: never (the default), 1, 2,
3 or 5 years. Every night at 03:45 Paris time, a contact with no exchange, change or related opportunity change for that
long loses their name, email, phone, notes, exchange summaries and link notes; the contact and its exchanges' dates and
channels stay, so counts and org charts keep their shape.

## Sources

A **source** says where opportunities come from: a name, a type (job board, social media, email, call, SMS), a link, a
listing flag, popularity and usefulness ratings and notes. Sources feed the filters and the dashboard's source ROI;
deleting one keeps its opportunities. Every workspace starts, once, with the job boards Indezy knows (read or not) and
LinkedIn; a board deleted since stays deleted, and an offer added to the pipeline uses its board's source.

## Freelance Offer Feed

**Offers** lists, newest first, the freelance missions of the job boards Indezy reads, each on its own schedule (see
[Architecture](./architecture.md#offer-collection) and [ADR 0009](./adr/0009-collecting-job-offers.md)). An offer is
kept only when its site marks it as freelance and its title names no CDI or CDD job ("CDI/Freelance" is kept).

| Site | What Indezy reads | Refreshed |
| --- | --- | --- |
| Free-Work | contractor offers of its public API, except Digistrat, Propulse IT and Omicrone (fake listings) | every hour |
| Collective.work | job pages whose contract types include freelance | every hour |
| Cherry Pick | its freelance missions, not its salaried jobs | every 3 hours |
| LeHibou | its 10 newest missions, pro bono ones left out | every 30 minutes |
| Yeets | missions still open | every 6 hours |
| WorkDispo | sitemap jobs with a contractor posting and a Freelance contract | every 2 hours |
| Next Mondays | sitemap jobs | every 12 hours |
| Mindquest | sitemap missions with a contractor posting | every 3 hours |
| Freelance Factory | missions of its WordPress API | every 6 hours |
| LittleBig Connection | open missions of its nine categories, dated when first seen | every 6 hours |
| Silkhom | freelance cards of its offers page | every 6 hours |
| LeGratin | its missions page | every 3 hours |
| Freelance-Informatique | its freelance offers, page after page | every 2 hours |
| Espace Freelance | its missions, page after page | every 12 hours |
| Beyond Skills IT | offers labelled Freelance | every 12 hours |
| Koïno | sitemap missions open to applications | every 12 hours |
| Sixième Homme | the missions its home page showcases (the only public ones) | every 12 hours |
| FreelanceRepublik | sitemap missions with a contractor posting | every 12 hours |
| EasyPartner | sitemap jobs with a contractor posting ("Prestation") | every 12 hours |
| Castor Network | Alten's freelance missions (engineering as well as IT) | every 12 hours |
| KatchMe | open freelance offers of its WordPress API | every 6 hours |
| OuiDesk | sitemap missions, dated when first seen (applying takes a referral) | every 12 hours |
| Kicklox | sitemap missions whose contract is "Prestation", without partners' salaried jobs or standing ads | every 3 hours |

Sites describing each offer on its own page are read a batch per run, so their backlog fills in over the first runs;
an offer set aside (salaried, older than 90 days, gone) is not read again for 30 days. The boards not read are listed
on the [Job boards](#site-health) page; the next ones are planned in
[TODO.md](../TODO.md#freelance-offer-feed-added-2026-10-19).

A card shows the site, title, publisher, daily rate, place with its distance from the profile's city, the trip there by
car and by public transport from off-peak to rush hour (`≈ 18 min – 26 min by car · ≈ 42 min – 48 min by transit`,
estimated from the straight line as described under [Commute-Time Sorting](#commute-time-sorting), none for a fully
remote mission), work mode (read from the text when the site gives none), duration, start, seniority, skills, an excerpt
and its age; unseen offers are marked **New**. The **distance** sort is also the commute order, the estimate growing
with the distance.

- **Filters** (kept in the address): keywords in the offer or the workspace's note, rate range, regions, departments,
  distance (10 to 200 km), work modes, remote days, duration, start date, seniority, skills, publication age, sites,
  unseen only, **Matches my profile** and **Hide suspicious offers**. Offers not stating a duration, start or seniority
  stay listed when filtering on them. **Sort** by newest, rate, distance or **Best match**. **Views**: all, saved,
  applied and hidden.
- **Add to pipeline** creates a Contact-stage opportunity from the offer (its highest rate, else the profile's target),
  with the publisher as client (created as an ESN when missing) and the board as source; an offer is added once.
- **I applied** records how (site form, email, phone) and when, adds the mission to the pipeline with an "Application
  sent" step and an optional follow-up step (after 7 days by default, 1 to 60); **Undo the application** forgets it.
- **Add a note** keeps up to 2,000 characters on an offer, found by the keyword filter and copied to its opportunity.
  Checkboxes save, hide, mark seen or add several offers at once.
- Copies of one mission on several boards (same title, and same publisher or opening text) show as one card with
  **Also on** links; acting on one copy acts on the mission.
- **Hide every offer from …**, or a name typed in **Profile → Offer feed**, blocks a publisher whatever its spelling, up
  to 200 per workspace.
- **Market** opens a snapshot of what the filters find: offer count, median and middle half of the rates, share
  reaching the profile's target with a rate histogram, work modes, top skills and publishers and daily publications
  over 30 days, refreshed once a minute at most, ending with [trends](#market-trends).

What a workspace does with offers (saved, hidden, seen, imported, notes, applications, blocks, excluded boards) is
private to it and in its data export. Offers older than 90 days or expired are removed every night unless the
workspace saved, applied to or imported them.

### Offer Page

**Open its page** (in a card's menu or on the dashboard) shows an offer at `/offers/<id>` with its full description,
copies, match and [history](#mission-history), the card's actions and **Copy the link**, to share with any Indezy
account. An offer no longer collected says why offers leave the feed.

### Saved Searches

**Save this search** keeps the filters and sort under a unique name, up to 20 per workspace. Each saved search shows the
offers found since it was last opened ("3 new", one per mission); the menu's **Offers** item counts them all.

### Saved Search Emails

The bell beside a saved search emails its new offers as they come (grouped every 30 minutes) or daily at a chosen hour,
Paris time, in the language the app was in: up to 20 offers each linking to its page in Indezy. There is no tracking
pixel; an offer counts as opened once opened in the app. After 60 days without an offer opened from them, the emails
stop and say so (turning them on again gives 60 more days). Each email has a **Stop these emails** link that works
without an account, and a one-click unsubscribe.

### Suggested Searches

**Suggested searches** proposes up to five searches drawn from the profile and the pipeline, at the target daily rate
or more (the pipeline's median rate without one): the job title within 30 km of the city; the first two skills alone
and the most asked pair among the first four, within 30 km; the first skill full remote, in the region, hybrid within
80 km, and starting within 30 days of the availability date; and the two skills of recent or won opportunities the
profile lacks. Searches finding nothing, already saved or dismissed are left out, those with the most good matches
first. **Preview** shows the offers, **Save** saves the search, and a dismissed search is never suggested again (200
dismissals kept). The list names the empty profile fields that would bring more. It opens by itself while the
workspace has no saved search.

### Profile Match

Once the profile names skills, every offer is scored out of 100:

| Part | Points | How |
| --- | --- | --- |
| Skills | up to 60 | 20 per profile skill the offer mentions, full marks from three. A skill counts when its words appear whole ("Java" is not in "JavaScript"); one shorter than three characters ("Go", "C#") only when listed among the offer's skills. Up to 20 profile skills. |
| Daily rate | 25 | 25 when the highest rate stated meets the target, 15 within 10 % below, 10 when no rate is stated. |
| Place | 15 | 15 when full remote or within 30 km (or within the profile's longest commute), 10 within 80 km (or one and a half times it), 5 farther with three remote days a week or more, or with no place stated. |
| Availability | 10 | 10 when it starts once the freelancer is available, 5 up to two weeks before or with no start stated. |

A profile without a target rate, city or availability date is scored on the other parts. The badge is green from 60 and
amber from 35, with a tooltip explaining it. **Matches my profile** keeps scores of 60 or more.

### Rate Versus Market

A card stating a rate shows how it compares with the median of the open offers naming the same first skill (one per
mission): in the offer's region with ten offers or more, else in France, else nothing ("+12% vs. Java in
Île-de-France"; green from 5 % above, amber from 5 % below). Medians are computed nightly at 04:45 Paris time.

### Market Trends

**Trends**, at the end of the **Market** panel, shows for a profile skill the median rate and open offers on the latest
night and their change over 30 days, 90 days and a year, in the one region the feed is narrowed to or in France. The
nightly medians are kept 400 days.

### Mission History

When a site republishes an offer with other details, Indezy records the change (rate, start, description). The card
shows the latest change and how many times the site published the mission under a new reference in 60 days ("Rate
raised from €500 to €550 · Published 3 times in 60 days"), a sign of a mission hard to fill; the offer's page lists up
to ten changes. Only boards read through a list or an API report changes.

### Publisher Page

A card's publisher name opens what that publisher puts in the feed: its open missions, their median rate and age, how
many of its last 90 days' missions it republished, its sites, its newest offers, the workspace's client of that name and
**Hide its offers**. A mission several intermediaries publish shows how many and the spread of their rates.

### Suspicious Offers

**Looks suspicious** marks, with the reason, an offer that:

| Rule | When |
| --- | --- |
| No publisher, no description | names no publisher and has no description |
| Rate far from the market | states a rate below 40 % or above 250 % of its first skill's median |
| Same text, many publishers | shares its title and opening text with four publishers or more |
| Known for fake listings | comes from Digistrat, Propulse IT or Omicrone |

The rules are applied to the open offers every hour.

### Site Health

**Job boards** (`/offers/sites`), linked from the feed, lists every board read, failing ones first, with its status,
last and next reading, recent and open offers, and its last error, flagging a board silent for a month or a reading that
found nothing.

- **Show in my feed** switched off keeps a board out of the workspace's feed, saved searches and snapshot.
- **Read now** reads a board at once, at most once every 15 minutes whoever asks.
- **Sites to visit yourself** lists sites Indezy does not read where missions can still be found (HiTechPros,
  Opteamis, LookingForTechs, Capgemini TalentNet, HelloWork) and matching services (Malt, Freelance.com, WheelOfWork,
  Comet, Amazing.dev, Crème de la Crème), with what each takes; **Mark as visited** records the last visit. The boards
  left out for other reasons (LinkedIn, Mon Consultant Indépendant, Nexoris, Welcome to the Jungle, recruitment firms
  with salaried jobs only) come last.

### From An Offer Link

**From an offer link**, beside the Kanban board's title and on the opportunities list, opens the new opportunity form on
its **Offer link** field. **Fill in** looks a pasted link up:

- an offer of the feed fills the form as **Add to pipeline** would, its new client or source created only on save;
  **Unlink** keeps the filled form as an opportunity of its own;
- an offer already in the pipeline (or a copy) links to its opportunity;
- an offer not collected yet is read at once, or its board is read on request;
- a page on another site is read for its schema.org job posting, or its title and summary, without joining the feed;
- salaried, expired or unreachable offers are refused with the reason.

Each workspace reads at most 20 links an hour.

### Browser Extension

The **Indezy: add this offer** extension for Chrome and Chromium browsers (`indezy-web/extension`) opens the new
opportunity form with the current page, or a right-clicked link (**Add this link to Indezy**), in the **Offer link**
field. It uses the browser's Indezy session, holds no password or token and reads nothing on the pages visited; its
options set the Indezy address. Publishing it in the Chrome Web Store waits on a decision (TODO.md, Needs Input).

## Monthly Activity Reports (CRA)

An accepted mission's page has an **Activity reports** card for the monthly compte rendu d'activité, and **Timesheets**
(`/activity-reports`) lists the missions in progress with theirs.

- Pick full or half days on site, remote or absent and tap the days; **Fill business days** fills the empty business
  days by the work mode. Weekends and French public holidays are shaded. Totals (days, on site, remote, absences,
  amount before VAT) update as you type.
- **Send for approval** locks the month and shows a validation link once, valid 30 days. The client or ESN opens it
  without an account, signs with their name and approves, or rejects with a reason; a month is answered once.
  **Generate a new link** replaces a lost one; **Reopen to edit** returns the month to draft and stops its link.
- **Download PDF** follows the template of the mission's ESN, else its client (title, portrait day list or landscape
  month grid, language, on-site / remote split, footer), and prints who answered and when, a simple electronic
  signature.
- A month with days worked, unless rejected, can be [invoiced](#invoices).

**End the mission** records the day it ended and why; its reports, the CRA page, the dashboard, the yearly review and
the bench time then use that day as its last. **Resume** undoes it.

## Purchase Orders

A won mission's **Purchase orders** follow the client's orders and their amendments against its activity reports. An
order has a number, a start, an optional end and a ceiling in days, in euros before VAT, or both; **Add an amendment**
adds days, euros or a later end. Usage counts the days worked in the reports between start and end, at the daily rate.
From 80 % of a ceiling the order asks for an amendment, over 100 % it says it no longer covers the work, and the
dashboard lists running orders at 80 % or more.

## Invoices

**Invoices** lists the workspace's invoices with their status (draft, awaiting payment, overdue, paid, credited) and the
totals awaiting payment and overdue. **Revenue by client, last 12 months** shares the invoices issued in the last year
(excluding VAT, less their credit notes) between clients, largest first, and warns when one client paid half or more:
working mostly for one client weakens the independence URSSAF and the courts weigh before reclassifying a freelance
contract as employment. Each client's page, and the page of each opportunity it pays for (its ESN, else its client),
says how it really pays, from its paid invoices: "Pays in 52 days on average, 12 days late (4 paid invoices)"
(`GET /api/invoices/payment-delays`). A draft is made:

- **From an activity report**: a month with days worked, billed to the mission's ESN (else its client) in that client's
  report language, with the days at the daily rate. A rejected month cannot be invoiced, and a month only once.
- **By hand** (**New invoice**): a client, optionally a mission, a period, the language and up to 50 lines.

A draft changes freely and its PDF says it is not an invoice. **Issuing** needs a complete
[billing identity](#billing-identity) and the client's address, takes a date (never later than today, never before the
last invoice), gives the year's next number with no gap (`<prefix><year>-0001`), sets the due date and freezes the
seller and buyer. An issued invoice can only be marked paid or unpaid, survives its mission or report (French law keeps
invoices ten years), and reads as overdue past its due date on the dashboard and in the digest. The PDF carries the
mentions French law asks, the VAT or its exemption, and the late payment penalties.

**Create a credit note** on an issued invoice drafts one with the same lines, to reduce for a partial credit; it is
issued in the same numbering and cannot credit more than what is left. Fully credited, the invoice reads **Credited**.

### Billing Identity

**Profile → Billing** holds the seller's legal name and form, SIRET, VAT number and rate (20 % by default) or VAT
exemption (franchise en base, the default), a company's capital and registration, the billing address, IBAN and BIC,
the payment term (30 days by default, 60 at most) and the invoice prefix (`F` by default). An IBAN with wrong check
digits, a SIRET without 14 digits or a malformed VAT number or BIC is refused. Invoices can be issued once a SIRET, an
address, an IBAN and, when VAT applies, a VAT number are set. Under portage the portage company invoices, so Indezy
does not.

### Factur-X

Issued invoices and credit notes download as **Factur-X**: a PDF/A-3B carrying the same data as an attached
`factur-x.xml` (EN 16931 profile). Drafts stay plain PDFs. Sending through an approved e-invoicing platform is planned.

### Accounting Export

**Export for the accountant** on the Invoices page exports a year of issued invoices and credit notes as **Journal
entries (FEC)**, the file French accounting tools import (sales journal `VE` with clients on `411000`, services on
`706000` and VAT on `445710`; payments in the bank journal `BQ` on `512000`), or as an **Invoice register**
spreadsheet in the interface language.

## Compliance Documents

**Documents** keeps the papers ESNs ask for and shares them through one link.

- **Add a document**: URSSAF vigilance certificate, company registration extract (Kbis), professional insurance, bank
  details (RIB), identity or other (with a label), with its dates, as a PDF, PNG or JPEG of 10 MB at most (the type read
  from the content). Without an expiry, an URSSAF certificate expires six months after issue and a registration
  extract three. Up to 30 documents.
- Documents expired or expiring within 30 days show on the dashboard (**Documents to renew**).
- **Share** picks a recipient, documents and a duration (7, 14 or 30 days); up to 20 links can be open. The link is
  shown once, its openings are counted, and **Revoke** closes it. The shared page needs no account.

## Dashboard And Analytics

The dashboard is scoped by the [season](#job-hunting-seasons) picker and switches to the [Kanban](#kanban-and-pipeline-view)
mode. **Customize** shows, hides and orders its six blocks, saved with the account; **Usual layout** resets them.

- **Key figures**: opportunities, average rate, estimated revenue of accepted missions (218 working days a year when a
  mission states none, up to the day it ended), the part still to come, active opportunities, and the target rate
  against the accepted missions' average.
- **Charts**: opportunities by stage and work mode, and the negotiated rates.
- **Reminders**: interviews this week, overdue invoices, follow-ups due, stale opportunities, missions ending within six
  weeks, order renewals within the notice period (30 days by default), purchase orders near their ceiling, documents to
  renew, contacts without activity for six months, and "on this day" anniversaries.
- **New offers**: the five unseen offers matching the profile best (the five newest without skills).
- **Analytics**: [trends](#trends), outcomes and refusal reasons, source ROI, conversion funnels (overall and by source,
  client type and ESN), asked against obtained rates by year, bench time with its cost, time to signature per client or
  ESN, skills in demand and the year's prospection activity.
- **Recent opportunities**.

### Trends

**Last 90 days** compares opportunities added, interviews held, missions won and their average rate with the 90 days
before, in percent with the earlier value, for the selected season.

## Yearly Review

**Year in review** (`/review`) sums up a calendar year back to 2000: revenue, days worked, average rate and missions
against the year before; each mission of the year with its days (from its activity reports, else estimated and marked
with an asterisk); opportunities added, won and refused with the reasons; the five skills asked most; and the network
added. **Download PDF** exports it on one A4 page.

## Reversion Rate Calculator

The taux de réversion is the share of the gross daily rate the freelancer keeps. The opportunities list sets it, or
derives it from the income tax (100 % less 40 % of social charges and the tax; 55 % with 5 % by default), saves both
on the profile, and shows the net daily, monthly and yearly revenue and the workable days net of French public holidays.

## Commute-Time Sorting

**Sort by → Commute time** on the opportunities list orders missions by their monthly commute, by car or public
transport: one trip from the profile's address to the client's, at rush hour, there and back, times the days on site
(none when remote, listed first; else the mission's days, else 20 less its remote days, else 20 on site or 10 hybrid). A
mission without a time says why.

Without a Google Maps key (production's choice: free), both addresses are located through the national address search
(IGN's Géoplateforme, each address searched once and kept) and the trip is estimated from the straight line: roads 1.3
times longer; a car at 30 km/h for the first 5 km, 70 up to 20 km and 100 beyond, plus 5 minutes; public transport at 18
km/h for the first 10 km, 45 up to 40 km, 70 up to 150 km and 200 beyond (high-speed rail), plus 15 minutes (each speed
covers only its own kilometres, so a longer trip never takes less time). At rush hour (a weekday at 8:30) a car's first
10 km take 1.2 times as long and the rest 2.7 times on a trip touching Île-de-France (Issou to Montrouge: about 50
minutes, 1 h 45 at rush hour), 1.4 times elsewhere; public transport 1.15 times. The list shows `≈ 50 min (≈ 56 km) · ≈
1 h 45 at rush hour` marked **estimation**. With a key (set up as [Development](./development.md) describes), Google's
Distance Matrix gives the time and its time in traffic for that departure, answers kept a week.

The opportunity page shows a **Commute** card (none for a fully remote mission): the trip one way by car and by public
transport with its distance, off-peak and at rush hour, the month's time there and back, and what each remote day a week
gives back on the quickest way (`Each remote day a week gives you back 13.3 h a month.`), from `GET
/api/commute/project/{id}`. Each Kanban card carries a chip with its drive at rush hour (`≈ 1 h 45`), read from the same
sorted list.

## Filtering And Search Surfaces

Every list filters as you type: opportunities, clients (words, type, city), contacts (words, client), sources (words,
type, usefulness) and the archive. The global search is under [Keyboard Shortcuts](#keyboard-shortcuts).

## Keyboard Shortcuts

`Alt+Shift+H` (or the account menu) shows the shortcuts: `Alt+Shift+N` new opportunity, `Alt+Shift+F` opportunities
search, `Alt+Shift+M` stage menu of the current opportunity or focused card, and `Alt+Shift+S` (or the top bar's
button) the **global search**: words of two characters or more found in one field of an opportunity, client, contact or
journal note, accents and case aside, up to 8 hits of each kind, highlighted. In the offer feed, `J` / `K` move between
offers, `O` opens, `S` saves, `H` hides, `A` adds to the pipeline, `D` shows details, `N` opens the note and `X` selects.
Shortcuts never run in editable fields, menus or dialogs. Every page works from the keyboard alone.

## Offline Use

In production the app installs as a progressive web app and shows, without a connection, what it last loaded of the
main workspace pages, kept seven days; online, a page falls back to it when the server takes over six seconds. Changes
need a connection. Downloads, sessions, tokens, exports, the admin console and public pages are never kept, and signing
out forgets everything.

## Admin Console And Feature Flags

**Admin** (`/admin`) opens for the accounts the deployment names (see [Deployment](./deployment.md)).

- **Overview**: accounts, sign-ups of the last 30 days, disabled accounts and each feature's use.
- **Accounts**: search by email or name; **Disable** signs an account out and refuses its sign-ins until **Enable**,
  keeping its data. An administrator cannot be disabled.
- **Features**: turns the **Offer feed**, **Public profiles**, **Data import** or **Yearly review** off or on for every
  account without a redeploy, within 30 seconds; a feature off leaves the menu and its API refuses requests.

## Internationalization

The interface is in French and English, chosen in **Profile → Preferences** and saved with the account.

## Known Gaps

Not implemented, even where a field or a dependency hints at it:

- password reset and email verification for local accounts, two-factor recovery codes, self-service account deletion
  and social login;
- push notifications and a mobile app (TODO.md, Needs Input);
- mailbox (Gmail, Outlook), LinkedIn and calendar sync;
- AI parsing, ranking or recommendations;
- sending invoices through an e-invoicing platform, and pushing entries into an accounting tool;
- qualified electronic signature of activity reports.

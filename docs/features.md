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

The menu lists **Dashboard**, **Calendar**, **Offers**, **Opportunities**, **Network**, **Clients**, **Contacts**,
**Sources**, **Year in review**, **Timesheets** (CRA), **Invoices**, **Quotes**, **Documents** and **Archive**, then
**Admin** for administrators. The toolbar's menu button folds it to its icons, each name shown on hover, and unfolds it
again; the browser remembers the choice. Below 1,024 pixels wide the menu is a drawer the button opens and closes.

## Presentation Page

`/` presents Indezy in French and `/en` in English (a first visit from an English browser goes to `/en`): what it does,
how it works, how the data is kept, and screens from a demo workspace whose people and offers are invented. Signed out,
**Sign in** opens Keycloak; **Create my account** shows outside production only, as sign-up in production waits on a
decision (TODO.md, Needs Input). Signed in, **Open the dashboard**. The page never sends a visitor to sign in, and an old
`/?project=<id>` link still opens its opportunity. Both languages are prerendered, so search engines and link previews
read them without JavaScript.

The screens come from `scripts/screenshots` (`npx playwright test -c pw.screenshots.config.ts`) and the card link
previews show (`src/assets/brand/og-image.png`, 1200 × 630) from `scripts/og-image` (`node scripts/og-image/render.mjs`):
both are retaken after a theme change. They keep their names, so nginx has browsers check them again on each visit
instead of keeping them a year as it does the build's hashed scripts, styles and fonts.

## Help Center And Legal Pages

The **?** button in the top bar opens the help of the page in use in a panel at the side, its article chosen from the
address (the opportunities, Kanban and timeline: the pipeline; invoices, quotes and expenses: quotes and invoices; the
profile: the account; any other page: getting started), with a search over all the help (as the help center's, its
results opening in the panel), **Open the full article**, **All the help**, **Report a problem** and **Suggest a feature**
(the support form with that kind and the page it came from) and **My requests**. The panel loads on first use, out of
the first page's download. A small **?** beside the settings that need explaining opens it at the section that explains
them: the billing identity and Factur-X (Profile → Billing), the email templates and their variables, and the offer
feed's filters; a unit test checks that each points at a heading both languages' article has.

### Support Requests

**Support** (`/support`, from the help panel or `?kind=` and `?error=` to prefill one) sends the Indezy team a request
(`POST /api/support/requests`): a problem, a feature request, a question or the account and billing, a subject (150
characters) and a description (5,000), and the technical context shown before sending and sent unless unticked (the
page it came from, the app's version, the browser, the screen size, the language and the error shown if any). Ten go a day at most.
Up to three screenshots or PDFs of 5 MB go with it (`POST /api/support/requests/{id}/attachments`, within an hour of
sending, the request still open): images drawn anew as the network's are (an opaque screenshot becomes a JPEG, what else
it carried dropped), PDFs checked by the platform's ClamAV, anything else refused; its author and the administrators
download them (`GET /api/support/requests/{id}/attachments/{attachmentId}`, served sandboxed and not cached), and they go
with the request.
**My requests** lists them, the newest first, with their status and answer (`GET /api/support/requests`). **Admin →
Support** lists every request, open ones first, with its author's address, and answers it, closing it or not
(`GET`/`PUT /api/admin/support/requests`); the member is emailed that an answer waits in the app, the answer itself not
in the email. Requests go with their account and are part of the data export. An error message in the app offers
**Report** (and stays 6 seconds), which opens Support on a problem with that error and the page it came from; pages
open without an account keep **Close**.

**Help** in the left menu (`/help`, open with or without an account) explains how Indezy works, area by area, for
freelancers rather than developers: getting started, the account and its data, keyboard shortcuts, the FAQ, the offer
feed, the pipeline and opportunities, clients and contacts, the calendar, timesheets and purchase orders, quotes and
invoices, documents, the dashboard, and the member network. Each article is a Markdown file per language
(`src/assets/help/<fr|en>/<slug>.md`, listed in `components/help/help-articles.ts`) with numbered steps naming the app's
own labels and screenshots of a demo workspace (`src/assets/help/screens`, retaken with
`npx playwright test -c pw.screenshots.config.ts help`; invented people, clients and offers). The help center lists the
articles by section as cards and searches their titles, summaries and text (the articles read once in the browser), each
result quoting the words found; an article page lists the articles on the left, its own sections on the right on a wide
screen, and the previous and next article at its foot. `e2e/help.spec.ts` checks that every article exists in both
languages, with its screenshots and the articles it links to.

The legal texts (`/legal/notice`, `/legal/terms`, `/legal/privacy`: legal notice, terms of use, privacy policy) are
Markdown files too (`src/assets/legal/<fr|en>/`), the French version prevailing, open without an account and linked from
the presentation page's footer and the help center. What the publisher still has to give (legal form, address,
registration) is marked **[à compléter]** in them.

## Privacy Settings

Profile → **Privacy** (`/profile?tab=privacy`) gathers every opt-in and opt-out in one place. Its switches save at once:
on the member network, the suggestions by contacts (on by default: a member is suggested to the members whose workspace
contacts hold their sign-in address, and is suggested theirs; off on either side, no suggestion by contacts between the
two, `PUT /api/network/membership/contact-suggestions?on=`), the suggestions by the same ESNs, the private card,
browsing privately, message requests, read receipts, the typing indicator and online presence; the weekly digest, the
emails of unread messages and of booked calls. The settings set inside a larger form (what the card shows, each network
notification's channel, the digest's sections, saved search emails, automatic archiving, contact anonymization, the
public profile, the data export and the account's deletion) each have a **Change** button opening their tab. Outside the
network, its settings say they do not apply; while the network is off, they are left out.

## Authentication And Account Access

Local accounts register and sign in with an email and a password of 8 characters to 72 bytes (an accented letter counts
as 2, an emoji as 4), and can turn on TOTP two-factor authentication. In production, sign-in goes through Keycloak. The
profile page has the tabs **Personal information**, **Preferences**, **Notifications**, **Security**, **Billing**,
**Emails**, **Public profile**, **Offer feed** and **Account**, where **Export my data** downloads every piece of the
account's data as JSON.

### Account Deletion

**Profile → Account → Delete my account** asks for the account's email, typed to confirm, then puts the account on
hold at once: every session ends, nobody can sign in, and its member card, posts and public page no longer show (its
conversations read as with a former member, closed to new messages). An email says the day it goes. Signing in within
30 days, with the password or through Keycloak, says the account is on hold and offers **Restore my account**, which
brings it back as it was. After 30 days, every night at 04:20 Paris time, everything the account held is deleted for
good: its workspace (opportunities, clients, contacts, documents, invoices, quotes, activity reports and settings),
its network presence (card, posts with their comments and reactions, its comments, reactions, follows and files) and
the account. The messages it sent stay with the members it wrote to, from a **Former member**. An administrator's
disabling of an account is separate: a disabled account is never purged.

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
each section kept or left out), an email for each call booked through the [booking link](#booking-link) (on), an email
for the [member network](#member-network)'s messages unread for an hour (on, while the network is on), and the count of
the saved searches' new offers beside **Offers** in the menu (on). Interview, follow-up and quiet-opportunity
reminders always show on the dashboard; saved searches set their own [emails](#saved-search-emails). The tab says when
the server cannot send email.

### Weekly Digest

Every Monday from 8:00 Paris time, in the interface language: open opportunities by stage, then up to five overdue
invoices, interviews of the next 7 days, follow-ups due, quiet opportunities, the week's goal against the bench (with
the missions ending, as on the [dashboard](#dashboard-and-analytics)) and the missions ending, the five offers
of the week matching the profile best and, while the member network is on, the week's most liked post of each tag the
member follows (up to five, by other members neither blocking the other, linking to the tag's page), then the network's
week: the posts of the members followed that drew the most reactions (up to five, each linking to its post), the
missions passed on that match the member's card (a skill it asks that the card shows, and its city unless fully remote),
and the new followers (how many, three named) and how many members viewed the member's page, linking to it. Each of these
network sections can be left out in Profile → Notifications like the others, and none is written for an account outside
the network. Empty sections are skipped and an empty week sends nothing. Each account gets
one digest a week whatever the number of servers; the email has a one-click unsubscribe and no tracking.

### Data Import

**Profile → Account → Import data** previews an Indezy export (up to 50 MB) section by section: sources, clients and
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
title, up to 30 skills, address; **Start from LinkedIn or my CV** opens the [profile import](#profile-import), what it takes
filling these fields and the rest, such as the phone, company, links, languages and skill years, saved with them), rate and availability (saved to the profile), a starter recruitment process
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

### Dossier de Compétences

**Dossier de compétences** on the Profile page downloads the profile as an A4 PDF, in French or English, named or
anonymized: the name and title, the city and (named only) the email, phone, LinkedIn and website; the presentation as a
summary; the skills with their level and years; the languages; the training record's valid certifications; then the
won missions, the latest first, with their period ("depuis mars 2026" while one runs), their client (in the anonymized
dossier "Client communiqué sur demande", and initials instead of the name), the description cut at 600 characters and
the tech stack; then the [past positions](#experience-and-education) under "Expériences antérieures" (the experience
itself when no mission was won), their employer hidden the same way when anonymized, and the schools under "Formation".
Nothing in it is written by AI (`GET /api/users/dossier?layout=NAMED|ANONYMOUS&language=FR|EN`); the **Tailored dossier
(AI)** on an opportunity's page is the AI version (see [AI Features](#ai-features)).

### Experience And Education

The personal information tab of **Profile** lists, under the form, the positions held before or outside Indezy and the
schools, each added, changed or deleted in a dialog (`/api/users/experiences` and `/api/users/educations`, 100 of each
at most, each workspace's own): a position has a title and a client or employer, a start and an end month (none while
it runs, and never before the start), what was done (2,000 characters) and the technical environment; a school has its
name, a degree and the years it started and ended. The running and latest positions come first, then the latest schools.
The [dossier de compétences](#dossier-de-competences) prints them all after the won missions, the pipeline staying what
was tracked in Indezy (no revenue, season or dashboard figure counts them); the public profile shows those marked **Show
on my public profile**. The account export carries them as `experiences` and `educations`, and deleting the account
removes them.

### Profile Import

**Import my profile** on the personal information fills the form from a CV read by AI (see [AI Features](#ai-features)),
from LinkedIn's profile PDF or from its data archive, both read without AI. The PDF of **Save to PDF** (same endpoint,
told apart by its first bytes) has a fixed layout, read with PDFBox by where each line starts and how large it is: the
left column's Contact (phone, the LinkedIn address joined when it runs onto a second line, the other links by the kind
written after them), Top Skills, Languages (proficiency dropped) and Certifications (names only), then in the main column
the name (the largest text), the headline and the place above Summary, Experience (each position found by its dates line,
`January 2020 - Present (4 years 11 months)` or in French, its title above, a new company named in larger letters above
that) and Education (not read yet); page footers are skipped, and a PDF with none of these headings is refused. The
archive (`POST /api/users/profile-import/linkedin`, the ZIP of **Get a copy of your data**, 20 MB at most, nothing
kept): `Profile.csv` gives the name, the place's town, the summary (else the
headline) and the websites (GitHub apart), `PhoneNumbers.csv` the phone, `Positions.csv` the running position (else the
latest) as title and company, `Skills.csv` the skills, each with the years of the dated positions whose title or
description names it (overlaps counted once), and `Languages.csv` the languages under the profile's names (English
becomes Anglais), and `Certifications.csv` the certifications (name, authority, started and finished on as obtained and
expiry). Columns are found by their names and notes above a header skipped; the archive's other files are not
read, and an archive of over 2,000 files, a file of over 5 MB in it or a file that is not one is refused. All three end on
the same review: each field found beside the profile's value, ticked when the profile's is empty, new skills and languages
added under the skill names' usual spelling (with the level a CV states, read by AI), and nothing saved until the profile
is. Skills the profile already names
(spellings and the known aliases folded together, as in the skills editor) are listed as there, languages the profile
does not offer as left out, and when more new skills are found than the 50-skill limit leaves room for, the freelancer
picks which, the first ones preselected. Certifications (also read from a CV by AI, 30 at most, dates as
`YYYY-MM-DD`) are listed apart: those the [training record](#training-record) already names as a certification or a
title (case aside) are marked as there, the others ticked and created as trainings (title and certification the name,
provider the issuer, obtained and expiry dates) when the form is filled. Past positions and schools go the same way into
[Experience And Education](#experience-and-education): every position `Positions.csv`, the PDF's Experience or the CV
(AI, 30 at most) lists, with its months and description, and every school of `Education.csv`, the PDF's Education (the
school in larger letters, its degree and years on the next line) or the CV (10 at most); one the profile holds already
(the same employer and title, or the same school with no other degree, case aside) is marked as there. They are added
off the public profile, and the profile's lists show them at once.

Without AI (none set up, the allowance spent, the provider down), the CV goes to `POST /api/users/profile-import/cv`,
read by `CvTextReader` from its text: the phone, links and town (after a postal code, or before ", France") by their
patterns; the name and title from the first lines; the sections by their usual headings in French and English
("Profil", "Compétences", "Expérience professionnelle", "Formation", "Langues", "Certifications"); each position by its
date range ("janv. 2024 – aujourd'hui", "03/2019 à 06/2021", "depuis mars 2022"), its title and employer on that line or
the one above, split at a dash, a comma, "chez" or "@"; each school by its years; and the skills of the skills section
then the known skill names found elsewhere, with the years of the positions naming them. The dialog says the CV was read
by its headings; a CV in columns or tables reads better with AI.

**Keep this CV**, offered unticked on the review of a CV (never of LinkedIn's files), keeps the file read as the
profile's CV, also kept from **My CV** under the personal information (`POST /api/users/cv`, a PDF, `.docx`, `.doc` or `.odt`
of 10 MB at most, checked by the platform's ClamAV like the network's documents): one per workspace, a new one replacing
it, downloaded with its name (`GET /api/users/cv`, `GET /api/users/cv/info` answering 204 without one) or removed. The
account export names it (not its content), and deleting the account removes it.

### Public Profile

**Profile → Public profile** publishes, once turned on, a page at `/p/<address>` that anyone with the link can read:
name, title, city, presentation, skills with levels, languages, links, photo and availability, and the past positions and
schools chosen for it. The email, phone,
address and workspace records stay private. The address has 3 to 40 lower-case letters, digits and single dashes, one
per account. A hidden profile and an unknown address both answer "No profile here".

### Testimonials

**Profile → Public profile → Testimonials** asks a client contact, or an **External person (outside your contacts)**, for
a short recommendation. An external person is asked by their first name, last name and email; Indezy emails them their
own link, as it does a contact with an email (in the freelancer's language: who asks, what for, until when the link
opens, and that it can be ignored), at most 20 emailed a day. The link is also shown once, to copy or send by hand, and
the page says whether the email went. Its author writes it without an account (20 to 1,500 characters, with their name
and optionally their title), agreeing to be named with it; the link stays open 30 days and is answered once. Once
written, the page offers to create an Indezy account, never needed (while sign-up is open). A received testimonial waits **To review** until **Show on my public page** puts it on `/p/<address>`, under
**Testimonials**; **Hide** takes it off and deleting it closes its link. An approved testimonial also shows to members on the freelancer's
network page, under **Recommendations**. Testimonials are part of the data export.

### Member Network

The member network is on for everyone (**Network** in the menu), like every feature, unless an administrator turns it
off (**Admin → Features → Member network**). **Profile → Network** joins it, a choice each account makes: a handle unique among members
(`@camille_d`, 3 to 30 lower-case letters, digits or underscores, starting with a letter; offered from the first name,
said taken while typing, and never one that would pass for Indezy, such as `@admin` or `@support`), and a **member
card** built from the profile: the name, then the photo, headline, skills, city, availability and languages, each shown
or hidden, and whether the member works remotely. The card's availability is, by default, taken from the won missions
("On a mission until March 2027" while one runs, through its last day, with no end said for an open-ended one), else
the profile's availability date ("Available from 2 December 2026", or now); or it is set by hand: available (from a
day or now), on a mission (until a day), or **Open to offers**. A coloured ring around the photo tells it at a glance:
green available, orange on a mission, indigo open to offers. A member available now (from no later day) wears the
**Open to work** badge (**DISPO**) on their photo, framed in green, wherever members show (posts, Members, their page,
the search, the messenger corner); **I am open to work**, on one's own page, says so in one click, and again goes back to
the availability the won missions tell (`PUT /api/network/membership/open-to-work?on=`). **Open to work now**, in
Members and the search, finds them alone (`now=true`). Only members see members' cards
(`GET /api/network/members/<handle>`).

**Verify with my SIRET** shows members they talk to an independent and not to a recruiter posing as one: the public
company registry (`recherche-entreprises.api.gouv.fr`, from INSEE Sirene; only the SIRET is sent, and the answer is read
afresh) must have the billing SIRET as an active establishment whose legal unit names the member, first and last name,
accents and case aside (the entrepreneur of an EI, an executive of a company). The card then bears a **verified** badge,
which lapses once the billing identity holds another SIRET. Without a SIRET, an establishment closed or unknown, or
another person named, the tab says so; when the registry cannot be reached, it asks to try later.
**Leave the network** hides the card at once and keeps the handle the account's for its return. The membership is part
of the data export.

A member's page, `/m/<handle>` (**See my page** in **Profile → Network**, or the card beside the feed, for
one's own), opens on a cover band in the member's color under their avatar, their name, the card, the skills on their
card the viewer's card has too and **Follows you** when they do, with **Follow** / **Unfollow** and a menu (mute, block,
report), or **Edit my card** on one's own page. Tabs show their posts, the members following them and the members they
follow (each opening their page); a member's own page adds **Saved** and **Drafts**, theirs alone. The tab is kept in the
address (`?tab=followers`, `following`, `saved` or `drafts`), so the card beside the feed opens each directly, and the
former addresses `/network/saved` and `/network/drafts` lead to them. Under another member's name, **Followed by Léo and
2 other members you follow** names the members the reader follows who follow them too, with their faces, the latest
followers first (`GET /api/network/members/<handle>/mutual`; never a member blocked either way), opening their followers.
**Pin to my page**, in the menu of one's own post, pins it at the top of one's page, marked **Pinned** (one at a time:
pinning another replaces it; deleting it unpins it; `PUT`/`DELETE /api/network/posts/<id>/pin`). **About**, under the
card, shows what the member wrote about themselves (1,000 characters) and, when they choose, the missions they won (role,
stack, from when to when; never the client), the latest first, ten at most; both are set in **Profile → Network**
(`PUT /api/network/membership/about`) and read like the member's posts (a private card's by its followers only,
`GET /api/network/members/<handle>/about`); one's own page invites one to write it. Beside another member's page, **About Léo** says
how many follow them and whom they follow, whether they follow the reader and how many skills both cards show. On their
own page a member **removes** a follower, without them being told.

**Recommendations**, a tab of the page once there is one (always on one's own), shows the testimonials the member
approved for their public page (see [Testimonials](#testimonials)), each as a quote with its author's name and title, the
latest first (`GET /api/network/members/<handle>/recommendations`; a private card's to its followers only); on one's own
page, **Ask for a recommendation** opens **Profile → Public profile** (`/profile?tab=public`). **Viewers**, a tab of one's
own page (and **n views of your page** on the card beside the feed), tells **who viewed my page**: how many members opened
it in the last 90 days and how many times in the last 7, the members named, the latest first (50 at most, never one
blocked either way or away from the network), and how many browsed privately. A member's page is counted once a day per
viewer, as it opens (`POST /api/network/members/<handle>/views`, never for one's own), and kept 90 days, dropped every
night after. **Browse privately**, in that tab (`PUT /api/network/page-views/private?on=`), opens pages without being
named, counted among the private viewers; as on LinkedIn, the member then sees no names among their own viewers either.

**Analytics**, under each of one's own posts, shows how many times it was shown (in a feed, a page or the search: a post
counts once half of it is in view, once per page read, reported in the background every few seconds,
`POST /api/network/posts/impressions`), to how many members, and its reactions, comments, reposts and saves
(`GET /api/network/posts/<id>/stats`, its author alone). Beside one's own page, **Card strength** measures what members
search the card shows (a photo, a headline, three skills, a city, an availability, an About section) as a gauge and a
level, each missing item one click from where it is set (the profile for what it holds, **Profile → Network** for what the
card hides or adds), and **Your activity** counts the posts of the last 7 days and the members they reached, and over 30
days the followers gained and the missions passed on and taken by members (`GET /api/network/activity`).

A member who left the network leaves every count and list while away, and a visitor who has not joined is asked
to join first.

From a member's page, **Mute** hides their posts while the follow stays, and **Block** (once confirmed) ends both
follows, keeps either from following the other, takes each out of the lists the other reads, and hides the blocker's
page from the blocked member, as if no member held the handle; the blocked member is not told. **Profile → Network**
lists the members muted and blocked, each to unmute or unblock.

Every page of the network takes the full width of the page, as the workspace's other pages do, following the screen, so
that nothing moves from one tab to the next, and lays itself out for it: lists of posts (a tag's, the saved ones on a wide
page) as many columns of cards as the width holds, a notification reading like an inbox row (when it happened on the
right, what happened on one line and the start of the post or comment it is about on another, 120 characters at most,
each cut with an ellipsis, so that every notification has the same height), a
single post in a readable column with its author's card beside it, and the members' search bar kept to a comfortable
length. The network's bar centers its search and its sections above the columns (**Back** before them on a page reached
from another), the search giving way first when room runs short. The feed, a member's page, the notifications, Messages, Members and the search
results share the same three columns from 1,440
pixels, as on LinkedIn, so that their middle column stays in place from one to the other: on the left the reader's own
page in short (their cover, avatar, name and headline, how many follow them and whom they follow, and **My posts**,
**Saved** and **Drafts**, each one click away, and on the notifications their filters under it), in the middle the page's content at a reading width (720 pixels at most,
the text never stretching out), and on the right, wider (420 pixels at most), the cards to go further, the room left going
between the columns. Beside the feed the right column holds the tags followed, the members to follow and the trending
tags; on the other pages, four members to follow (each with its reason and **Follow**, never the member whose page it is,
and **See all members**) and the five tags trending, each card left out while empty or when it cannot be read. From
1,100 pixels the left column goes (the reader's page then heads the feed's right column); below, the page is one column,
the right column under the content, and beside the feed the reader's page a one-line strip above the posts (their name,
then **My posts**, **Saved** and **Drafts** as chips), the members to follow a strip too. Posts show their
author's avatar (their photo, else their initials on a color their handle keeps) and how long ago they were posted,
the full date on hover. On a phone, a one-line **What's new?** and a floating button open the composer full screen.
**Network** in the menu (`/network`) is the home feed: a post of up to 3,000 characters, paragraphs kept, where
`@handle` links to the member's page and `#tag` names a skill or a city (typing either lists, under the text, the
members whose handle or name starts with what follows, those followed first, or the tags in use, the most used first;
the arrows move through them and Enter or Tab writes the one chosen), opening the tag's page
(`/network/tags/<tag>`): the posts naming it, the members whose cards show the skill, the offer feed's five newest open
missions naming it (while the feed is on, with a link to them all), and **Follow the tag**. The feed
then holds the reader's posts, those of the members they follow and those naming a tag they follow (listed above
it), the newest first, 20 at a time, the next page coming as the reader nears the end (or with **Show more**), but
those of members muted or blocked either way, or away from the network. Every minute, while the page is seen, the
feed asks whether posts by others reached it since its newest one; a **1 new post** pill then floats over the feed
and reads it again from the top. **Trending this week**, beside the feed (a strip above the posts on a phone), ranks
the 8 tags most named in the last seven days by the posts the reader can read, each with how many posts named it and
opening its page. **Keep as draft** keeps the text
for later, and with a time (a minute to 30 days ahead) **Schedule** publishes it on its own within a minute of that
time; **Drafts**, a tab of the reader's own page, lists them, the scheduled first, each to publish now or delete (50 at
most). A member's page shows their posts too. Its author **edits** a post in the 15 minutes after posting it; it then reads **edited**, which opens its earlier
texts. **Save** keeps a post in the reader's private **Saved** list, a tab of their own page, the latest saved first and
filtered by the tags of the posts saved; a post taken off, deleted or from a member blocked either way leaves it.
**Send**, under a post, sends it in a message, as on LinkedIn: to up to ten of the reader's open conversations (with a
member or a group) or members they can write to (a conversation opening with each), with a word if wanted; the message
carries the post's link, which the conversation shows as the post's card (its author and first words, opening it; "This
post is no longer available" once out of the reader's reach). **Repost** shares a post with an optional comment, the original shown inside it (a repost of a repost reposts the
original; once the original is gone or out of reach, the repost says so); a post's author turns its reposts off.
**For you**, the feed's second tab, gathers the posts of the last 30 days from every member the reader can read (but
their own and those muted), ranked by what they share with the reader (3 points a tag they follow, 2 a tag among the
skills their card shows, 2 the author in their city) and by the members who reacted to them (1 each, 5 at most), the
whole divided by 1 plus a third of the post's age in days. **Not interested**, in a For you post's menu, hides it there for good (Undo brings it back) and counts against what is like it: 2 points off the author's other posts for each of theirs set aside (6 at most) and 1 off each tag it names for each post set aside naming it (3 at most a tag), from the reader's last 200 set aside. **Missions**, the third tab, is the job board of the network:
every **Mission to pass on** the reader can read, from all members and not only those followed (but their own, and those
of members muted, blocked either way or away from the network), the newest first, 20 at a time
(`GET /api/network/feed/missions`); each card has **Add to my pipeline**, which turns it into an opportunity (below).
Its filters narrow them (among the latest 500): a skill or role, a city, a daily rate the mission reaches, a start no later
than a day, at least 2, 3 or 5 remote days a week, and **Hide those in my pipeline** (`skill=`, `city=`, `rate=`,
`before=`, `remote=`, `hideTaken=true`). When a mission is passed on, the members whose card shows one of the skills it asks
(and its city, unless it is fully remote) are told, 200 at most, never its author (**A mission matching my card**, turned
off or emailed in **Profile → Network**). After the members' missions, once their list ends, and while the offer feed is
on, **On the job boards, for your profile** shows the five collected offers matching the reader's profile best (the
skill filtered, when one is), each marked **Job board · <site>** and opening the offer, which adds it to the pipeline,
with a link to all of them in **Offers**: the members' missions come first. Each mission shows how it fits the reader's
profile, scored as the feed's offers are (`NetworkMissionMatchService`: its role and stack for the skills, its daily rate
against the target, fully remote at 5 remote days or its city located against the profile's, its start against the
availability, with the profile's weights; `GET /api/network/feed/missions/matches?ids=`, by post id), green from 60 and
amber from 35; **Best match first** (`bestMatch=true`) ranks the latest 500 by that score, the newest first among equals.

A tag's page lists its posts **Newest** or **Most liked** (whatever the reaction) first. **Like** a post, once, or no
longer; a hover, a long press or the up arrow on **Like** offers the other reactions
(❤️ Like, 👏 Bravo, 😄 Funny, 🤝 Support, 😮 Wow), a member keeping one, the latest. The counts line shows the most
frequent reactions and how many there are, opening who reacted (but members blocked either way). A like, a reaction
and **Save** show at once, coming back if the server refuses. (A third action, **Useful**, said much what Like and its
reactions say, and Save keeps a post for oneself: it was taken out on 2026-11-17, with what members had marked.) A member
deletes their own posts, their likes and comments going with them, and they are part of the data export.

A post is of a kind, chosen in the composer, each with its card: a plain **Post**, a **Mission to pass on** (its role,
required, and the daily rate range, city, remote days a week, start, skills and, only if the author names it, the
client), **Available** (the start, required, the rate range and skills), a **Question**, whose author marks one reply as
**the answer** (the question then reads **Answered**), a **Retour d'expérience**, or a **Poll**: two to four choices,
open one to seven days, each member voting once and changing their vote while it is open, and seeing the shares once
they voted, it closed or it is theirs. A post the spam score held is
published as a plain post; drafts are plain posts. **Add to my pipeline** on another member's mission creates the
opportunity at Contact, filled from its facts (role, rate, remote days, start, place, skills, the post's text), its
client the one named or else the workspace's **Client confidentiel**, its source **Indezy @author**; telling the author
or not. Its author sees how many members took it, and who among those who chose to tell. **Share to the network**, on an
offer's page and on a declined opportunity's page, opens the feed with such a mission filled in from it (an offer's link
included), its publisher or client left out, to read again before publishing; on a won opportunity, it shares the
**Mission signed** milestone, its role only. The dashboard shares the average rate obtained against the objective and the
last 90 days' trends, and the yearly review shares **A year of freelancing** (missions, days worked and the skills most
asked for as tags), never a client nor the revenue.

A post's first link, and a message's, shows as a card under its text (the site, the page's title and description, opening the page
apart), read once in the background after the post or the message goes out (again when its link changes, never for a
deleted message), never an image, so readers load nothing from the linked
site; a page out of reach leaves no card.

A post, and a message, carries up to four **images** and three **documents** (PDF, slides or spreadsheets: pptx, xlsx,
docx and their OpenDocument forms; 50 MB each, 60 files a day), added from the composer and uploaded at once. Each image
needs a description (its alternative text) before the post goes; it is made anew, upright as the camera said, at most
2048 pixels on its longest side, so that nothing the file carried beside its pixels (place, camera, date) is kept.
Images show as a grid (one wide, two side by side, three with one tall, four in a square), each opening at full size; a
document shows as a card with its name, type, size and, for a PDF, its first page and number of pages. A document whose
text names one of the workspace's clients, ESNs or contacts says so in the composer, to remove it or **keep it**. Files
of a deleted post or message go with it; those added but never sent are dropped after a day. The **Saved** list shows
the documents of the saved posts alone with **Documents**.

**Comments** open under a post: comments and, under each, replies (one level), the oldest first. Each writer deletes
their own comment, its replies going with it. The post's author **pins** one comment on top, **hides** one (then only
the author sees it, marked hidden) or **closes** the comments, after which none is added. A member likes a comment, once,
its count shown under it. Comments between members who
block each other, and those of members away from the network, are not shown. Images, documents and link previews wait
on the choice of their store.

**Messages** (`/network/messages`, from the network's navigation, counted with the notifications on **Network** in the
menu) holds one-to-one conversations and groups. Messages are open between two members once either follows the other
(a follow accepted; a request waiting does not count). **New message** picks one member among those the reader follows
or who follow them and opens their conversation (a new one if none); **Message** on a member's page, or on their tile in
**Members**, does the same. **Message** on the page of a member neither follows sends a **message request**: one message,
then it waits (**Your message request waits for Léo's answer.**). The recipient finds it under **Requests** in Messages,
with how many wait, out of their conversations and unread count; they read it and **Accept** (both write since, as
members who follow each other do) or **Decline** (it leaves their list, its sender untold, who writes no more). Profile →
Network → **Receive message requests** turns them off, a member then writable only by those they follow or who follow
them; a member sends 10 requests a day at most. **New group** names a group and gathers up to 19 of those members, for a collective or a
mission team: every member writes in it, each message showing who wrote it; its creator renames it and adds members, and
anyone leaves it, reading it no more. Once neither follows the other any more, either blocks the other or one leaves the
network, the past stays readable but no new message goes, the conversation saying why. The list shows each conversation's latest message and how many are unread, the
latest first. New messages, reactions and reads reach the list, the open conversation and the counts on **Network** as
they happen; while that stream is closed (it opens again by itself), the list is read again every 30 seconds and an
open conversation every 10. Between two members, **Seen** shows under the latest of the reader's messages the other
read, and "Léo is typing…" while they write; **Profile → Network** turns each off, both ways (off, the member no longer
sees it from others either). As before a post, the workspace's client, ESN and contact names in a message are pointed
out before it goes. In a conversation, Enter sends and Shift+Enter starts a new line; a
message is replied to (quoting it; on a touch screen, by swiping it to the right), reacted to with one of the five reactions, and edited or deleted for both by its
sender within 15 minutes. The briefcase beside the field shares one of the reader's opportunities as a card (role, daily
rate, city, start, remote days, stack, never the client); its recipient **Adds it to their pipeline** in one click, at
**Contact** with a confidential client and the sender as its source (who referred it), the card then linking to it.
**The messenger corner**, as on LinkedIn or Facebook: on every page of the app but Messages itself, on a screen wide
enough for the menu to stay (1,024 pixels), a **Messaging** bar rests at the bottom right with how many conversations are
unread (the muted aside). It unfolds the conversations, the latest first, each with its last message and a green dot on
the members **online** now; one opens as a small chat window beside the bar (three at most on a wide screen, fewer on a
narrower one, the oldest closing first), to read and answer without leaving the page, folded to its head or closed with
×, **Open in full** going to its Messages page. The corner follows the live stream and reads itself again every 30 seconds.
A member is online while their app is open (its live stream marks them seen at each heartbeat, whichever server holds it),
and reads as offline about a minute after it closes. Who is online shows only in a conversation between two, and only
between members who both show it: **Show when I am online, and see who is**, in **Profile → Network** beside read receipts
and typing, turns it off both ways.

**Mute** keeps a conversation out of the unread count; **Archive** moves it to **Archived**
until a new message comes. **Search messages**, above the list, finds the reader's messages holding what is typed (two
characters or more, case aside), the latest first, each opening its conversation. Another member's message is
reported like a post (**Report**, with a reason): its text goes to the moderators, who can remove it for both sides.
Messages still unread an hour after they came are emailed: one email for all the reader's conversations, at most every
three hours, saying who wrote and how many messages, never their text; a muted conversation sends none, and the email's
one-click unsubscribe, like **Profile → Notifications**, stops them. **Download as PDF**, in a conversation's menu, gives the whole conversation (each message with its sender and time, the
message it replies to, edited or deleted) in the page's language. The account export includes the messages sent.

**Notifications** (`/network/notifications`, counted on **Network** in the menu as they come, or every minute while the
stream is closed) tell a
member who followed them, liked, commented or reposted their post, liked or replied to their comment or mentioned them by
`@handle`, grouped as they read ("Léo and 2 others liked your post", with the post's first words), the newest first, under
**Today**, **This week** and **Earlier**; unread ones are tinted with a dot, and each opens the post, or the member for a
follow. Opening the page marks them read. A menu on the left (chips above the list below 1,440 pixels) shows them **All**
or only the **Unread** ones (those unread when the page opened), the **Mentions**, the **Comments and replies**, the
**Reactions and reposts**, the **Follows** or the moderators' word, each with how many it holds, and links to
**Notification settings** (`/profile?tab=network`, the profile opening on its Network tab). **Mark all as read** takes the tints and
dots off at once; each notification's menu **deletes** it (its group: those of its kind about the same post, or about
none, `DELETE /api/network/notifications?type=&postId=`) or **turns its kind off**, as Profile → Network would (no more of
them, in the app nor by email; the moderators' word cannot be turned off). **Profile → Network** sends each kind nowhere, to the app (the default) or by
email too: an hour after a notification of a kind chosen for email is still unread, one email groups all those still
unread (at most one every three hours, names and the post's first words, never a comment's text), with a one-click
unsubscribe that stops every kind.

A post has its own page, `/network/posts/<id>`, with its comments open: its time on the card opens it, **Copy link** in
its menu copies its address, and a notification or the global search lead there. The page says the post is gone when
it was deleted, its author left, or the reader may not read it. A follow, a like (of a post, or of a comment under it) and a repost are told once per member, however often they are
undone; nothing comes from a member either one blocks, and **Profile → Network** turns each kind off.

**Members to follow**, above the feed, are up to ten members each with its reason: one of the reader's Indezy contacts
(matched on the email the member signs in with, the contact never told), followed by members the reader follows ("Followed by Léo and 2 other members you follow"), sharing skills with the reader's card ("Also on Kafka"),
or in the same city, the strongest first, on what the cards show only; following one reads the feed again. A member who
turns on **Suggest members who went through the same ESNs** (Profile → Network) also sees those who turned it on too and
had an opportunity with one of their ESNs ("Also through Acme Conseil", matched by SIREN, else by name).

Before a post or a comment goes out, the names of the workspace's clients, ESNs and contacts found in its text (accents
and case aside, as whole words) are listed: **Anonymize** replaces each ("un client à Paris", "une ESN", "un contact")
and leaves the text to read again before publishing, **Publish anyway** publishes it as written. The check only reads
the workspace; nothing of it is published or kept.

**Private card** (Profile → Network) makes a member approve who follows them: **Follow** on their page reads **Ask to
follow** and sends a request, told to them and listed under **Requests to follow** to **Approve** (the member then
follows, and is told) or **Decline** (untold); **Request sent** withdraws it. The card stays visible to members, but its
posts reach only its followers. Turning the card public again approves every request waiting.

**Report** (a post's or a comment's menu, or a member's page) sends it to the moderators with a reason (spam,
harassment, hate, misleading, confidential information, impersonation, something else) and, if wanted, a few words,
once while the report is open; the member reported is not told who reported them. In **Admin → Moderation** an
administrator reads the open reports, the oldest first, with the text as it read and how many reports name the same
target, and **dismisses**, **warns**, **removes** the post or the comment, **suspends** (1 to 90 days, 7 by default:
no posting, commenting, reposting, following or reporting meanwhile) or **bans** (the membership ends and the account
cannot join again). The decision falls on every open report of the same target; the author is told of a warning, a
removal or a suspension, and each reporter whether the moderators acted, in their notifications. Decided reports stay,
with the moderator's note, under **Latest decisions**, and a sanction is in the account's activity log. A post or a
comment that looks like spam (see [Security](./security.md#member-network)) is held instead of published, its author
told so; **Moderation** lists what is held, with why, to **Publish** as its author's or **Reject**.

Every page of the network opens with the same bar, staying at the top as the page scrolls: the network's **search**,
then its sections as a row of tabs, **Home**, **Messages** (with how many are unread), **Members**, **Communities** and **Notifications**
(with how many are unread), the one open marked, so that any section is one click away; on a phone the search takes the
whole row and the five tabs share the one under it as icons with their counts, the open one named too. The reader's own
page, with their saved posts and drafts, is the card on the left.

The **search** suggests, from two characters typed, up to five members and up to five tags in use, each opening its page,
then **See all results**: a member is found when every word typed starts their handle, their first or last name (a word
of it, for a compound name: "bob mar" finds Bob Martin), one of their card's skills or its city, or is in its headline,
accents and case aside, on what their card shows only; a tag when it starts with the one word typed. `@` looks for members
only, `#` for tags only. **Enter**, or **See all results**, opens the results (`/network/search?q=`): **All** shows the
first three members found (those named by their name first, the whole name before a word of it, then those the reader
follows, then by last name; with their headline, city and first skills, **Followed by 3 members you follow** when
some do, and **Follow**, **Ask to follow** a private card
or **Following**), the tags, and the first three posts holding what was typed (case and accents aside: "developpeur" finds "Développeur", the newest first, from every
member the reader can read, their own included, but those muted or blocked either way and the private cards they do not
follow); **Members**, **Posts** and **Tags** show each kind alone, 20 at a time, the tab kept in the address
(`?tab=`). **Members** narrows the members found by availability (available, open to offers, on a mission), remote work and a city, and **Posts** the posts by kind, to the last 7 or 30 days, or to the members followed, as chips kept in the address with **Clear filters**. The empty field offers the last five searches made on the device (kept in the browser only), with **Clear recent searches**. The server searches members with `GET /api/network/search/members?q=&page=&city=&availability=&remote=` and posts with
`GET /api/network/search/posts?q=&page=&kind=&days=&followed=` (nothing under two characters; `%` and `_` searched as written). Mentions in the
composer match names the same way, every word typed.

A post's, a tag's or a member's page puts **Back** before the
search: to the page the reader came from within the app, or to the feed when they opened it first, from a link or a
bookmark.

**Communities** (`/network/communities`, a tab of the network's bar) gather members around a stack, a city or a trade:
listed with those the reader is in first, then the largest, found by name, each joined in a click. **Create a community**
names one (60 characters, a description of 500 if wanted; ten at most a member), at an address made from its name
(`/network/communities/kafka-a-lyon`). A community's page shows its name, description, members and moderators, **Join** /
**Leave**, and its feed: its members post in it from its composer (a spam score holding a post as anywhere else), every
member reads it (those muted or blocked either way aside). Its creator moderates it and names co-moderators among its
members; moderators edit its name and description, **remove** a post from it (deleting it) and **remove** a member (who
cannot join again; a co-moderator only by the creator, never the creator); when its last moderator leaves, its earliest
member moderates it. The administrators have the creator's hand in every community, the last word, as decided in
TODO.md (**Member network audience**); deleting a community (its creator or an administrator) keeps its posts as plain
posts. A community is open, or **private** (chosen when creating it, or changed by its moderators): a private one is
joined on request (**Ask to join**, **Withdraw the request**), its moderators told and answering in a **Requests** tab
(the member let in is told, one declined is not), and only its members (and the administrators) read its posts, anywhere:
its feed, the home feed, a member's page, the search, a post's own page; opened again, every request waiting is let in.
A community's posts reach its members' home feed, and every post posted in a community names it beside its time
(opening it), but in its own feed. Its members are told of each post in it (**Profile → Network** turns it off, like any
kind), its moderators of a request to join, a member of being let in or named a moderator, each notification naming the
community and leading to the post or to the community. Reports of its posts reach the administrators' moderation queue as any post's do
(`/api/network/communities`, `/<slug>`, `/<slug>/membership`, `/<slug>/posts`, `/<slug>/members`, `/<slug>/moderators/<handle>`,
`/<slug>/requests/<handle>`).

**Events** (`/network/events`, from **Events** on the reader's card) are the members' meetups in a place and webinars
online. A member organizes one (5 a day at most) with a title, when it starts and ends (two hours when no end is given,
Paris time), its address or its link to join, its tags and a description, and goes to it. The list shows the coming
events the soonest first (**Upcoming**), those the reader answered (**My answers**) and those they organize, past ones
too (**Organized by me**), found by words or by a tag (`?tag=`); each card gives the date, the time, the place or
**Online**, the tags, the organizer and how many go, with **Going** and **Interested** answered in a click (again to take
it back). An event's page (`/network/events/<id>`) adds its description, the link to join or its page, who goes and who
is interested, and **Add to my calendar**: an .ics file any calendar reads, its times in UTC. Its organizer (or an
administrator) changes it until it ends (a new time reminds again) and cancels it; those who answered are reminded the
day before (**Tomorrow: an event you answered**) and told when it is cancelled, two notification kinds of their own that
Profile → Network turns off or emails. A tag's page lists the three next events naming it, with a link to all of them.
A cancelled or past event takes no answer; an event whose organizer left or with whom either blocks the other is not
found (`/api/network/events`, `/<id>`, `/<id>/attendance?status=`, `/<id>/attendees`, `/<id>/ics`).

**Invite to the network**, on a contact's page in Contacts, emails a contact with an address an invitation from the
member, in the member's language, once per contact, after a confirmation (20 a day at most). The button gives way to
**Invited on <date>**, to **Their page on the network** once the contact is a member, or to **Does not want to be
invited** for an address that declined. The email's link opens a page without an account (`/network-invitation#<token>`)
saying who invites, with **Create my account** and **Never invite me again**, also the email's one-click unsubscribe:
no member invites that address again. The address is kept only as its SHA-256; when the person joins the network, each
member who invited them is told (**joined the network from your invitation**), and they are suggested to each other as
**In your contacts** (`/api/network/invitations/contacts/<id>`, `/api/public/network-invitations/<token>`).

**Ideas for Indezy** (`/network/ideas`, from **Ideas** on the reader's card) is the ideas board: members suggest what
they miss in Indezy (a sentence of 5 to 120 characters and an optional detail, 5 a day at most), their own support
counted, and support the ideas they want, once each, taking it back at will. While an idea is written, the three most
supported ideas sharing its words show under it, to support them rather than repeat them. The board lists the most
supported first or the newest, narrowed by status (Open, Planned, In progress, Shipped, Not planned) or by words of the
title or detail, 20 at a time; each idea shows its number, its supporters, its status, the administrators' word, and
who suggested it (a former member once their account is deleted, the idea staying). Its author changes an open idea,
and deletes it while no one else supports it. The administrators answer each idea with a status and a word, merge a
duplicate into the idea it repeats by that idea's number (its supporters moved there, counted once) and delete any;
those who support an idea are told when its status changes or it is merged (**An idea you support is now: Planned**, a
notification of its own kind, which Profile → Network turns off or emails like the others). An idea's address
(`/network/ideas/<number>`) shows it above the board, a merged one with a link to the idea kept. A shipped, declined or
merged idea takes no more support (`/api/network/ideas`, `/<id>`, `/<id>/vote`, `/<id>/status`, `/<id>/merge?into=`).

**Members** on the feed (`/network/members`) has five tabs, the one open kept in the address (`?tab=`). **All members**
is the member directory: the members found by a skill and a city typed in its search bar, an availability and remote
work picked as chips, 20 a page by last name, as a grid of tiles (the member's color, avatar, headline, city,
availability and first three skills), each opening the member's page and with its own **Follow** (or **Ask to follow** a
private card, **Request sent**, **Following**). It searches what each card shows only, so a hidden skill or city is never
found, and leaves out the viewer and any member either one blocks. **Following** and **Followers** list the reader's
follows and followers as the same tiles, **Requests received** the requests to follow their private card (counted on the
tab) with **Approve** and **Decline**, and **Requests sent** those they wait an answer for, withdrawn from the tile
(`GET /api/network/follow-requests/sent`). Following goes one way, as on Instagram: there are no friends, and a follow
counts once accepted (at once for a public card). A tile shows **Message** once either follows the other.

### Client And ESN Reviews

A client's page shows **Reviews by members** to the network's members (`GET /api/network/reviews/client/{clientId}`):
the averages of three ratings out of five (payment, process, honesty on the rate) and the published reviews, without
their authors, each with **Report** (`POST /api/network/reviews/{id}/report`, the reasons of posts). The reviews of
members who each keep the company as their own client meet by its SIREN, else by its name folded (case, accents and
punctuation aside). A member who won a mission with the client, as final client or as ESN, writes or rewrites their one
review of it (`PUT`, 20 to 2,000 characters) or deletes it.

A review is held for the administrators instead of shown when it looks like spam, an insult or personal data
(`NetworkReviewFilter`): a link, an email address, a phone number, the name of one of the author's contacts at the
company, an insult or slur, an accusation of an offence ("escroc", "arnaque", "scam": a fact for a court, a defamation
risk for a review), the same text as another of the author's reviews, or mostly capitals. The author is told it waits
(`REVIEW_HELD`), and so is each administrator (`REVIEW_PENDING`). **Admin → Moderation** lists the held reviews with
their reasons: the administrator edits out what should not show and publishes, or rejects, the author told either way
(`REVIEW_PUBLISHED`, `REVIEW_REJECTED`). A reported review is decided like a post; removed, it is no longer shown and
cannot be written again for that company.

**Admin → Moderation** opens on the reports of the last 90 days at a glance (`GET /api/admin/network/reports/dashboard`,
`days` from 1 to 365): how many, how many still open and since when (under a day, under a week, longer), by reason,
by what they target, and the ten companies whose reviews were reported most, with their open and total reports.

### Rate Benchmark

Beside the feed, **Daily rates in the network** gives the median daily rate of a skill and the middle half (25th to 75th
percentile), for a seniority and a region or all of them, from the won missions of the members who share their rates
(**Profile → Privacy → Share my rates with the benchmark**, off by default). Each freelancer counts once per skill (their
latest won mission naming it in the last two years); a figure shows only when five freelancers or more back it, otherwise
the card says how many do. Seniority comes from the years the profile gives for the skill, the region from the mission
(remote, Île-de-France or the other regions). See [ADR 0014](./adr/0014-aggregated-market-data.md).

### Web Push

**Profile → Notifications → Notifications on this device** turns on push in the browser it is in, once the browser
allows notifications: the network's notifications ("You have a new notification on the network") and new messages of
conversations not muted reach the device even with Indezy closed, in the account's language, a click opening the
notifications or the conversation. So do an interview planned within the hour ("Interview at 14:00", its step, role and
client, opening the opportunity; Paris time, checked every 5 minutes) and, on weekday mornings at 9:07, the follow-ups
due ("3 contacts wait for your follow-up", opening the dashboard); each is claimed in the database first
(`interview_steps.reminder_pushed_at`, `users.follow_ups_pushed_on`), so several replicas push it once. Each browser keeps its own subscription (ten per account at most), sent to the
server (`POST /api/push/subscriptions`, `/remove`) and dropped once its push service no longer knows it. Pushes are
encrypted for the browser (RFC 8291) and signed with Indezy's VAPID key (RFC 8292); only the push services of the major
browsers are accepted as endpoints. Without the server's VAPID keys ([Deployment](./deployment.md#web-push-keys)),
the switch says push is not available yet.

### AI Features

Claude helps where typing is slow ([ADR 0015](./adr/0015-ai-provider.md)); what it writes is shown to check and change,
never saved or sent on its own:

- **Fill in from a pasted posting (AI)** on a new opportunity (`POST /api/ai/posting`, 20,000 characters at most): the
  role, the company (an ESN's end client when it names one), the rate, the stack, the place, the work mode and remote
  days, the start date and the duration, nothing it does not state; the posting becomes the description, the client and
  a **Job posting** source are created on saving when the workspace has none of that name. **From a PDF** puts the text
  of a posting saved as a PDF in the box to check first (`POST /api/ai/posting/pdf`, no AI: 5 MB and the first 10 pages
  at most, lines kept, cut at 20,000 characters and said so); a protected PDF, a scanned one without text or another
  file is refused, saying which.
- **Suggest a rate (AI)** under the daily rate of the opportunity form (`POST /api/ai/rate`): the daily rate to ask, the
  range a client would accept and up to three reasons, from what the form says (role, stack, work mode and remote days,
  place, duration, description), the profile's skills and years, target rate and town, and the feed's median for the main
  skill, shown beside it; **Use** puts the rate in the field. The rate is kept between 100 and 3,000 euros, its range
  around it.
- **Prepare the interview (AI)** on an opportunity (`POST /api/ai/interview/{projectId}`): up to six technical and six
  behavioural questions it will likely ask, each with a hint drawn from the profile, a two-minute pitch for this mission
  to copy, and up to six questions to ask, from the opportunity, its steps so far (with their notes), the profile and
  what the [company health check](#company-health-check) says of the client and the ESN when they have a SIREN (in
  business, closed, or under proceedings since a date, which brings questions on the mission's stability and how
  invoices are paid); **Keep as a note** puts it in the journal as Markdown. A mock interview is a conversation in **Ask the AI about this
  opportunity**.
- **Prepare the negotiation (AI)** on an opportunity (`POST /api/ai/negotiation/{projectId}`): a counter-offer, the rate
  below which to walk away (never above the counter-offer), a strategy and up to five arguments for the call, from the
  rates offered, asked and paid by the client, the profile's target and the feed's median for the main skill; the
  ESN's margin is worked out, not asked, when the client rate is known.
- **Draft a message (AI)** on an opportunity (`POST /api/ai/message`): an application, a follow-up, a thank-you after an
  interview or a decline, in French or English, from the role, the company, the stack, the stage, the description and a
  few words on what to say; the subject and the message are editable and copied in one click.
- **Polish (AI)** in **Write an email** (`POST /api/ai/polish`, 5,000 characters at most): the subject and message with
  their spelling and grammar fixed, or more formal, warmer or shorter, in their own language, every `{{variable}}` kept
  (an answer that drops or adds one is refused, saying so); **Undo** brings back what was there.
- **Weigh this opportunity (AI)** on an opportunity (`POST /api/ai/fit`): a fit score out of 100 and a verdict, what
  fits and what may not, the soft criteria to check (management, team, pace, remote culture), the questions to ask in
  the interview, and what to think of the rate against the profile's target and the feed's market median for its main
  skill, with a rate to ask; from the opportunity, the profile (skills and years, target rate, town, presentation) and
  what matters to the freelancer.
- **Ask the AI about this opportunity**, a card on its page (`GET`/`POST`/`DELETE /api/ai/chat/{projectId}`): a
  conversation for interview preparation, negotiation or a post-mortem, with suggested questions; the opportunity's
  description, notes, steps and latest journal entries are its context, the last 20 turns go back with each question
  (2,000 characters at most), and the history stays with the opportunity, cleared on request, deleted with it and part of
  the data export.
- **Tailored dossier (AI)** on an opportunity's page (`POST /api/ai/dossier/{projectId}?layout=NAMED|ANONYMOUS&language=FR|EN`):
  the [dossier de compétences](#dossier-de-competences) as a PDF with its summary rewritten for the opportunity, the
  skills it asks for first and the descriptions of the 15 latest missions bringing out what matters for it, in the
  dossier's language. The AI only rewords: a skill it names that the profile lacks is dropped, a mission without a
  description gets none, the summary is cut at 900 characters, and the name, contact details and clients are never sent
  to it. Nothing is kept.
- **Import my profile → Choose my CV** on the profile's personal information (`POST /api/ai/profile-import`, a PDF, Word `.docx` or `.doc` (its text read with Apache POI) or
  OpenDocument `.odt` of 10 MB at most, its first 20,000 characters read): the name, phone, city, current position and
  company, presentation, links, languages and skills with their years, nothing it does not state. A review shows each
  field found beside the profile's value, ticked when the profile's field is empty and unticked when it differs, the
  skills the profile lacks (case aside, up to the 50-skill limit) and the languages it offers; **Fill in the form** puts
  only what is ticked in the form, adding skills and languages and never removing any, and nothing is saved until the
  profile is. The file is neither kept nor scanned, being only read; a scan without text or another format is refused.
- **Search by meaning (AI)** in **Search everything**, offered while the AI is available and two characters or more are
  typed (`POST /api/ai/search`, 300 characters at most): the latest 200 opportunities (role, client, status, stack,
  description, notes and end reason) and 300 journal notes, each cut to 240 characters with an id, go to the AI, which
  answers the ten at most that fit the question by its meaning, each with one sentence saying why; ids it was not given
  are dropped, each kept once. A hit opens its opportunity. Nothing is asked of a workspace without records.
- **Describe my search (AI)** above the offer feed (`POST /api/ai/offer-brief`, 2,000 characters at most): what the
  freelancer looks for in their own words set as the feed's filters (skills, minimum rate, remote days, work modes,
  regions by name, latest start, minimum duration, keywords, and what to avoid as words to leave out), every other filter
  cleared, ready to save as a search.
- **Prepare my application (AI)** on a feed offer's page not yet in the pipeline (`POST /api/ai/offers/{offerId}/pack?channel=ESN_EMAIL|MALT|LINKEDIN`):
  the message in the channel's format (an ESN email with its subject, a Malt proposal, a LinkedIn message cut to 300
  characters), editable and copied, the answers to the usual screening questions (availability, notice, rate, remote)
  and a rate to propose with its reasons, from the offer, the profile, its availability date and the feed's median for
  the offer's first skill. **Add to pipeline with this pack** adds the offer as a Contact-stage opportunity, keeps the
  pack in its journal and opens it, where **Tailored dossier (AI)** is.
- **Should I apply? (AI)** on a feed offer's page (`GET`/`POST /api/ai/offers/{offerId}/verdict`): one line on whether
  the offer is worth applying to for the profile (skills, target rate, town) and its deal-breakers (on site every day, a
  rate under the target, a permanent job posted as freelance, portage required, a start date passed, required skills the
  profile lacks). The verdict is kept for the workspace so an offer is paid once, asked again on request, and dropped
  every night at 05:20 once the feed removes the offer.
- **Profile → Preferences → AI** (`GET /api/ai/status`, `PUT`/`DELETE /api/ai/key`): whether AI can be used and this
  month's allowance on Indezy's key (10 US dollars a month on the free tier, all accounts together within 100), or the member's own Anthropic key, stored encrypted, with
  Opus, Sonnet or Haiku 5.5 for every task or **Automatic**, which no limit applies to. Automatic, as on Indezy's key,
  puts each task on the cheapest model that does it well: Haiku 5.5 reads postings and triages offers, Sonnet 5.5 writes
  messages, tailors dossiers and suggests rates, Opus 5.5 weighs opportunities, holds conversations and prepares negotiations and
  interviews; the card lists them. With their own key the member picks any task's model in that list
  (`PUT /api/ai/key/models`, `chosen` in the status), the others keeping the key's model or their own; going back to
  Indezy's key forgets the choices. An allowance used up, Indezy's monthly cap reached, a refusal or a
  key Anthropic refuses are each said as such.

## Projects And Opportunities

**Opportunities** (`/projects`) lists the workspace's opportunities and missions. Beyond the role, stage, description,
tech stack, dates, duration, work mode and remote days, an opportunity holds:

- its **client** (the final client, or the ESN when the final client is unknown, as for offers added from the feed), an
  optional ESN or intermediary, a source and a season;
- the client daily rate, from which the margin is shown, and the asked and offered rates beside the agreed one;
- once refused, optionally why, asked on every move to Abandonné (refused by the recruiter) or Désisté (refused by the
  freelancer);
- **Files** in its **Documents and links** card: PDF, images, Word, OpenDocument or text, up to 50 MB each and 20 per
  opportunity.

Its page changes the stage in place and shows the tech stack as tags with how well they match the profile (a tag can
be renamed across every opportunity), the steps, journal and emails, and once won the activity reports and purchase
orders. **Copy project link** copies an address that opens the page for the owning account only.

Each card of the list, whatever the work mode (remote, hybrid or on site), shows the mission's place (its place of work,
else its client's city) with its distance from the profile's address, and the trip there by car and by public transport
from off-peak to rush hour (`≈ 18 min – 26 min by car · ≈ 42 min – 48 min by transit`), as described under
[Commute-Time Sorting](#commute-time-sorting). The page shows the place under **Mission location**.

The list's filters (words, rate range, work mode, technologies, stage, season, dates, duration, client, **Archive**) and
sort live in the page's address, so a view can be bookmarked or shared. **Export summary** downloads the opportunities
starting this year as CSV.

| Stage | Column (French / English) |
| --- | --- |
| `CONTACT` | Contact |
| `INTERVIEW` | Tests & Entretiens / Tests & Interviews |
| `OFFER` | Offre / Offer |
| `LOST` | Abandonné / Abandoned (refused by the recruiter) |
| `WITHDRAWN` | Désisté / Withdrawn (refused by the freelancer) |
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

### Co-optation

An opportunity's page holds a **Co-optation** card: who referred the mission to the freelancer (**I pay a bonus to who
referred me**), or whom they referred to it (**I receive a bonus for referring**), by name or as a network member's
`@handle` (taken from its source, `Indezy @handle`, when it was added from the network), and the bonus that goes with it:
none, a fixed amount, an amount per day worked, or a share of what the mission earns (100% at most). The bonus is counted
from the mission's timesheets (days worked, at its daily rate for a share: **20 € × 12 days worked = 240 €**), and stands
**Promised**, **Due** or **Paid** on a day, with a note. `GET /api/projects/referrals/{freelanceId}` lists the account's
co-optations with what is still to pay and to receive (`GET`, `PUT`, `DELETE /api/projects/{id}/referral`); deleting the
mission or the account deletes it.

## Kanban And Pipeline View

The dashboard's Kanban mode shows the pipeline in the six stage columns and follows the selected season. Hovering a
column's title says what it holds (**Refusé par le recruteur** on Abandonné, **Refusé par vous (consultant)** on
Désisté).

- Dragging a card changes its stage; into Abandonné or Désisté it asks why (optional), shown on the card. Who refused is
  the column itself. Within a column, cards keep a manual order under the favorites (the star).
- A card shows its days since last activity and is highlighted once idle for the inactivity period (14 days by
  default, 1 to 365 in **Profile → Preferences**), which also drives the dashboard's stale opportunities.
- A column's quick add creates an opportunity from a role, client and rate. Cards sharing a client and role with
  another (case aside) are flagged as possible duplicates.

**Archive** on an opportunity's page takes it off the board and the reminders but keeps its stage for the statistics.
**Archive idle opportunities every night** (Preferences; on for accounts created since 2026-11-21, off for older ones) archives at 03:30 Paris time every opportunity
the board highlights. The **Archive** page lists and searches them with the accepted, abandoned and withdrawn ones; **Restore** puts one
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

**Dictate**, beside the note, turns speech into the note's text in the interface language, after what is typed, with
the words still being heard shown under it, until **Stop dictating**: a voice memo after a call. It uses the browser's
own speech recognition, so nothing is recorded or sent to Indezy (Chrome and Edge send the audio to their maker's speech
service; the button is not shown where the browser has none, as in Firefox).

## Emails From Templates

**Write an email** on an opportunity picks a template and a recipient among the ESN's and the client's contacts, then
fills a subject and message to edit; **Open in my mail app** opens a `mailto:` link, **Copy** copies them, and **Send
from Indezy** sends it (see [Emails Sent From Indezy](#emails-sent-from-indezy)). A follow-up, a rate proposal, an availability announcement and a rate review at renewal are always
offered (an opportunity opened with `?email=rateReview` starts on the last); **Profile → Emails**
manages the workspace's own (up to 50). Variables are written `{{name}}`: `firstName`, `lastName`, `company`, `role`,
`rate`, `availability`, `myName`, `myPhone` and `privacyNotice`; one without a value stays as written and is pointed out.

### Emails Sent From Indezy

**Send from Indezy** in **Write an email**, offered once Resend is set up (`GET /api/contacts/emails/status`), sends the
message to the chosen contact through Resend (`POST /api/contacts/{contactId}/emails`): from "the freelancer's name via
Indezy" at Indezy's sender address, answers going to the workspace's own address, the message as typed with a line under
it saying who sent it through Indezy. It is recorded as an exchange by email from the freelancer, waiting for an answer,
its subject as the summary and about the opportunity, so it counts in the follow-ups due; nothing is recorded when
Resend refuses it. **Send me a copy** (on by default) sends the freelancer the same message without tracking. With
`dossier: true` the [dossier de compétences](#dossier-de-competences) (named, in the email's language) goes attached.

**Email my ESN contacts**, in **Announce my availability (AI)** on the week's goal, lists the contacts with an address at
the ESNs the workspace had an opportunity with, as the ESN or the client, whose contacts answered it before, or that
published offers in the feed in the last 60 days naming one of the profile's skills (their name matched whatever its
spelling, `postings` the count) (`GET /api/contacts/emails/availability-recipients`, end clients and blacklisted
companies left out, anyone in touch in the last six weeks, and anyone marked **Asked not to be contacted**), those who
answered or wrote most first with how many times, then those posting most ("posted 3 matching missions lately"), then
by company and name, none ticked at first.

**When** sends them now or plans them **Over 1, 2, 3 or 5 working days** with **At most a day** (15 by default, 1 to 50):
`POST /api/contacts/emails/scheduled` (one email per contact, written for it) spreads them evenly over Monday to Friday
but French public holidays, 9:00 to 17:00 in Paris, the first of today five minutes on at the earliest, taking more days
when the cap asks. A job sends each when its time comes (one replica only, claimed first), as **Send from Indezy** does
and recorded in the contact's exchanges then; one whose contact was in touch since it was planned, asked not to be
contacted or was anonymized is dropped unsent. Refused for the day's 50 emails, one waits for the next working morning;
another failure tries again an hour later, five tries at most. **Waiting to go** lists them, the soonest first, each
with **Cancel** (`GET` and `DELETE /api/contacts/emails/scheduled/{id}`); up to 200 wait per workspace, gone with the
contact or the account.

Each sending, now or planned, is a **campaign** (`POST /api/contacts/emails/campaigns`, named after the subject; its
emails carry `campaignId`, an exchange keeping it). **Your campaigns** lists the latest twenty that sent or plan
something (`GET /api/contacts/emails/campaigns`): the emails sent and still to go, the contacts who answered since
(an answer recorded on the email, or an exchange from them on or after its day), the opportunities opened since with
their companies as the client or the ESN, and the missions won among them. A campaign that cannot be started leaves its
emails going without one. **Prospect again (N new)** on a campaign ticks every contact listed today that it did not
write to or plan for (`contactIds`), its subject back, for a new sending. The subject and message are written from the availability date and the network post when the section
opens, `{{firstName}}`, `{{company}}` and `{{mission}}` (the title of the newest offer naming a profile skill their company published in the last 60 days, `mission` on each recipient; a line holding it is left out for a contact without one) replaced by each contact's; **Edit** beside a ticked contact opens their own message, written from the shared one, sent in its place (**Back to the shared message** undoes it); **Send to N contacts** sends one email each, one after the other, with the dossier attached unless unticked,
each recorded in the contact's exchanges and counted in the 50 a day; those that could not go are named and stay ticked.

**Tell me when it is opened**, off by default and asked email by email (the owner's choice), adds a one-pixel image whose
address holds a 256-bit random token (`GET /api/public/emails/{token}.gif`, the same image whatever the token) and says
at the email's foot that the sender is told when it is opened. The exchange then shows **Opened N×, first on…** or **Not
opened yet**. Fetches within 30 seconds of sending (mailbox scanners) and after 30 days count nothing; mail apps that
fetch images on delivery or never make it a hint. Up to 50 emails a workspace go in 24 hours. Answers are not detected
yet: they reach the freelancer's mailbox, and **Answered** is set by hand (see TODO.md's **Needs Input**).

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
60 in Preferences), until an answer or a newer exchange is recorded. Once its company (client or ESN) answered twice, the
row says how long it usually takes ("usually answers in 3 days": the median days from a message sent to one of its
contacts to that contact's next exchange), and the follow-up is due two days after that instead of after the delay.
When the message was about an opportunity, the row's **Draft a follow-up (AI)** opens [Draft a message (AI)](#ai-features)
on it, set to a follow-up (`projectId` on each follow-up due).

### Contact Network And Org Charts

**Reports to** on a contact's form names their manager, at any company, never the contact itself or someone it
manages. A contact's page shows its manager, team and whom it **Works with**: two-way links, often across a client and
its ESN, each with a note of up to 100 characters, up to 100 per contact. A deleted manager's team reports to nobody.
The client's **Contacts** tab draws the **Org chart**, with each person's links to other companies.

### Contact Privacy

The freelancer is the controller of their contacts' personal data; Indezy keeps it on their behalf. A contact's **Their
data** card shows the legal basis (legitimate interest by default, consent or contract), **Asked not to be contacted**
(`doNotContact`, ticked on the contact's form when they object to prospecting, in their data download; such a contact is
never offered in **Email my ESN contacts**), **Download their data** (a
JSON file of what the workspace holds about them, without other people's names) and **Erase on their request** (the
contact goes with their exchanges and links). Both requests are recorded in the account's activity by the contact's
number only. The `{{privacyNotice}}` email variable tells a recipient who keeps their details, why, and how to see,
correct or erase them.

**Profile → Preferences → Anonymize contacts without exchange for** sets a retention period: never, 1, 2,
3 or 5 years (3 years for accounts created since 2026-11-21, never for older ones, as the CNIL advises for prospects). Every night at 03:45 Paris time, a contact with no exchange, change or related opportunity change for that
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
| Beyond Skills IT | offers labelled Freelance | every 12 hours |
| Koïno | sitemap missions open to applications | every 12 hours |
| Sixième Homme | the missions its home page showcases (the only public ones) | every 12 hours |
| FreelanceRepublik | missions of its job board with a contractor posting | every 12 hours |
| EasyPartner | sitemap jobs with a contractor posting ("Prestation") | every 12 hours |
| Castor Network | Alten's freelance missions (engineering as well as IT) | every 12 hours |
| KatchMe | open freelance offers of its WordPress API | every 6 hours |
| OuiDesk | sitemap missions, dated when first seen (applying takes a referral) | every 12 hours |
| Kicklox | sitemap missions whose contract is "Prestation", without partners' salaried jobs or standing ads | every 3 hours |

Sites describing each offer on its own page are read a batch per run, so their backlog fills in over the first runs;
an offer set aside (salaried, older than 90 days, gone, or listed at an address no request can be made of) is not read
again for 30 days; a link's quotes or spaces are percent-encoded first, so they do not set it aside. The boards not
read are listed on the [Job boards](#site-health) page; the next ones are planned in
[TODO.md](../TODO.md#freelance-offer-feed-added-2026-10-19).

A card shows the site, title, publisher, daily rate, place with its distance from the profile's city, the trip there by
car and by public transport from off-peak to rush hour (`≈ 18 min – 26 min by car · ≈ 42 min – 48 min by transit`,
estimated from the straight line as described under [Commute-Time Sorting](#commute-time-sorting), none for a fully
remote mission), work mode (read from the text when the site gives none), duration, start, seniority, skills, an excerpt
and its age; unseen offers are marked **New**. The **distance** sort is also the commute order, the estimate growing
with the distance.

- **Filters** (kept in the address): keywords in the offer or the workspace's note, words to leave out (an offer naming
  any of them in its title, company, skills, place or description is not shown: a sector, a client), rate range, regions, departments,
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
- **Worked with** marks an offer whose publisher (its name folded, whatever the spelling) is the client or the ESN of a
  mission the workspace won; **Non-solicitation** marks one from the end client of a mission signed through an ESN whose
  [clause](#non-solicitation-clauses) still runs, its tooltip naming the ESN and the clause's last day; **Never answers
  you** marks one whose publisher's contacts were sent three messages or more in their [exchanges](#contact-interaction-history)
  and never answered any, nor started one, its tooltip giving how many; **Likely for …** names one of the workspace's
  end clients (companies marked final, and the clients of its missions through an ESN) that the offer's title or
  description names as a whole word, the longest name first, not the publisher itself (`relation` on each offer,
  `unanswered` and `endClient`).
- **Hide every offer from …**, or a name typed in **Profile → Offer feed**, blocks a publisher whatever its spelling, up
  to 200 per workspace. Above the results, a publisher of which the workspace hid five offers or more in 30 days (its
  spellings together), never kept nor applied to, is offered to block (**Hide all its offers**, three at most,
  `GET /api/offers/blocked-publishers/suggestions`); **Not now** leaves it, not offered again on that device.
- Below it, a sector word that five or more of the offers hidden in 30 days mention, a quarter of them at least, and no
  offer kept or applied to, is offered to leave out (two at most, one per sector, `GET /api/offers/hidden-words/suggestions`).
  Only a fixed list of sector words is read (banque, bancaire, assurance, defense, energie, pharma, telecom,
  aeronautique, casino, crypto, immobilier…), so a suggestion never names a skill or a client. **Leave them out** adds the
  word to **Leave out offers mentioning** for this search (saving the search keeps it); a word the search leaves out
  already is not offered, and **Not now** is remembered on that device.
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

**What counts in the match score** on the profile weighs each part (`matchWeightSkills`, `matchWeightRate`,
`matchWeightPlace`, `matchWeightStart`): **Doesn't count** (0), **As usual** (1, the default) or **Counts double** (2),
the skills once or twice, never not at all. A part's points and its maximum are both multiplied, so the score stays out
of 100 (`OfferMatch.Weights`), alike on the cards and in the SQL that sorts by match and keeps **Matches my profile**.

### Rate Versus Market

A card stating a rate shows how it compares with the median of the open offers naming the same first skill (one per
mission): in the offer's region with ten offers or more, else in France, else nothing ("+12% vs. Java in
Île-de-France"; green from 5 % above, amber from 5 % below). Medians are computed nightly at 04:45 Paris time.

### Market Report

**Quarterly market report** (`/market-report`, open without an account, linked from the Market panel;
`GET /api/public/market-report?quarter=2026-Q3`, the last complete quarter by default, one of the last eight or the
running one) gives the daily rates the feed's offers published in the quarter state (the top of a range): the median
and middle half across skills, then for the 30 skills most missions name their median, middle half, change since the
previous quarter and median by seniority (the posting's), and the median of each region. A mission posted on several
sites counts once; every figure needs 10 missions or more, else it is left out. Members' own rates are never used:
those stay the members' [benchmark](#rate-benchmark) (ADR 0014). A report is computed once an hour at most.

### Market Trends

**Trends**, at the end of the **Market** panel, shows for a profile skill the median rate and open offers on the latest
night and their change over 30 days, 90 days and a year, in the one region the feed is narrowed to or in France. The
nightly medians are kept 400 days.

### Skills Gap

**Skills in demand you lack**, under the trends (`GET /api/offers/skills-gap`), compares the profile with the open
offers naming at least one of its skills: the share of those offers naming each skill the profile lacks, spellings and
aliases of one skill counted once ("Kubernetes 33 % · 14 offers"), shown from 10 matching offers for skills in at least
10 % of them and 3 offers, the six most asked first, with **Plan a training** to the Documents page's trainings. The
open offers' skills are read again at most every 10 minutes.

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
| Reposted, never changed | its mission was published under six references or more in 60 days, none of its details ever changed: a listing kept up to gather profiles |

The rules are applied to the open offers every hour.

### Site Health

**Job boards** (`/offers/sites`), linked from the feed, lists every board read, failing ones first, with its status,
last and next reading, recent and open offers, and its last error, flagging a board silent for a month or a reading that
found nothing.

- **Show in my feed** switched off keeps a board out of the workspace's feed, saved searches and snapshot.
- **Read now** reads a board at once, at most once every 15 minutes whoever asks.
- **Sites to visit yourself** lists, as cards, every site Indezy does not read, in three groups: where missions can
  still be found in person (HiTechPros, Opteamis, LookingForTechs, Capgemini TalentNet, LinkedIn, Mon Consultant
  Indépendant), matching services (Malt, Freelance.com, WheelOfWork, Comet, Amazing.dev, Crème de la Crème), and
  **Orienté CDI** (CDI-oriented): HelloWork, Welcome to the Jungle, Urban Linker, Bluecoders, UNLCK and Konekt, worth a
  visit when looking for a salaried position too. Each card says what it takes; **Mark as visited** records the last
  visit.

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
field. It uses the browser's Indezy session, holds no password and reads nothing on the pages visited unless asked; its
options set the Indezy address, and optionally a personal API token (kept in the browser's local storage, never synced).

**Fill this form with my Indezy profile** (right-click on a page) is the form co-pilot: with that token (one with no
part ticked) it reads `GET /api/application-profile` (name, email, phone, city, LinkedIn, GitHub, website, title, target
daily rate, availability; nothing else of the profile), then fills the page's empty fields by what each says it is
(`autocomplete`, name, id, placeholder, label, in French or English: `extension/fill.js`), and puts the CV
(`GET /api/application-profile/cv`: the one kept on the profile, else the named dossier de compétences in French) in the
first empty file field asking for one ("CV", "résumé", "curriculum"); never a password, another file, a
hidden, read-only or disabled field, a text area (a cover letter) or one already typed in, and never sends the form. A
note in the page says how many fields it filled (or why it could not), and Indezy's new opportunity form for the page
opens beside it, unfocused, to keep the application. Another Indezy than the usual one (or a local one) is reached once
the browser allows its address, asked when the options are saved. Publishing it in the Chrome Web Store waits on a decision (TODO.md, Needs Input).

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
- **By hand** (**New invoice**): a client, optionally a mission, a period, the language and up to 50 lines. A line with
  a negative quantity takes off (a discount, a deposit already invoiced); its price is never negative, as EN 16931
  asks, a quantity is never zero, and an invoice totalling less than zero is not issued (a credit note does that).
- **From a quote** (see [Quotes](#quotes)): its deposit invoice, then its final invoice.

A draft changes freely and its PDF says it is not an invoice. **Issuing** needs a complete
[billing identity](#billing-identity) and the client's address, takes a date (never later than today, never before the
last invoice), gives the year's next number with no gap (`<prefix><year>-0001`), sets the due date and freezes the
seller and buyer. An issued invoice can only be marked paid or unpaid, survives its mission or report (French law keeps
invoices ten years), and reads as overdue past its due date on the dashboard and in the digest. The PDF carries the
mentions French law asks, the VAT or its exemption, and the late payment penalties (at the billing identity's rate when
set, frozen with the seller). While overdue, an invoice states what it is owed on top (`lateFees`): its amount left to
pay, the days late, the interest at its rate (amount × rate × days late / 365, none without a rate) and the €40 recovery
indemnity (art. L441-10 and D441-5 du Code de commerce).

**Currency**: an invoice is in euros, or in US dollars, pounds sterling or Swiss francs for an international client.
In another currency its draft takes the value of one unit in euros at the issue date, typed by the freelancer (the
bank's or the ECB's rate), without which it cannot be issued. The lines and totals are in that currency; the list
shows the euros beside, and the totals awaiting payment, overdue and by client are in euros. The PDF states the rate
and the amounts in euros, the VAT's included (art. 242 nonies A, annexe II du CGI); the Factur-X XML gives the
currency and the VAT in euros as well (BT-6, BT-111). The FEC entries are in euros with each line's amount in the
currency (`Montantdevise`, `Idevise`), a payment at the invoice's rate (an exchange gain or loss is the accountant's
to record), and the register gives the currency and the total in it. A credit note keeps its invoice's currency and
rate. Quotes stay in euros.

**Download as Word** gives an invoice, a credit note or a quote as a `.docx` laid out like its PDF from the same
content (`GET /api/invoices/{id}/docx`, `GET /api/quotes/{id}/docx`): the logo where the style puts it, the seller beside
the heading in the accent colour, the client, the lines, the totals, the mentions and notes, a quote's signature box and
its terms and conditions text on a new page, in the style's font, with the footer and the page number. It is written as
its XML parts, without a library. For an issued invoice it is a copy to read or archive: the invoice sent stays the PDF
(Factur-X), its number and contents frozen; a quote's terms PDF is not part of it.

**Word templates** (Profile → Billing, `GET/POST/DELETE /api/users/billing/templates[/{INVOICE|QUOTE}]`) replace that layout
with the freelancer's own: a `.docx` of 5 MB at most, without macros, checked by ClamAV, one for the invoices (credit
notes and deposit invoices too) and one for the quotes. Its placeholders (`GET /api/users/billing/templates/placeholders`)
are `{{document.title|number|issueDate|dueDate|period|validUntil|draft}}`, `{{seller.name|details|address|siret|vatNumber|iban|bic}}`,
`{{client.name|details|address|siren|vatNumber}}`, `{{totals.excludingVat|vat|toPay}}`, `{{mentions}}`, `{{notes}}`,
`{{acceptance}}` and `{{logo}}`, `{{invoice.…}}` and `{{quote.…}}` standing for `{{document.…}}`, and the part between
`{{#lines}}` and `{{/lines}}` (its table row, or its paragraphs) is repeated per line with `{{line.description|quantity|unitPrice|vatRate|amount}}`;
a part between `{{#vatExempt}}` and `{{/vatExempt}}` shows only without VAT, after `{{^vatExempt}}` only with it. The help
center's **Document templates** article (`/help/document-templates`) lists every placeholder with its meaning and an
example, in both languages; `PlaceholderGuideTest` fails when it misses one the filler knows. **Download a template to
start from** (`GET /api/users/billing/templates/{INVOICE|QUOTE}/starter?language=FR|EN`, `StarterTemplate`) writes the
built-in layout as a `.docx` in the documents' font, accent colour (the quotes' own for a quote) and footer, every
placeholder in place (the logo, the seller and client, the number and dates, a lines table row between `{{#lines}}` and
`{{/lines}}`, the totals with the VAT only `{{^vatExempt}}`, the mentions, notes and, on quotes, the acceptance and a
signature box): uploaded back as it is, it leaves nothing unknown nor missing.

A **fillable PDF form** goes in the same place instead of a `.docx` (`PdfFormTemplate`, read by its `%PDF` start): its
fields are named after the placeholders, a field `number` inside a field `invoice` or a flat `invoice_number`, a line's
`line_description_1`, `line_amount_1`… for the first row, `_2` for the second, and a check box `vatExempt`. Refused
protected, with scripts or without any field; the analysis lists the fields known, those unknown and the mentions it
lacks. **Download as Word** then fills it in Liberation Sans (the field's size kept), flattens the form and answers a
PDF (`application/pdf`, the file name ending in `.pdf`): the lines past the form's rows and the mentions it has no field
for go on a page added at the end. Like the Word copy it is a copy for the freelancer; the invoice sent stays the
built-in Factur-X PDF.
Word splits a typed placeholder across runs: a paragraph holding one is written as one run first, its breaks and tabs
kept. On upload the page shows the placeholders found, those unknown (left as written) and the mentions the template
lacks (number, date, seller, client, lines, totals, mentions), which the filled document adds at its end rather than
leaving out. **Preview** downloads it filled with the latest invoice or quote, or a sample one before the first
(`GET /api/invoices/template-preview`, `GET /api/quotes/template-preview`, 404 without a template). **Download as Word**
then fills the template; the PDF keeps the built-in layout (a PDF of the template
waits on the owner's choice of converter).

**Create a credit note** on an issued invoice drafts one with the same lines, to reduce for a partial credit; it is
issued in the same numbering and cannot credit more than what is left. Fully credited, the invoice reads **Credited**.

### Quotes

**Quotes** (`/quotes`) prepares fixed-price work (an audit, a training, a development): a draft names a client,
optionally a mission, the language, how long the offer holds (30 days by default, up to 365), an optional deposit on
order (1 to 100 %), notes and up to 50 lines. Its PDF says it has not been sent. **Send** needs a complete
[billing identity](#billing-identity) and the client's address, takes a date (never later than today), gives the year's
next quote number, apart from the invoices' (`D<year>-0001`), sets the day it holds until and freezes the seller, the
buyer and the payment term. The PDF states the VAT or its exemption, how long the offer holds, the deposit and its
amount, the payment term, the late payment penalties, that the quote is free, and a box for the client's signature
preceded by "Bon pour accord". The **Terms and conditions of sale** written in Profile → Billing (20,000 characters at
most, `quoteTerms`) follow on their own pages, the quote saying they apply; a sent quote keeps those it was sent with.
They can also be a PDF (`POST`/`DELETE /api/users/billing/quote-terms-file`, 5 MB and 20 pages at most, checked by the
platform's ClamAV, refused with a password), whose pages are appended after the quote; each upload is a new file
(`GET /api/users/billing/quote-terms-files/{id}`), so a sent quote keeps appending the one it was sent with. A sent quote is then **Accepted by the client** or **Refused by the client**, and reads
**Expired** past its validity until then. An accepted quote that asks a deposit gets **Invoice the deposit**: a draft
**deposit invoice** (facture d'acompte, Factur-X type 386) of that share of the amount before VAT, one line per VAT rate
of the quote, numbered with the other invoices once issued. **Invoice the balance** then drafts, once, the final invoice
with the quote's lines, client, mission and language, stating "Selon devis N° …", less each issued deposit invoice line
for line ("Acompte facturé N° … du …", quantity −1); it waits until the deposit invoice is issued, a credited deposit is
not taken off, and deleting the draft lets the quote be invoiced again. The page totals the sent quotes awaiting an answer and
the share of answered quotes accepted. Quotes are part of the data export.

### Expenses

**Expenses** (`/expenses`, in the menu after **Quotes**) records the business expenses of a year: the date, what for,
a category (travel, meals, equipment, software, training, office, insurance, fees, other), the amount VAT included, the
VAT it includes (never more than the amount), optionally a won mission and notes. The page totals the year, its VAT and
what the expenses cost net, by month and by category, and says what they change under the legal status of
**Profile → Billing** (`GET /api/expenses/summary/{year}`):

- **Micro-entreprise**: not deductible, the flat allowance on the turnover already counts them; they cost their full amount.
- **EI au réel, EURL, SASU, SARL, SAS**: they lower the taxable profit by their amount, less the VAT recovered when the
  seller charges VAT; they cost that much times the profile's reversion rate (55 % when none is set), the rest being
  charges and tax not paid.
- **Portage**: the portage company reimburses them as professional expenses out of the turnover, before charges; they
  cost that much times the reversion rate.
- **No status**: only recorded, with a link to set it.

Expenses are part of the data export and go with the account.

### Billing Identity

**Profile → Billing** holds the seller's legal name and form, SIRET, VAT number and rate (20 % by default) or VAT
exemption (franchise en base, the default), a company's capital and registration, the billing address, IBAN and BIC,
the payment term (30 days by default, 60 at most), the invoice prefix (`F` by default) and the late-payment interest rate
the freelancer's terms state (0 to 100 % a year; left empty, the PDFs state the ECB rate plus 10 points without a figure). An IBAN with wrong check
digits, a SIRET without 14 digits or a malformed VAT number or BIC is refused. Invoices can be issued once a SIRET, an
address, an IBAN and, when VAT applies, a VAT number are set. Under portage the portage company invoices, so Indezy
does not. An **Accent colour** (`accentColor`, #RRGGBB, refused under a 3 to 1 contrast on white as too pale to read or
print) draws the title, the table header and the rules of invoices, credit notes and quotes; it is part of the seller
frozen at issue, so an issued document keeps the colour it had. A **Logo** (`POST`/`DELETE /api/users/billing/logo`, a
PNG or JPEG of 2 MB at most, drawn anew at most 600 pixels on its longest side) is printed on the left above the seller,
or centred or on the right above both the seller and the title (`logoPlacement`), small, medium or large (`logoSize`:
110 by 38, 160 by 56 or 220 by 80 points at most), both frozen at issue; each upload is a new logo (`GET /api/users/billing/logos/{id}`), so issued documents, which keep the id
they were issued with, still print theirs. Logos go with the workspace. The **Font of invoices and quotes** (`font`) is Liberation Sans or Liberation Serif, both
bundled under the SIL Open Font Licence and embedded as Factur-X needs, frozen at issue, or the workspace's own:
**Upload my font** takes a TrueType file of 5 MB at most and optionally its bold face (`POST /api/users/billing/font`,
`file` and `bold`), refused when its OpenType outlines are CFF (PDFBox embeds TrueType ones), when its licence flags
forbid embedding (OS/2 `fsType` restricted or bitmap-only) or when it lacks letters a French invoice prints (accents,
€, guillemets); it becomes the documents' font (`font` CUSTOM, `fontFileId`, `fontFamily`), the regular face printing
the bold text when no bold one was given. Each upload is a new file, frozen with the seller at issue; **Remove** (`DELETE
/api/users/billing/font`) goes back to Liberation Sans, and issued documents keep theirs. Word copies name its family.
A **Letterhead** (`POST /api/users/billing/letterhead`, a PDF of 5 MB at most) is drawn under every page of the invoices
and quotes, its first page under a document's first page and its second (or the first again) under the others, never
under a quote's appended terms PDF; it is refused unless it has one or two upright A4 pages, no password, no script or
action, and every font embedded (so that Factur-X stays PDF/A), and it is checked by ClamAV. Each upload is a new file
(`GET /api/users/billing/letterheads/{id}`), frozen with the seller at issue (`letterheadId`); `DELETE` stops using it.
The document is drawn in its usual place, or within **Room for its header** and **Room for its footer** when set (0 to
120 mm, `letterheadTop` and `letterheadBottom`, never less than the usual 14 mm, the footer line and page numbers just
above the room kept at the bottom), frozen with the seller at issue: a letterhead leaves the rest of the page free.
The **Layout** (`theme`, frozen at issue) is **Simple** (black on white, the logo above the seller), **Modern** (a band of
the accent colour, or a dark slate without one, across the top with the logo and the title in white, the table header
and the amount to pay filled with it) or **Classic** (Liberation Serif unless a font was uploaded, the logo centred, a
thin rule under the header and above the footer, every other line shaded and the bank details framed). Word copies keep
the built-in layout without the theme. **Preview** draws the first page of an invoice in the style the form holds, saved
or not, as an image (`POST /api/invoices/style-preview` with the form's identity, PNG): the latest invoice's client and
lines, or a sample's before the first, numbered and dated as if issued today; the stored logo, font and letterhead are
used, the form setting the rest. **Quotes have their own colour and layout** gives the quotes a **Quotes' layout** and a
**Quotes' accent colour** (`quoteTheme`, `quoteAccentColor`, refused too pale like the other; null for the invoices'
ones), frozen with the quote's seller when it is sent, everything else shared; **Preview a quote** draws the preview in
them (`?quote=true`). A **Line under your name** (`header`, 150 characters: a tagline or contact details) is
printed under the seller's name, frozen at issue too. A **Footer** (`footer`, 200 characters, such
as the website and phone) is written small at the foot of every page, also frozen at issue; every document of several
pages (invoices, quotes, activity reports, dossiers) numbers them "1/2" at the bottom right. The **Notes a new invoice
starts with** and **Notes a new quote starts with** (`invoiceNotes`, `quoteNotes`, 2,000 characters) fill the notes of
a draft created without any, by hand or from an activity report; credit notes keep their own.

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
  details (RIB), identity or other (with a label), with its dates, as a PDF, PNG or JPEG of 50 MB at most (the type read
  from the content). Without an expiry, an URSSAF certificate expires six months after issue and a registration
  extract three. Up to 30 documents.
- Documents expired or expiring within 30 days show on the dashboard (**Documents to renew**).
- **Share** picks a recipient, documents and a duration (7, 14 or 30 days); up to 20 links can be open. The link is
  shown once, its openings are counted, and **Revoke** closes it. The shared page needs no account.

### Training Record

The Documents page also keeps the **trainings** followed or planned: title and provider, dates, hours and cost, the
certification obtained with its end (valid, expiring within 30 days, expired, or without end, as documents are), and the
funding asked to the training fund (FIF PL for liberal professions, AGEFICE for traders, an OPCO or another body) with
its status (to request, requested, accepted, refused, paid), the fund's deadline for the request and the amount funded.
The deadline is typed from the fund's rules of the year, which Indezy does not encode. A request still to send within 14
days of its deadline, or past it, is shown in red and listed in the dashboard's reminders; valid certifications appear on
the [public profile](#public-profile). `GET`, `POST`, `PUT` and `DELETE` on `/api/trainings` keep them, 200 per
workspace, and the account export carries them.

## Dashboard And Analytics

The dashboard is scoped by the [season](#job-hunting-seasons) picker and switches to the [Kanban](#kanban-and-pipeline-view)
mode. **Customize** shows, hides and orders its six blocks, saved with the account; **Usual layout** resets them.

- **Key figures**: opportunities, average rate, estimated revenue of accepted missions (218 working days a year when a
  mission states none, up to the day it ended), the part still to come, active opportunities, and the target rate
  against the accepted missions' average.
- **Charts**: opportunities by stage and work mode, and the negotiated rates.
- **Reminders**: interviews this week, overdue invoices, follow-ups due, stale opportunities, missions ending within six
  weeks, order renewals within the notice period (30 days by default), purchase orders near their ceiling, documents to
  renew, contacts without activity for six months, and "on this day" anniversaries. Each renewal also shows the current
  daily rate and the market median of the mission's first skill in the offer feed, with **Ask for the new rate**, which
  opens the opportunity's email on the built-in **Rate review at renewal** template.
- **Stages to update**: an exchange from a contact, recorded on an open opportunity in the last 21 days and not before
  the opportunity last changed, whose words turn it down ("non retenu", "pas donner suite", "poste pourvu", "not
  selected", "unfortunately"...) suggests **Move to Abandoned**, and one asking for an interview ("entretien", "visio",
  "disponibilités", "interview"...) while it is at first contact suggests **Move to Interview**; one per opportunity,
  from its latest such exchange, with the words quoted. **Leave it** hides it on that device (`statusSuggestions` in the
  dashboard's stats, words only, no AI).
- **This week's goal** (the bench planner), first among the reminders while the running mission ending soonest ends
  within 12 weeks, or between missions: when it ends, how many opportunities to add this week (8 within 4 weeks or
  between missions, 5 within 8, 3 beyond, two fewer for each at the interview or offer stage, at least 1), those added
  since Monday against it, the opportunities under way, and how long nothing was added once it is a week or more; with
  **Find offers**, **Add an opportunity** and **Announce my availability (AI)** (`POST /api/ai/availability`): a
  LinkedIn post and a shorter network post from the profile and the day after the mission ends (else the profile's
  availability date), editable, to copy, the network one kept as a network draft on request; nothing is posted. Below,
  **Email my ESN contacts** works without AI: see [Emails sent from Indezy](#emails-sent-from-indezy). A running
  open-ended mission shows none.
- **Recommended for you** (**New offers** without profile skills): the five unseen offers matching the profile best (the five newest without skills), with a matching profile ranked in one list with the missions members pass on that fit it best (35 or more, not in the pipeline, the network's **Best match first**): each by its score, an offer's moved by up to 5 points by its rate against the market median (a fifth of the gap), five at most; a mission opens its post.
- **Analytics**: [trends](#trends), outcomes and refusal reasons, source ROI, conversion funnels (overall and by source,
  client type and ESN), asked against obtained rates by year, bench time with its cost, time to signature per client or
  ESN, skills in demand and the year's prospection activity.
- **Recent opportunities**.

**Prospection activity** shows the last year as GitHub does: a tile a day (opportunities created and interview steps
held), a column a week from Monday, the months above and Monday, Wednesday and Friday beside, shaded in five steps, with
the year's total, the days with activity, the longest run of active days and the busiest day. A tile grows on hover or
focus and tells its day: the date, how many opportunities and interviews, and the first five of them with their client;
a click (or Enter) keeps its card open, each opportunity a click away, and Escape closes it. The arrows move a day up or
down and a week left or right, Home and End to the first and last day. The tiles share the card's width; on a phone the
year scrolls, opening on the latest weeks, the weekdays staying in view.

### Trends

**Last 90 days** compares opportunities added, interviews held, missions won and their average rate with the 90 days
before, in percent with the earlier value, for the selected season.

The analytics also show a **Commute** card once a trip is known (the driving list of
[Commute-Time Sorting](#commute-time-sorting)): the average monthly hours there and back of the open opportunities and
of the won missions, the share of days remote across both, and, when both exist, the average daily rate of the missions
over 20 hours a month against the shorter ones.

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

An opportunity's **Place of work** (optional, up to 255 characters, shown on its page) is where its commute goes instead
of the client's address: a campus, La Défense, an ESN's premises. An offer added from the feed or from its link fills it
in with the offer's postal code and town (`92400 Courbevoie`).

The opportunity page shows a **Commute** card: the trip one way by car and by public transport with its distance,
off-peak and at rush hour, the month's time there and back, and what each remote day a week gives back on the quickest
way (`Each remote day a week gives you back 13.3 h a month.`), from `GET /api/commute/project/{id}`. A fully remote
mission shows its trips alone, saying no day on site is counted: it costs nothing a month and sorts first. Each Kanban
card carries its place with the distance (`Montrouge · ≈ 56 km`) and a chip with its drive off-peak then at rush hour
(`≈ 50 min – 1 h 45`), read from the same sorted list.

With the opportunity's daily rate, each way there also shows its **effective daily rate**: the rate less, on each day on
site, the hours there and back at rush hour priced at the rate's hourly rate (7 hours a day) and the day's cost, spread
over the month's 20 working days. The cost comes from the profile: **Cost per kilometre driven** (0 to 5 €, the round
trip's kilometres) and **Monthly transport pass** (0 to 1,000 €, spread over the days on site); without them only the
hours count, and the card says so.

### Opportunity Map

**Map** on the opportunities list opens `/projects/map`: each opportunity still on the board at its place of work or its
client's address, as a pin coloured by stage around the home address, on OpenStreetMap tiles (no key, its attribution
shown). A pin's popup names the role, the client, the stage and the place, and opens the opportunity. The places are
located on the server by the same address search as the commute estimate (each address once, then kept); the page says
how many opportunities could not be placed, and links the profile when it has no home address
(`GET /api/commute/map/{freelanceId}`). The list stays the main view on a phone.

### Professional Kilometres

**Professional kilometres** on the Timesheets page downloads this year's or last year's kilometres as CSV for the
accountant to claim mileage (frais réels or indemnités kilométriques): a row per month and mission with the client, the
days on site of its validated activity report (half days counting half), the round trip by car and their product, then
a total per client (its days on site and kilometres, clients by name, those without a known route left out) and the
year's total (`GET /api/commute/kilometres/{freelanceId}/{year}?language=FR`, French decimals and headers, or `EN`). A
mission whose route is unknown keeps its days with the kilometres left blank; client names stay text in spreadsheets.
The amount on the official scale is not computed: its yearly rates wait on the owner (see TODO.md).

## Filtering And Search Surfaces

Every list filters as you type: opportunities, clients (words, type, city), contacts (words, client), sources (words,
type, usefulness) and the archive. The global search is under [Keyboard Shortcuts](#keyboard-shortcuts).

## Keyboard Shortcuts

`Alt+Shift+H` (or the account menu) shows the shortcuts: `Alt+Shift+N` new opportunity, `Alt+Shift+F` opportunities
search, `Alt+Shift+M` stage menu of the current opportunity or focused card, and `Alt+Shift+S` (or the top bar's
button) the **global search**: words of two characters or more found in one field of an opportunity, client, contact or
journal note, accents and case aside, up to 8 hits of each kind, highlighted; while the member network is on, a member
also finds members (by what their card shows), posts (among the latest 1,000) and their own messages, but those of
members blocked either way, each opening the member's page, the post's or the conversation. In the offer feed, `J` / `K` move between
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
- **Moderation**: the member network's reports, decided as [Member Network](#member-network) tells.
- **Features**: turns the **Offer feed**, **Public profiles**, **Data import**, **Yearly review** or **Member network**
  off or on for every account without a redeploy, within 30 seconds; a feature off leaves the menu and its API refuses
  requests. Each is on until turned off.

## Internationalization

The interface is in French and English, chosen in **Profile → Preferences** and saved with the account.

## Known Gaps

Not implemented, even where a field or a dependency hints at it:

- password reset and email verification for local accounts, two-factor recovery codes and social login;
- push notifications and a mobile app (TODO.md, Needs Input);
- mailbox (Gmail, Outlook), LinkedIn and calendar sync;
- AI parsing, ranking or recommendations;
- sending invoices through an e-invoicing platform, and pushing entries into an accounting tool;
- qualified electronic signature of activity reports.

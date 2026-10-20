# ADR 0009: Collect Freelance Offers From Job Boards In The Backend

- Status: Accepted
- Date: 2026-10-19

## Context

French IT freelancers watch a dozen job boards for missions. Indezy shows them all in one feed (see
[Features](../features.md#freelance-offer-feed)), so it has to read those boards: none offers a partner API, a few
expose the JSON their own pages use, others only HTML. The boards differ in what they publish (rates as numbers or
as text, dates or none, permanent jobs mixed with freelance missions), and some forbid crawlers or require a sign-in.
The server runs as one replica with 1 GiB of memory on a shared node.

## Decision

- Read the boards in the backend, in the `scraping` package: one `OfferReader` per site turns its pages into
  `ScrapedOffer`s, `OfferNormalizer` checks and completes them, `OfferStore` saves each page, and `OfferCollector`
  runs the readers one site at a time from `OfferScheduler` (every 5 minutes it reads the sites that are due; each
  site has its own refresh interval in `OfferSite`).
- Read a site's own structured data before its HTML: its public JSON API (Free-Work, Cherry Pick, LeHibou, Yeets), the
  page's framework data (Collective.work's `__NEXT_DATA__`, LittleBig Connection's React Server Components payload),
  then sitemaps with schema.org `JobPosting` markup (WorkDispo, Next Mondays, Mindquest) and finally HTML (Silkhom,
  LeGratin, Freelance-Informatique, Espace Freelance, Beyond Skills IT, Koïno, Sixième Homme), parsed with jsoup
  (MIT license, `org.jsoup:jsoup`).
- Leave out listings that look generated rather than published by people (Free-Work's Digistrat, Propulse IT and
  Omicrone), whatever their markers say.
- For a site whose list does not describe its offers, read only the pages of offers not stored yet, a few per run
  under the page budget, and remember the offers set aside for 30 days (`offer_skips`) so they are not read again.
- Keep freelance missions only, by each site's own marker (a contract type, a mission type or a filter parameter),
  and leave out offers whose title names a CDI or CDD job and no freelance mission, which some sites mark as
  freelance; permanent and fixed-term jobs never enter the feed.
- Go through `SiteHttpClient` for every request: fixed site domains only (redirects included), `robots.txt` honoured
  for the `IndezyBot` token, at least 2 seconds between two requests to a host, an honest user agent, a 30-second
  timeout and an 8 MB response cap.
- Leave out sites that forbid crawling, hide missions behind a sign-in or a payment, or no longer exist; the
  [TODO](../../TODO.md) lists each one and why.
- Claim each site with a conditional `UPDATE` on `offer_site_statuses`, so a second replica never reads it at the
  same time, and back off a failing site (its interval doubled per failure, a day at most).
- Place offers with the embedded list of French departments and regions and, for coordinates, the French government
  geocoding API (`geo.api.gouv.fr`, no key), cached per place in `geo_places`.

## Rationale

Reading the data the sites already publish for their own pages is far more stable than parsing their layout, and
gives exact dates and rates. A backend job keeps every workspace on the same offers, collected once, and keeps the
user's browser out of third-party requests. Politeness rules (robots.txt, a delay, an identified user agent) keep
the load on each board lower than one person browsing it.

## Consequences

- The backend makes outbound HTTPS requests to the boards and to `geo.api.gouv.fr`; egress must stay open for the
  server (it is today: the namespace's network policy limits ingress only).
- A site changing its API or markup breaks only its reader: the failure is recorded on the site's status, the other
  sites keep running, and the reader's test, built on a saved sample of the site's answer, shows what to adapt.
- Offers are shared data (`job_offers`); what a workspace does with them is in `job_offer_states`, removed with the
  offer by the nightly retention unless the workspace saved or imported it.
- Collection is off in tests and in the one-off schema-migration and seed runs (`indezy.offers.collection-enabled`).

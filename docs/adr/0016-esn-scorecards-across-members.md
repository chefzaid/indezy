# ADR 0016: ESN Scorecards Across Members, Named Once Five Freelancers Back Them

- Status: Accepted
- Date: 2026-11-23
- Amends: [ADR 0014](./0014-aggregated-market-data.md) (no shared figure names an ESN)

## Context

Each workspace already has a private scorecard per company (`CompanyScorecardService`): its offer rate, process length,
the ESN's margin, its contacts' answer time and its payment delay, from the workspace's own records. A scorecard across
members (how an ESN treats freelancers in general) is useful only if it names the ESN, which ADR 0014 forbade for every
shared figure. The owner decided (TODO.md, Needs Input **ESN scorecard across members**, 2026-11-23): name ESNs, which
are companies and not people, in figures five freelancers or more back, the rest of ADR 0014 unchanged, with a setting in
the profile to opt out of contributing.

## Decision

- An ESN (a company of a workspace marked as an ESN, matched across workspaces by its folded name, as the feed's
  publishers are) gets a scorecard across members: the median offer rate, process length, answer time and payment delay,
  and the margin's middle half (25th to 75th percentile), `GET /api/network/esn-scorecards` (the most backed first, 50 at
  most) and `GET /api/clients/{id}/scorecard/members` (the workspace's own company), for the network's members only.
- A freelancer counts once per ESN: their private scorecard of it, the record with the most opportunities when the
  company was entered twice; an ESN with which they had no opportunity does not count.
- The ESN is listed once 5 freelancers back it, and each figure shows only when 5 of them give it; below, only how many
  freelancers do. No figure names a freelancer, a client or a contact.
- Contributing is on unless turned off: `users.share_esn_records` (null reads as on), the switch **Count my records in
  the ESN scorecards** in Profile → Privacy (`PUT /api/users/preferences`, `shareEsnRecords`). Turning it off takes the
  account's records out at once: every figure is computed from the workspaces as they are when it is asked.
- Members are told before their records are read: What's new, the help (network and clients) and the switch's hint say
  what counts, from the release that brings it.

## Rationale

- The figures describe companies' practices, not people; five freelancers per figure keeps one member's record from
  being read back out of a median, as for the rate benchmark.
- An opt-out, rather than ADR 0014's opt-in, is the owner's choice for records about ESNs, which carry no personal rate:
  the member keeps the switch and turning it off takes effect at once.

## Consequences

- The rate benchmark keeps its opt-in (`share_rates`): the two switches are separate.
- The scorecards read every contributing workspace on each request; with many members they would move to a nightly
  aggregate, kept only above the threshold, like the benchmark.
- ESNs entered under different names (abbreviations, legal suffixes) count apart until their folded names match.

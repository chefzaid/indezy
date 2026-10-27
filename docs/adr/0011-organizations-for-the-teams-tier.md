# ADR 0011: Organizations For The Teams Tier

- Status: Proposed (waiting on the owner's answer in TODO.md, Needs Input "Teams tier: organizations")
- Date: 2026-10-27

## Context

The product direction plans a Teams tier for collectives, portage companies and boutique ESNs: a consultant
availability board, a shared opportunity pool, organization dashboards (occupancy, bench, margins, renewals, CRA and
invoice status), single sign-on and a white-label option (TODO.md, Teams Tier). Today every record belongs to one
workspace (`Freelance`), and `AccessGuard` lets only its account read it: nothing is shared with anyone but through a
link the freelancer creates.

An organization reading a consultant's data changes that rule, so what crosses the boundary must be decided before any
of the tier is built.

## Proposed Decision

- An **organization** has members, each with a role: **owner** (billing, members, settings), **manager** (dispatches
  opportunities, reads the shared data) and **consultant** (a freelancer whose own workspace stays theirs). A person
  joins by accepting an invitation sent to their email, and can leave at any time.
- A consultant's workspace stays **private by default**. Joining shares, explicitly and as listed on the invitation:
  - their availability (date and status) and their profile's skills, for the availability board;
  - the opportunities the organization dispatched to them, with their stage, rate and steps;
  - for the missions placed through the organization, their activity reports' and invoices' status (not their content).
  Anything else (their own opportunities, clients, contacts, notes, rates, documents, offers) is shared only when the
  consultant marks one item as shared with the organization, and unshared at any time.
- The organization's **opportunity pool** belongs to the organization: its clients and contacts are the organization's,
  never copied from a consultant's workspace. Dispatching one to a consultant creates a linked opportunity in their
  workspace; the pool marks a client already submitted through another member, so two members are not sent to the same
  client for one need.
- Organization pages read through their own endpoints and DTOs (never a workspace's), checked by an organization scope
  added to `AccessGuard`; a consultant leaving stops every share at once, the organization keeping the history of its
  own pool.
- Single sign-on goes through Keycloak identity brokering per organization, and white-label (logo, colors, domain)
  comes after, both priced with the tier (TODO.md, Needs Input "Plans, prices and payment provider").

## Alternatives Considered

- **Shared by default** (everything a consultant does visible to managers): simplest for the organization, but turns a
  freelancer's private pipeline into a reporting tool for their ESN, against the product's "freelancer-owned data".
- **Separate accounts per organization** (a consultant has one workspace per organization): no boundary to manage, but
  a freelancer's search splits across workspaces, which is what Indezy is meant to unify.

## Consequences

- Every query of the tier carries both scopes (the workspace's and the organization's), and the tests must prove a
  manager reads nothing unshared, as `TwoAccountsIntegrationTest` does for workspaces today.
- The data export, erasure and retention rules extend to the organization's own records, and a data processing
  agreement is needed for organizations (TODO.md, Commercial and Enterprise Foundation).

## Questions For The Owner

1. Is the boundary above right: availability, skills and dispatched work shared on joining, everything else per item?
2. Who are the first organizations: collectives of freelancers (peers), portage companies or ESNs? It decides whether
   margins (an ESN's view) belong in the dashboards.
3. Can a consultant belong to several organizations at once?

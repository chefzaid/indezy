# ADR 0014: Aggregated Market Data From Members Who Opt In, Five Freelancers Or More Per Figure

- Status: Accepted (amended by [ADR 0016](./0016-esn-scorecards-across-members.md): ESN scorecards name the ESN, contributing on unless turned off)
- Date: 2026-11-21

## Context

The network can tell freelancers what their peers charge (a daily rate benchmark by skill, seniority and region) and,
later, how ESNs behave (a scorecard) or how the market moves (a quarterly report). These figures come from what members
record in their workspaces: the rates of the missions they won, the processes they went through. A member's rates and
clients are personal and commercial data; the owner set the rules (TODO.md, Needs Input, **Aggregated market data
consent**, 2026-11-20): opt-in only, a figure published once 5 or more freelancers back it, never a client's or a
contact's identity.

## Decision

- Contributing is an opt-in per account, off by default: `users.share_rates`, the switch **Share my rates with the
  benchmark** in Profile → Privacy (`PUT /api/users/preferences`). Turning it off takes the account's figures out at once:
  nothing is copied, every figure is computed from the workspaces as they are when it is asked.
- A freelancer counts once per skill: the daily rate of their latest won mission naming the skill (its tech stack) in the
  last 24 months, with its seniority on the skill (from the years the profile gives: under 3 junior, under 8 confirmed,
  under 15 senior, else expert) and its region (remote, else Île-de-France or the other regions by the postal code of the
  place of work or of the client).
- A figure (median, 25th and 75th percentiles, `GET /api/network/benchmark`) is shown only when 5 freelancers or more back
  it; below, only how many do. The skills listed (`/api/network/benchmark/skills`) are those 5 or more back. Only the
  network's members read it.
- No figure names a freelancer, a client, an ESN or a contact, and none is given for fewer than five freelancers, however
  the filters narrow it.

## Rationale

- Consent (GDPR art. 6(1)(a)) is the clearest basis for sharing commercial data with peers, and the switch withdraws it
  as simply as it gives it.
- Five freelancers per figure keeps one member's rate from being read back out of a median, while staying reachable for
  common skills; a figure is computed on demand, so no aggregate outlives a withdrawal or an account's deletion.
- Counting a freelancer once per skill keeps a member with many missions from weighing more than others.

## Consequences

- Rare skills, narrow seniorities and regions show no figure until enough members share; the card says how many back
  the choice.
- The ESN scorecard and the quarterly market report follow the same rules when they are built.
- The benchmark reads the workspaces of the accounts that opted in on each request; with many members it would move to a
  nightly aggregate, kept only above the threshold.

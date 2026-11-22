# ADR 0015: Claude Through Indezy's Key Within Monthly Limits, Or The Member's Own

- Status: Accepted
- Date: 2026-11-22

## Context

Many planned features need a language model: reading a job posting into an opportunity, drafting messages, weighing an
opportunity against the profile, adapting a CV, an assistant per opportunity. The owner chose (TODO.md, Needs Input,
**AI provider and costs**, 2026-11-20) an Indezy key in Vault, Anthropic first, with per-plan limits and a monthly
spending cap by default, plus an optional bring-your-own key that lifts the limits. Plans are not decided yet.

## Decision

- Claude through the official Java SDK (`com.anthropic:anthropic-java`), each task on the cheapest model that does it
  well (2026-11-22): Haiku 5.5 to read postings and triage offers, Sonnet 5.5 to write messages, tailor dossiers and suggest rates, Opus
  5.5 to weigh opportunities and hold conversations; `INDEZY_AI_MODEL` puts them all on one model, `indezy.ai.models`
  changes one task's, and a member's own key runs every task on its own model unless they choose one. Each feature asking for a structured answer (a JSON schema from a Java record) at the lowest effort
  that serves it, with the server-side refusal fallback on (`fallbacks: "default"`).
- Indezy's key (`INDEZY_AI_ANTHROPIC_API_KEY`, from Vault) serves every account within a monthly allowance in US dollars
  (`INDEZY_AI_ACCOUNT_MONTHLY_CAP_USD`, 10 by default: the free tier, the same for every account until paid plans, which will allow more), and all accounts
  together within a monthly cap (`INDEZY_AI_MONTHLY_CAP_USD`, 100 by default). Each request's tokens and cost are counted
  in `ai_usage` by account, month (Paris time), feature and key, from the response's usage and the model's list price.
- A member may set their own Anthropic key (Profile → Preferences → AI) and choose Opus, Sonnet or Haiku 5.5: their
  requests then go through it, billed to them, without Indezy's limits. The key is stored encrypted (AES-256-GCM, a key
  derived from the server's secret) and never shown again.
- What is sent is the member's own text and the opportunity it is about; nothing of other members. A feature shows what
  the AI wrote for the member to check and change; nothing is saved or sent on its own.

## Rationale

- One provider and one SDK keep the code small; structured answers avoid parsing free text.
- Dollar limits follow the real cost whatever the model or the feature; the global cap bounds Indezy's bill even if many
  accounts use their allowance; the member's own key keeps heavy users unlimited at no cost to Indezy.

## Consequences

- Until the key is in Vault, the features say AI is not available, except for members with their own key.
- Per-plan limits replace the single allowance once plans exist; a local model (privacy) and other providers stay open.

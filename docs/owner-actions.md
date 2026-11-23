# Owner Actions

What only the owner can do for Indezy: secrets to store, facts to give, and accounts or settings outside this
repository. Each entry says what it unblocks, the exact steps, and how to check it worked. The coding agent keeps this
page in step with TODO.md's **Needs Input**; once an action is done, tell the agent (or tick it in TODO.md) and it
removes the entry.

## 1. Turn on the AI features (Anthropic key)

**Unblocks:** filling an opportunity from a pasted posting, drafting messages, weighing an opportunity and the
opportunity chat for every member ([ADR 0015](./adr/0015-ai-provider.md)). Until then those features say AI is not
available, except for members who set their own key in Profile → Preferences → AI.

**Limits (free tier):** 10 US dollars per account and month, 100 for all accounts together. Paid plans will allow more.
To change them, set `INDEZY_AI_ACCOUNT_MONTHLY_CAP_USD` and `INDEZY_AI_MONTHLY_CAP_USD` on the server (see
[Deployment](./deployment.md#ai-key)).

1. Sign in at <https://console.anthropic.com> with the account that will pay for Indezy's AI.
2. Optional but advised: **Settings → Workspaces → Create workspace** named `indezy`, then set its **monthly spend limit**
   to at least the global cap (100 USD), so a bug can never cost more than that.
3. **Settings → API keys → Create key**, in that workspace, named `indezy-production`. Copy the key (`sk-ant-...`): it is
   shown once.
4. Store it in Vault (KV v2), path `secret/apps/indezy/ai`, field `anthropic_api_key`:
   ```bash
   vault kv put secret/apps/indezy/ai anthropic_api_key='sk-ant-...'
   ```
   (or in the Vault UI: **secret → Create secret**, path `apps/indezy/ai`, key `anthropic_api_key`).
5. Tell the coding agent the key is in Vault: it adds the `indezy-ai` ExternalSecret to `infra/k8s/external-secrets.yaml`
   (the YAML is in [Deployment](./deployment.md#ai-key); adding it before step 4 would leave Argo CD degraded), and
   the next release restarts the server with the key.

**Check:** Profile → Preferences → AI reads "AI features use Indezy's allowance" with "This month: $0.00 of $10.00".

## 2. Turn on web push (VAPID keys)

**Unblocks:** Profile → Notifications → **Notifications on this device**, the network's notifications and new messages
pushed to members' browsers. Until then the switch says push is not available yet.

1. On any machine with OpenSSL, generate the key pair and print both keys:
   ```bash
   openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem
   openssl ec -in vapid.pem -pubout -outform DER 2>/dev/null | tail -c 65 | base64 | tr '/+' '_-' | tr -d '=\n'; echo
   openssl ec -in vapid.pem -outform DER 2>/dev/null | tail -c +8 | head -c 32 | base64 | tr '/+' '_-' | tr -d '=\n'; echo
   shred -u vapid.pem
   ```
   The first line printed is the public key, the second the private key.
2. Store them in Vault, path `secret/apps/indezy/push`:
   ```bash
   vault kv put secret/apps/indezy/push vapid_public_key='B...' vapid_private_key='...'
   ```
3. Tell the coding agent: it adds the `indezy-push` ExternalSecret ([Deployment](./deployment.md#web-push-keys)).

Never change the pair afterwards unless it leaked: every browser would have to turn push on again.

**Check:** the switch can be turned on; the browser asks to allow notifications.

## 3. Give the 2026 social contribution rates

**Unblocks:** the official rates table per year and status (URSSAF estimates, declaration calendar), which each member
can override. The coding environment cannot reach urssaf.fr or service-public.gouv.fr, and other sources disagree on
one rate.

Reply in TODO.md (**Social contribution rates**) or to the agent with the 2026 rates as a percentage of turnover for a
micro-entreprise, with their source:

- services BIC (artisanal and commercial services): expected 21.2 %;
- liberal professions under the SSI (BNC): **25.6 % or 26.1 %?** (sources disagree);
- regulated liberal professions under the CIPAV: expected 23.2 %;
- the vocational training contribution (CFP) per activity, and the versement libératoire rates if you want them shown.

Or allow `www.urssaf.fr` for the coding sessions, and the agent reads them itself.

## 4. Complete the legal texts

**Unblocks:** a legal notice that meets the LCEN, and final terms and privacy policy.

Edit `indezy-web/src/assets/legal/fr/notice.md` (and `en/`), or give the agent these facts, which are marked
`[à compléter]`: the publisher's legal form, share capital, registered address, SIREN/RCS, VAT number and telephone
number, and the courts' city. Then check each `[à vérifier]` mark (data retention periods, whether a data protection
officer is named).

## 5. GitLab pipeline schedules

**Unblocks:** the nightly end-to-end run and the weekly check that the job boards are still readable.

In GitLab, project `swirl/indezy` → **Build → Pipeline schedules → New schedule**, on branch `main`, time zone
Europe/Paris:

1. Description `Nightly E2E`, interval `30 2 * * *`, variable `PLAYWRIGHT_SCHEDULE` = `true`.
2. Description `Weekly reader canary`, interval `0 6 * * 1`, variable `READER_CANARY` = `true` (skip it if it exists).

**Check:** each schedule shows a next run; the first run's pipeline appears under **Build → Pipelines**.

## 6. Decisions still open

Each waits on a word from you in TODO.md's **Needs Input**:

- **Google, Microsoft, Dropbox and LinkedIn sign-in and sync:** which providers first, and whether you will register
  their OAuth clients (the ids and secrets would go to Vault).
- **Paid compliance providers:** which e-signature provider, e-invoicing platform (PDP) and bank data provider, with
  accounts you open.
- **Keycloak:** the sender address for the realm's emails (Verify email, Forgot password), for Indezy's own realm with
  its logo, as decided.
- **Reply detection for emails sent from Indezy:** whether to route answers through a `replies.swirlit.dev` subdomain
  (its MX record to Resend's inbound service, the inbound webhook's signing secret in Vault) so Indezy marks an email
  answered and forwards the answer, or keep answers going straight to the member, marked by hand.
- **Browser extension:** whether to publish it in the Chrome Web Store (a one-time developer fee, under your name or
  company).
- **ESN scorecard across members:** whether shared figures may name an ESN (a company) when 5 freelancers or more back
  them, which amends ADR 0014; or only market-wide figures; or the private scorecard each workspace already has.
- **Template designer canvas:** keep the header's and footer's three zones, or build a free canvas for the PDF only, or
  for both the PDF and the Word copy (floating text boxes).

# Deployment Guide

Indezy deploys to the application-neutral K3s platform managed by [`swirl-cloud`](https://github.com/chefzaid/swirl-cloud). The platform provides the GitLab runner, Argo CD, Vault, External Secrets, registry, ingress, PostgreSQL and observability services. This repository owns all Indezy-specific project, secret, delivery and runtime configuration.

## Infrastructure Layout

| Path | Responsibility |
|---|---|
| `.gitlab-ci.yml` | delivery pipeline |
| `infra/onboarding.json` | onboarding contract read by the platform's `add-repos.sh` |
| `infra/argocd/application.yaml` | the single Argo CD `Application` |
| `infra/k8s/` | desired state; `kustomization.yaml` is the entry point and holds the image tags |
| `infra/overlays/ha/` | optional multi-node profile |
| `infra/ansible/` | optional manual reconciliation of the pushed GitOps state (`site.yaml`, `inventory.ini`) |
| `infra/compose/compose.yaml` | local PostgreSQL, pgAdmin (`admin` profile) and the dev container |
| `infra/database/init/` | local PostgreSQL initialization |
| `infra/scripts/` | CI, release and GitLab configuration scripts, and the local helpers behind `maskfile.md` |

Production images are built from `indezy-server/Dockerfile.runtime` and `indezy-web/Dockerfile.runtime`. Names use lowercase kebab-case and YAML files end in `.yaml`; Kubernetes files use the component name, and hook jobs end in `-job.yaml`.

## Runtime And Ownership

The public endpoint is `https://indezy.swirlit.dev`. Application resources run in `apps`; Argo CD and shared services run in `infra`; CI pods run in `gitlab-runners`.

`infra/k8s/ingress.yaml` declares three Ingresses on that host:

| Ingress | Path | Backend | Middlewares |
|---|---|---|---|
| `indezy-ingress` | `/` | `indezy-web` | upload limit |
| `indezy-api-ingress` | `/api` | `indezy-server` | `indezy-sso` ForwardAuth, upload limit |
| `indezy-public-api-ingress` | `/api/public` | `indezy-server` | upload limit |

The `indezy-sso` Middleware sends requests to the shared Keycloak OAuth2 Proxy, preserves login return URLs and backend `401`s, and forwards the signed access token. `/api/public` skips it: shared links are opened by people without an account, and each request is authorized by its own token (see [Security](./security.md#activity-report-validation-links)). Traefik prefers the longer path. The same file owns the 16 MiB request-body limit and the `indezy-backend` ServersTransport (120 seconds for response headers). TLS uses the cluster's `swirlit-dev-tls` Secret; connection timeouts and HTTP-to-HTTPS redirects are platform settings. `indezy-ingress` publishes Indezy in the Homepage `Applications - PROD` group.

The Keycloak issuer, internal JWKS URI and required audience live in `infra/k8s/server.yaml`; identity-provider secrets stay platform-owned.

The backend connects to `postgres.swirlit.internal:5432/indezy` as `indezy_user`. The `indezy-db-setup` Argo CD sync hook creates or updates that role and database, sets the role's planner settings and enables `pg_trgm` before the Deployments roll out. The hook and the server's `wait-for-db` init container use the public PostgreSQL 18.6 Alpine image pinned by digest in `kustomization.yaml`, without registry credentials.

Network policy admits Traefik, Prometheus, CI job pods and pods in `apps` to both services. The backend reads the job boards, `geo.api.gouv.fr` and Resend over HTTPS, so its egress policy admits DNS (`kube-system/kube-dns`), PostgreSQL (`app: postgres`, or `cnpg.io/cluster: postgres-ha` under the platform's HA profile) and Keycloak (`app: keycloak`) in `infra`, and every public address; private, link-local and carrier-grade NAT ranges are refused, so a new in-cluster dependency needs its own rule.

Background work runs inside the server and is turned off by environment variable, without a new image:

| Work | Schedule | Off switch |
|---|---|---|
| offer collection, one site at a time, each site claimed in the database | every 5 minutes | `INDEZY_OFFERS_COLLECTION_ENABLED=false` |
| geocoding of offers (otherwise placed by postal code or department) | with the collection | `INDEZY_OFFERS_GEOCODING_ENABLED=false` |
| archiving idle opportunities of accounts that turned it on | 03:30 Paris time | `INDEZY_PROJECTS_ARCHIVING_ENABLED=false` |
| anonymizing dormant contacts of accounts with a retention period | 03:45 Paris time | `INDEZY_CONTACTS_RETENTION_ENABLED=false` |
| all scheduled work at once, the above plus webhook deliveries, digests, saved search emails and session clean-up | | `INDEZY_SCHEDULING_ENABLED=false` |

## Secrets

External Secrets projects these Vault KV paths into `apps` (`infra/k8s/external-secrets.yaml`):

| Vault KV path | Kubernetes Secret | Keys | Purpose |
|---|---|---|---|
| `apps/indezy/runtime` | `indezy-server-secret` | `DB_PASSWORD`, `JWT_SECRET` (from `database_password`, `jwt_secret`) | database password and JWT signing secret |
| `apps/indezy/registry` | `indezy-registry-auth` | `registry`, `username`, `password` (rendered as a docker config) | read-only registry pull credential |
| `apps/indezy/mail` | `indezy-mail` | `RESEND_API_KEY` | Resend key of the saved search emails; empty sends nothing |
| `infra/postgres` | `indezy-db-admin-credentials` | `POSTGRES_ADMIN_USER`, `POSTGRES_ADMIN_PASSWORD` | shared administrator, used only by the database setup hook |

Onboarding generates missing `apps/indezy/runtime` fields (24 random bytes for the database password and 48 for the JWT secret, hex encoded) and keeps existing values. Never commit plaintext or base64-encoded secret values. `GOOGLE_MAPS_API_KEY` (commute times) is not projected; add it to the contract before enabling the feature in production.

## Administrators

The admin console (`/admin`) opens to the accounts whose emails `INDEZY_ADMIN_EMAILS` lists, comma-separated; the
server reads it from the `indezy-server-config` ConfigMap (`infra/k8s/server.yaml`) at startup. It is empty by default,
so nobody is an administrator: set it in Git, let Argo CD sync and restart the server Deployment. The account must
exist (register or sign in through Keycloak once with that email).

## Saved search email credentials

Saved search emails go through Resend, from `Indezy <noreply@swirlit.dev>` (`INDEZY_MAIL_FROM`), with links to
`https://indezy.swirlit.dev` (`INDEZY_PUBLIC_URL`). `swirlit.dev` is already verified in Resend for the website; create
a sending access key restricted to it for Indezy, separate from the website's, and store it as the `RESEND_API_KEY`
field at Vault KV v2 path `secret/apps/indezy/mail`. The server reads the `indezy-mail` Secret through an optional
`secretKeyRef`.

The field must exist, even empty, or the ExternalSecret fails to sync, Argo CD reports the application degraded and the
deploy job's health check fails. Onboarding seeds it empty when missing and keeps an existing value. With an empty key
the server sends nothing and the saved search dialog says emails are not set up. After adding or rotating the key, wait
for the ExternalSecret to be Ready and restart the server Deployment, which reads it at startup.

Resend accepting an email means accepted for delivery, not delivered; its logs show bounces. Each email carries an
idempotency key (`saved-search/<id>/<cutoff>`), so a retry within Resend's 24 hours sends it once.

## Delivery Pipeline

`.gitlab-ci.yml` runs a branch pipeline, or a merge request pipeline when the branch has an open merge request. Jobs run on the platform's instance runner (tag `swirl-cloud`) as unprivileged Kubernetes pods.

| Job | Stage | Runs | What it does |
|---|---|---|---|
| `01-build` | build | every pipeline | required: validates the manifests and onboarding contract, packages the JAR (with SpotBugs and Checkstyle) and the production web archive |
| `02-test` | build | every pipeline | allowed to fail: backend and frontend tests, lint and the coverage policy |
| `03-package` | build | every pipeline except Sonar scan-only | required: builds both runtime images with Kaniko, without pushing |
| `01-e2e` | verify | manual | allowed to fail: the Playwright suite |
| `02-quality` | verify | automatic on the default branch and in full mode, manual otherwise | allowed to fail: Sonar analysis from the test artifacts |
| `03-security` | verify | automatic in full mode, manual otherwise | allowed to fail: Trivy scan of dependencies, IaC and secrets, failing on high or critical findings |
| `01-release` | release | default branch, manual with confirmation | publishes the release (below) |
| `02-deploy` | release | default branch, after `01-release` | deploys it (below) |
| `set-major-version` | version | default branch, manual | prepares `<NEW_MAJOR_VERSION>.0.0` and synchronizes the Maven and npm manifests |

`PIPELINE_MODE=full` runs quality and security automatically, and a pipeline started from the GitLab UI in full mode on the default branch also releases and deploys without the manual step; E2E stays manual. The schedules set `PLAYWRIGHT_SCHEDULE=true` (build, tests, package and the [nightly E2E suite](./testing.md#scheduled-e2e-runs), nothing after) or `READER_CANARY=true` (the [reader canaries](./testing.md#reader-canaries) only).

`01-release` needs only `01-build` and `03-package`, and:

1. builds and pushes `registry.swirlit.dev/swirlit/indezy/indezy-server:<version>` and `indezy-web:<version>` with Kaniko, reusing registry-backed layer caches for 30 days;
2. uploads the JAR, the web archive and `SHA256SUMS` to the Generic Package Registry;
3. refuses to continue when the default branch moved past the pipeline's commit;
4. commits `release: <version> [skip ci]` with the version and the image tags in `infra/k8s/kustomization.yaml`, and tags it `v<version>`;
5. commits `chore: prepare <next minor version> [skip ci]`, pushes both commits and the tag, and creates a GitLab Release linking the packages.

`02-deploy` applies `infra/argocd/application.yaml`, requests a hard refresh, waits up to 15 minutes for the pushed revision to be `Synced` and `Healthy`, then checks the backend health endpoint and the frontend's health and index page inside the cluster.

`VERSION` holds the base version; each first-parent commit after the one that last changed it adds one to the patch, so releasing `1.17.3` prepares `1.18.0`. Release, deploy and `set-major-version` share the `indezy-production` resource group. Argo CD, not CI, creates the namespace, reconciles, prunes and self-heals.

GitLab keeps test, coverage, browser, quality, security and release artifacts for seven days; Maven, npm, Sonar and Trivy caches and a cached Kaniko executor avoid repeated downloads. The SonarQube project keeps the long-lived quality history.

### Automatic Sonar analysis

`.sonar-auto.json` declares the contract the platform uses to request a scan when analysis is missing or older than 24 hours: a default-branch pipeline with `SONAR_SCAN_ONLY=true`, which runs only `01-build`, `02-test` and `02-quality`. The platform provisions the Sonar project and the masked `SONAR_TOKEN`. A submission failure fails the quality job visibly, without affecting delivery.

## Add or reconfigure this repository

Run `./add-repos.sh` from the platform checkout and select this repository.
The platform reads [infra/onboarding.json](../infra/onboarding.json), prompts
for the public subdomain (`@` selects the zone apex), and provisions the declared
service requests without executing repository administrator scripts.

The contract declares the files to render, registry access, Vault fields, DNS
hostname and readiness checks. Onboarding commits the rendered files with its
saved choices in `infra/onboarding-values.json`, which the first run creates.
Rerunning it changes the host, ingress, backend CORS, identity endpoints, image
paths and delivery settings together; resource names, database and role names
and Vault paths stay the same. It also configures two-way GitHub/GitLab
synchronization through `.github/workflows/sync-gitlab.yml`, which runs on every
GitHub push and, through a repository-dispatch webhook, on every GitLab push; it
fast-forwards the side behind, merges divergent branches without force pushing,
refuses conflicting tag rewrites; its monthly schedule rotates the managed
GitLab token before it expires.

Public DNS is declared in `infra/onboarding.json` (`dns.hosts`) and reconciled
by the platform; see its [application DNS ownership](https://github.com/chefzaid/swirl-cloud/blob/main/docs/dns.md#application-dns-ownership).

After provisioning, the platform starts an API pipeline for the exact pushed
configuration. `APP_ONBOARDING=true` lets that pipeline release automatically;
`ONBOARDING_EXPECTED_SHA` must match before building or publishing, and
deployment requires the release to descend from that commit and remain the
default branch tip. The publication records `Onboarding-Pipeline` and
`Onboarding-Source` commit trailers so a repeat run can find the existing
release. A failed deployment can retry that pipeline's deploy job; if
publication fails after its Git push, repair the failed publication step before
retrying, since the guard refuses to publish another release from the old
checkout.

`01-build` runs the offline onboarding checks (Python, PyYAML, Git and kubectl):

```sh
python3 infra/scripts/test-onboarding.py
```

## Standalone GitLab bootstrap

`infra/scripts/configure-gitlab.sh` is a fallback for setting the project up without the platform's onboarding. It needs:

- the instance runner online with tag `swirl-cloud`, and healthy GitLab, Argo CD, Vault, External Secrets and registry
- `kubectl`, `curl`, `git`, `jq`, `openssl`, `python3`, `libsodium` and `sudo` on the control-plane host
- `GITLAB_ADMIN_TOKEN` able to manage `swirlit/indezy`, and `GITHUB_ADMIN_TOKEN` able to manage Actions secrets and dispatch workflows for `chefzaid/indezy`

```bash
GITLAB_ADMIN_TOKEN=<gitlab-token> \
GITHUB_ADMIN_TOKEN=<github-token> \
  ./infra/scripts/configure-gitlab.sh
```

It creates or updates the GitLab project and its settings (runner, job-token pushes, `main` protection, labels, badges, the Sonar binding and `SONAR_TOKEN`), the GitHub/GitLab synchronization, the registry deploy token and the `apps/indezy/registry` and `apps/indezy/runtime` Vault values, then applies the Argo CD `Application`. It does not seed `apps/indezy/mail` or reconcile DNS.

## Validation And Manual Reconciliation

Before pushing infrastructure changes:

```bash
kubectl kustomize infra/k8s >/dev/null
kubectl kustomize infra/overlays/ha >/dev/null
kubectl apply --dry-run=client --validate=false \
  -f infra/argocd/application.yaml >/dev/null
```

The Ansible entry point applies the same `Application`, requests a hard refresh, waits for a new `Synced`/`Healthy` reconciliation and checks both rollouts. It deploys the pushed `main` revision, never local manifests:

```bash
ansible-playbook -i infra/ansible/inventory.ini infra/ansible/site.yaml
```

## Rollback

Revert or change the image-tag commit on `main` and let Argo CD sync it. Do not patch live Deployments: self-healing restores Git's desired state. Post-rollout checks are in [Operations](./operations.md#first-checks-after-a-rollout).

## Multi-node HA profile

`infra/overlays/ha` runs two API and two web replicas across at least two eligible
hostnames, with a disruption budget for each Deployment and rolling updates
that keep an available replica. Keep `infra/k8s` for the current single-host
installation. PostgreSQL, Keycloak and ingress must also survive a host failure;
this overlay does not replicate the shared database.

To opt in, persist `spec.source.path: infra/overlays/ha` in
`infra/argocd/application.yaml`, then reconcile that Application; release and
bootstrap helpers reapply this file, so a live-only override does not last.
Image updates stay in `infra/k8s/kustomization.yaml`; the overlay inherits them
and copies the API image to its schema Job.

The profile serializes schema updates before the API revision starts. After the
database setup hook, `indezy-schema-migration` takes a PostgreSQL advisory lock
before Spring initializes Hibernate and applies schema changes on that same
connection; a startup or DDL failure fails the hook, and closing the connection
releases the lock. The migration-only process binds HTTP to loopback, has no
Service and exits. API replicas use `ddl-auto=validate` and wait in the next
Argo sync wave.

This reuses Hibernate's schema update, not a versioned migration system. Back up
the database and test schema changes against a copy first; they must stay
compatible with the old API revision during rollout, and breaking or destructive
changes need an explicit migration plan.

The API uses shared JWT credentials and needs no sticky sessions. Login failure
counters stay per process, so more replicas do not give a distributed account
lockout. Readiness includes database connectivity; liveness checks only
application state, so a database failover does not restart every API. Validate
authentication across replicas, concurrent writes and optimistic locking, a
database outage and a node drain before accepting the profile.

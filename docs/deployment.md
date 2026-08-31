# Deployment Guide

Indezy deploys to the application-neutral K3s platform managed by [`bm-cluster`](https://github.com/chefzaid/bm-cluster). The platform provides generic GitLab runner, Argo CD, Vault, External Secrets, registry, ingress, PostgreSQL, and observability services. This repository owns all Indezy-specific project, secret, delivery, and runtime configuration.

## Infrastructure Layout

Indezy follows the shared application-repository convention used by DevApp and Thoughty:

| Directory | Responsibility |
|---|---|
| `infra/ansible/` | optional manual reconciliation of committed GitOps state |
| `infra/argocd/` | the single Argo CD `Application` bootstrap at `application.yaml` |
| `infra/compose/` | local development and production-shaped profiles |
| `infra/database/` | ordered local database initialization scripts |
| `infra/k8s/` | application-owned Kubernetes desired state |
| `infra/scripts/` | configuration, health-check, and local lifecycle helpers |

Names use lowercase kebab-case. YAML files use `.yaml`; the Compose base is `compose.yaml`; Kubernetes workload files use the logical component name because each may contain more than one resource kind. Hook jobs end in `-job.yaml` and the aggregate entry point remains `kustomization.yaml`.

Production entry points:

- Argo CD: `infra/argocd/application.yaml`
- Kubernetes: `infra/k8s/kustomization.yaml`
- GitLab bootstrap: `infra/scripts/configure-gitlab.sh`
- immutable image-tag update: `infra/scripts/set-image-tags.sh`

Local entry points:

- Compose: `infra/compose/compose.yaml`
- Ansible: `infra/ansible/site.yaml` with `infra/ansible/inventory.ini`
- PostgreSQL initialization: `infra/database/init/`
- backend health helper: `infra/scripts/check-backend-health.sh`

Shared platform resources remain in `bm-cluster`.

## Runtime And Ownership

The public endpoint is `https://indezy.swirlit.dev`. NGINX Ingress routes `/api` to the Spring Boot service and `/` to the Angular/NGINX service. The same Ingress publishes Indezy in the cluster Homepage `Applications` group and protects both routes with the shared Keycloak OAuth2 Proxy. The non-secret issuer, internal JWKS URI, and required audience live in `infra/k8s/server.yaml`; identity-provider secrets remain platform-owned.

| Concern | Repository resource |
|---|---|
| GitLab pipeline | `.gitlab-ci.yml` |
| Argo CD application | `infra/argocd/application.yaml` |
| aggregate desired state | `infra/k8s/kustomization.yaml` |
| runtime and registry secrets | `infra/k8s/external-secrets.yaml` |
| database provisioning | `infra/k8s/database-setup-job.yaml` |
| application services and ingress | `infra/k8s/server.yaml`, `web.yaml`, and `ingress.yaml` |

Application resources run in `apps`; Argo CD and shared services run in `infra`; disposable CI pods run in `gitlab-runners`.

The backend connects to `postgres.swirlit.internal:5432/indezy` as `indezy_user`. An Argo CD sync hook creates or updates that role and database before the Deployments roll out. The public ingress uses the cluster's `swirlit-dev-tls` Secret.

## Secrets

External Secrets projects these Vault contracts into `apps`:

| Vault KV path | Purpose |
|---|---|
| `apps/indezy/runtime` | database password and JWT signing secret |
| `apps/indezy/registry` | read-only private-registry credential |
| `infra/postgres` | shared administrator used only by the database setup hook |

The app bootstrap generates strong runtime values when `apps/indezy/runtime` does not exist. Do not commit plaintext or base64-encoded secret values. An optional Google Maps server key should likewise be added to the external secret contract before the feature is enabled.

## Delivery Pipeline, Images, and Artifacts

The graph shows ordered build, test, package, E2E, quality, release, deploy, and version jobs. Tests are non-blocking and E2E is optional/manual. Standard mode leaves quality manual; `PIPELINE_MODE=full` runs independent non-blocking quality/security reporting automatically and automates release and deploy while E2E remains manual.

The release job publishes:

```text
registry.swirlit.dev/swirlit/indezy/indezy-server:<semantic-version>
registry.swirlit.dev/swirlit/indezy/indezy-web:<semantic-version>
```

Daemonless Kaniko builds each runtime Dockerfile's `production` stage and reuses 30-day registry-backed layers on unprivileged Kubernetes runners. Maven/npm/Sonar caches avoid repeated dependency downloads. GitLab retains test, coverage, browser, and compiled outputs for seven days, while releases publish versioned JAR and SPA archives plus `SHA256SUMS` immutably to the Generic Package Registry. The linked SonarQube project retains long-lived quality-gate, issue, and metric history.

## One-Time GitLab Bootstrap

Prerequisites:

- the generic instance runner is online with tag `bm-cluster`
- GitLab, Argo CD, Vault, External Secrets, and the registry are healthy
- `.gitlab-ci.yml` exists in the repository's current commit
- `kubectl`, `curl`, `git`, `jq`, `openssl`, `python3`, `libsodium`, and `sudo` are installed on the control-plane host
- `GITLAB_ADMIN_TOKEN` can manage `swirlit/indezy`
- `GITHUB_ADMIN_TOKEN` can manage Actions secrets and dispatch workflows for `chefzaid/indezy`

Run:

```bash
GITLAB_ADMIN_TOKEN=<gitlab-token> \
GITHUB_ADMIN_TOKEN=<github-token> \
  ./infra/scripts/configure-gitlab.sh
```

The app-owned script creates or updates the project, enables the instance runner and CI job-token pushes, configures bidirectional GitHub/GitLab push synchronization, creates a read-only registry deploy token, writes app-specific Vault values, and applies the Argo CD `Application`. It also reconciles project metadata, labels, merge safeguards, `main` protection, cleanup, badges, the GitLab/Sonar binding, and a masked project-scoped `SONAR_TOKEN`. It does not add Indezy configuration to `bm-cluster` or commit credentials.

Every GitHub push starts `.github/workflows/sync-gitlab.yml` directly. Every GitLab branch or tag push invokes the same workflow through the managed repository-dispatch webhook, including commits marked `[skip ci]`. The reconciler fast-forwards whichever side is behind, merges divergent branches without force pushing, and refuses conflicting tag rewrites. Its monthly schedule self-rotates the managed GitLab token into the encrypted GitHub secret before expiry.

## Delivery Flow

Normal pipelines run `01-build`, `02-test`, and `03-package` automatically and expose E2E, quality, release, and version changes manually. Full mode automates quality, release, and deploy while leaving E2E manual. Release then:

1. consumes the successful backend and frontend build artifacts;
2. publishes immutable application archives, checksums, server images, and web images using 30-day registry-backed Kaniko caching;
3. refuses to deploy if `main` advanced during the pipeline;
4. commits the semantic release version and image tags in `infra/k8s/kustomization.yaml` and creates its annotated tag;
5. prepares and commits the next minor version with patch reset to zero;
6. creates a GitLab Release linked to both packages;
7. applies and refreshes the Indezy Argo CD `Application`;
8. waits for that exact commit to become `Synced` and `Healthy`; and
9. checks both internal health endpoints.

`VERSION` starts at `1.0.0`. Each new first-parent commit advances the patch used by builds and releases. Releasing, for example, `1.0.3` prepares `1.1.0`. Set `NEW_MAJOR_VERSION` when starting a pipeline and play `set-major-version` to deliberately prepare `<major>.0.0` and synchronize Maven/npm manifests.

Production delivery is serialized through the `indezy-production` resource group. Argo CD, rather than CI, owns namespace creation, reconciliation, pruning, and self-healing.

## Validation And Bootstrap

Before pushing:

```bash
kubectl kustomize infra/k8s >/dev/null
kubectl apply --dry-run=client --validate=false -k infra/k8s >/dev/null
kubectl apply --dry-run=client --validate=false \
  -f infra/argocd/application.yaml >/dev/null
```

The normal bootstrap is performed by `configure-gitlab.sh`; applying the application directly is useful only when the GitLab project and Vault contracts already exist:

```bash
kubectl apply -f infra/argocd/application.yaml
```

For a repeatable operator-triggered refresh, the Ansible entry point applies the same Argo CD `Application`, requests a hard source refresh, waits for a new `Synced`/`Healthy` reconciliation, and verifies both Deployment rollouts. It deploys the pushed `main` revision, never uncommitted local manifests:

```bash
ansible-playbook -i infra/ansible/inventory.ini infra/ansible/site.yaml
```

## Health And Rollback

Backend health: `/api/actuator/health`

Frontend health: `/health`

```bash
kubectl get application indezy -n infra
kubectl get deployment,pod,service,ingress,externalsecret -n apps
kubectl rollout status deployment/indezy-server -n apps
kubectl rollout status deployment/indezy-web -n apps
```

Rollback by reverting or changing the desired image-tag commit on `main`. Do not patch live Deployments because Argo CD self-healing restores Git's desired state.

# Deployment Guide

Indezy deploys to the application-neutral K3s platform managed by [`bm-cluster`](https://github.com/chefzaid/bm-cluster). The platform provides generic GitLab runner, Argo CD, Vault, External Secrets, registry, ingress, PostgreSQL, and observability services. This repository owns all Indezy-specific project, secret, delivery, and runtime configuration.

## Runtime And Ownership

The public endpoint is `https://indezy.swirlit.dev`. NGINX Ingress routes `/api` to the Spring Boot service and `/` to the Angular/NGINX service.

| Concern | Repository resource |
|---|---|
| GitLab pipeline | `.gitlab-ci.yml` |
| Argo CD application | `infra/argocd/indezy-app.yaml` |
| aggregate desired state | `infra/k8s/kustomization.yaml` |
| runtime and registry secrets | `infra/k8s/indezy-secrets.yaml` |
| database provisioning | `infra/k8s/indezy-db-setup.yaml` |
| application services and ingress | `infra/k8s/indezy-*.yaml` |

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

## Images

The default-branch pipeline publishes:

```text
registry.swirlit.dev/root/indezy/indezy-server:<pipeline>-<commit>
registry.swirlit.dev/root/indezy/indezy-web:<pipeline>-<commit>
```

Kaniko builds each Dockerfile's `production` stage without a Docker socket or privileged runner.

## One-Time GitLab Bootstrap

Prerequisites:

- the generic instance runner is online with tag `bm-cluster`
- GitLab, Argo CD, Vault, External Secrets, and the registry are healthy
- `.gitlab-ci.yml` exists in the repository's current commit
- `kubectl`, `curl`, `git`, `jq`, `openssl`, and `sudo` are installed on the control-plane host
- `GITLAB_ADMIN_TOKEN` can manage `root/indezy`

Run:

```bash
GITLAB_ADMIN_TOKEN=<api-token> ./infra/scripts/configure-gitlab.sh
```

The app-owned script creates or updates the project, enables the instance runner and CI job-token pushes, creates a read-only registry deploy token, writes app-specific Vault values, and applies the Argo CD `Application`. It does not add Indezy configuration to `bm-cluster`.

## Delivery Flow

Branch and merge-request pipelines validate Kubernetes manifests and run Maven and Angular quality gates. A successful default-branch pipeline additionally:

1. publishes immutable server and web images with Kaniko;
2. refuses to deploy if `main` advanced during the pipeline;
3. updates the two image tags in `infra/k8s/kustomization.yaml`;
4. pushes a `deploy: <version> [skip ci]` desired-state commit using `CI_JOB_TOKEN`;
5. applies and refreshes the Indezy Argo CD `Application`;
6. waits for that exact commit to become `Synced` and `Healthy`; and
7. checks both internal health endpoints.

Production delivery is serialized through the `indezy-production` resource group. Argo CD, rather than CI, owns namespace creation, reconciliation, pruning, and self-healing.

## Validation And Bootstrap

Before pushing:

```bash
kubectl kustomize infra/k8s >/dev/null
kubectl apply --dry-run=client --validate=false -k infra/k8s >/dev/null
kubectl apply --dry-run=client --validate=false \
  -f infra/argocd/indezy-app.yaml >/dev/null
```

The normal bootstrap is performed by `configure-gitlab.sh`; applying the application directly is useful only when the GitLab project and Vault contracts already exist:

```bash
kubectl apply -f infra/argocd/indezy-app.yaml
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

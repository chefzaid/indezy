# Operations Runbook

This runbook covers the Indezy application layer. Use the `bm-cluster` runbooks when GitLab, Argo CD, ingress, Vault, External Secrets, PostgreSQL, or another shared service is unhealthy.

## Runtime Surfaces

| Surface | Address |
|---|---|
| application | `https://indezy.swirlit.dev` |
| GitLab project and pipelines | `https://gitlab.swirlit.dev/swirlit/indezy` |
| SonarQube quality dashboard | `https://sonarqube.swirlit.dev/dashboard?id=swirlit%3Aindezy` |
| Argo CD application | `https://argocd.swirlit.dev/applications/indezy` |
| backend service | `indezy-server.apps.svc.cluster.local:8080` |
| frontend service | `indezy-web.apps.svc.cluster.local:8080` |
| shared PostgreSQL | `postgres.swirlit.internal:5432` |
| Prometheus metrics | `indezy-server.apps.svc.cluster.local:8080/api/actuator/prometheus` |

## First Checks After A Rollout

```bash
kubectl get application indezy -n infra
kubectl get pods,services,ingress -n apps -l app=indezy-server
kubectl get pods,services -n apps -l app=indezy-web
kubectl get externalsecret indezy-server-secret indezy-registry-auth \
  indezy-db-admin-credentials -n apps
kubectl rollout status deployment/indezy-server -n apps
kubectl rollout status deployment/indezy-web -n apps
curl --fail https://indezy.swirlit.dev/health
curl --fail https://indezy.swirlit.dev/api/actuator/health
```

Healthy means the expected Git revision is `Synced` and `Healthy` in Argo CD, both Deployments have an available replica, External Secrets report `Ready=True`, and both health endpoints succeed.

## Logs

```bash
kubectl logs deployment/indezy-server -n apps --tail=200
kubectl logs deployment/indezy-web -n apps --tail=200
kubectl get pods -n apps
```

The Kubernetes profile writes Logstash-compatible JSON to stdout. Fluent Bit
automatically enriches and indexes it in Elasticsearch; use Kibana's generic
**Applications Namespace Logs** dashboard and filter `app` to `indezy-server`.
Prometheus discovers the backend pod annotations automatically, and Grafana
loads the repository-owned **Indezy Overview** dashboard from its labeled
ConfigMap.

The repository-owned **Indezy — Application Logs** Kibana dashboard is imported
with the platform-managed least-privilege dashboard bootstrap credential. It is
fixed to `indezy-server` and `indezy-web`, separates warnings/errors from the
complete recent stream, defaults to the last 24 hours, and refreshes every 30
seconds.

The database setup is an Argo CD hook and is deleted after success. During a failing sync, locate and inspect it with:

```bash
kubectl get jobs -n apps
kubectl logs job/indezy-db-setup -n apps
```

## Common Incidents

### Keycloak SSO exchange fails

An unauthenticated browser request should be sent through `https://keycloak.swirlit.dev/oauth2/start` to the `swirlit` realm. After authentication, `GET /api/auth/sso` must receive an ingress-provided access token and return an Indezy session. Check the OAuth2 Proxy and Ingress annotations first, then verify the public issuer, internal JWKS URI, and `oauth2-proxy` audience in `infra/k8s/server.yaml`. Never work around the failure by trusting identity headers without validating the signed token.

### Backend waits for PostgreSQL

```bash
kubectl logs -n apps <indezy-server-pod> -c wait-for-db
kubectl get service -n infra
kubectl get externalsecret indezy-server-secret -n apps -o yaml
```

Verify the shared PostgreSQL service, the setup hook, and that the projected application password matches the `indezy_user` role. Fix the Vault contract or hook in Git and refresh Argo CD; do not add a plaintext Secret.

### Backend is running but not ready

```bash
kubectl describe pod -n apps <indezy-server-pod>
kubectl port-forward -n apps service/indezy-server 18080:8080
curl http://127.0.0.1:18080/api/actuator/health
```

Check startup logs and database connectivity. The health endpoint will remain unavailable or report `DOWN` when a required dependency fails.

### Frontend loads but API calls fail

```bash
kubectl get ingress indezy-ingress -n apps -o yaml
kubectl get endpoints indezy-server -n apps
kubectl logs deployment/indezy-server -n apps --tail=100
```

Confirm the `/api` ingress route, ready backend endpoints, and the backend `/api` context path.

### Images published but deployment did not change

Check the `01-release` job, the `deploy: ... [skip ci]` commit, and Argo CD's revision and conditions. CI intentionally fails when `main` advanced during a pipeline instead of overwriting newer desired state.

### Argo CD reverts a manual change

This is expected with self-healing. Make the desired change in Git and let Argo CD reconcile it.

## Backup And Recovery

Indezy data is stored in the shared PostgreSQL service. Confirm the platform backup policy includes the `indezy` database and test restores before production use. Take an additional logical backup before risky schema or data work from a trusted environment that can reach PostgreSQL:

```bash
pg_dump -h postgres.swirlit.internal -U indezy_user -d indezy > indezy-backup.sql
```

Keep the dump outside Git and handle it as sensitive data.

## Secret Rotation

Rotate the application database password by updating `apps/indezy/runtime` in Vault and allowing the database setup hook to update the role before the backend rollout. Rotating `jwt_secret` invalidates active sessions. Registry pull-token rotation is managed by `infra/scripts/configure-gitlab.sh` when the stored credential is absent or the managed token no longer exists.

# Operations Runbook

This runbook covers the Indezy application layer. Use the `swirl-cloud` runbooks when GitLab, Argo CD, ingress, Vault, External Secrets, PostgreSQL, or another shared service is unhealthy.

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
Every request gets an id (`RequestIdFilter`): the caller's `X-Request-Id` when it is a plain one of up to 64
characters, else a new UUID, returned in the response's `X-Request-Id` header and written with the HTTP method on each
line the request logs (`request_id`, `method`). Filter on `request_id` to follow one request; the path is not logged,
since some carry a token.
Prometheus discovers the backend pod annotations automatically, and Grafana
loads the repository-owned **Indezy Overview** dashboard from its labeled
ConfigMap.

The offer collection exports, per job board (`site` label): `indezy_offers_runs_total`
(`outcome` = `success` or `failure`), `indezy_offers_found_total`, `indezy_offers_new_total`
and `indezy_offers_run_duration_seconds`, counted by the replica that ran the site, and,
read from the shared site statuses at most once a minute, `indezy_offers_site_consecutive_failures`,
`indezy_offers_site_last_found` and `indezy_offers_site_last_success_age_seconds`;
`indezy_offers_geocoding_timeouts_total` counts the place lookups that failed. The
**Indezy Overview** dashboard shows the failed runs in a row and what the last run found per
site, the new offers per hour and the runs and geocoding timeouts per hour. The platform's
Prometheus rules (`swirl-cloud`, group `applications.indezy`, owner `application`) alert when a
site fails three runs in a row (`IndezyOfferSiteFailing`), when every successful reading of a
site over 24 hours found nothing (`IndezyOfferSiteFindsNothing`, a sign its pages changed) and
when more than 20 lookups to `geo.api.gouv.fr` fail in an hour (`IndezyGeocodingTimeouts`);
their runbooks are in `swirl-cloud`'s `docs/guides/alert-runbooks.md`.

The API's exception handler logs rejected requests (4xx) at `WARN`, naming only
the fields, parameters or database constraint involved (or, for another request
error such as an unsupported `Content-Type`, only the exception type and
status: `Rejected request: HttpMediaTypeNotSupportedException (415)`), and
unexpected server faults at `ERROR` with their stack trace; a client that left
before its response was written (a closed tab: `Broken pipe`) is no fault, and is
logged at `DEBUG` only. Rejected values are
not logged (see [Security › Logging](./security.md#logging)). Failed logins,
lockouts and duplicate registrations are logged at `WARN` without the email: to
spot a brute-force attempt, count the `Login rejected: invalid credentials` and
`Login blocked after too many failed attempts` lines over time.

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

An unauthenticated browser API request should be redirected to Keycloak's `swirlit` realm, preserving its original URL for return after login. After authentication, `GET /api/auth/sso` must receive an ingress-provided access token and return an Indezy session. Check the OAuth2 Proxy and the app-owned ForwardAuth Middleware first, then verify the public issuer, internal JWKS URI, and `oauth2-proxy` audience in `infra/k8s/server.yaml`. Never work around the failure by trusting identity headers without validating the signed token.

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

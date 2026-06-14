# Operations Runbook

This runbook covers day-to-day checks and first-response troubleshooting for the deployed Indezy application.

## Runtime Surfaces

Production URL:

```text
https://indezy.swirlit.dev
```

Kubernetes namespace:

```text
application
```

Core workloads:

- `indezy-db-setup` PreSync job
- `indezy-server` deployment and service
- `indezy-web` deployment and service
- `indezy-ingress` ingress

External dependencies:

- Cloudflare DNS and proxy
- Nginx Ingress Controller
- shared PostgreSQL in `infra`
- Nexus registry at `nexus.swirlit.dev:5000`
- Jenkins at `https://jenkins.swirlit.dev`
- ArgoCD at `https://argocd.swirlit.dev`
- optional Google Maps Distance Matrix API

## First Checks After A Rollout

1. Check ArgoCD sync status.

```bash
kubectl -n infra get application indezy
```

2. Check pods.

```bash
kubectl -n application get pods -o wide
```

3. Check services and ingress.

```bash
kubectl -n application get svc
kubectl -n application get ingress
```

4. Check rollout status.

```bash
kubectl -n application rollout status deploy/indezy-server
kubectl -n application rollout status deploy/indezy-web
```

5. Smoke test from outside the cluster.

```bash
curl -I https://indezy.swirlit.dev
curl -I https://indezy.swirlit.dev/api/swagger-ui.html
```

## What Good Looks Like

- ArgoCD application is synced and healthy.
- `indezy-server` has one ready replica.
- `indezy-web` has one ready replica.
- ingress has host `indezy.swirlit.dev`.
- frontend `/health` returns success.
- backend health endpoint `/api/actuator/health` (Spring Boot Actuator) returns `{"status":"UP"}`.
- login page loads in the browser.
- authenticated dashboard loads after login.
- project, client, contact, and source lists load without API errors.

## Logs

Backend:

```bash
kubectl -n application logs deploy/indezy-server --tail=200
kubectl -n application logs deploy/indezy-server -f
```

Frontend:

```bash
kubectl -n application logs deploy/indezy-web --tail=200
kubectl -n application logs deploy/indezy-web -f
```

Database setup job:

```bash
kubectl -n application logs job/indezy-db-setup
```

Describe failing resources:

```bash
kubectl -n application describe pod <pod-name>
kubectl -n application describe ingress indezy-ingress
kubectl -n application describe job indezy-db-setup
```

## Common Incidents

### Backend pod waits for database

Symptoms:

- pod stuck during init
- logs repeat "Waiting for infrastructure postgres"

Checks:

```bash
kubectl -n application logs <server-pod> -c wait-for-db
kubectl -n infra get svc | findstr postgres
```

Likely causes:

- shared PostgreSQL is down
- service DNS changed
- `indezy_user` or `indezy` database was not created
- secret passwords no longer match

Recovery:

1. Verify shared PostgreSQL.
2. Check `indezy-db-setup` job logs.
3. Confirm `indezy-server-secret.DB_PASSWORD` matches the role password.
4. Re-sync ArgoCD after fixing secrets or setup job.

### Backend readiness fails

Symptoms:

- pod running but not ready
- readiness probe fails on `/api/actuator/health`

Checks:

```bash
kubectl -n application describe pod <server-pod>
kubectl -n application port-forward svc/indezy-server 8080:8080
curl http://localhost:8080/api/actuator/health
```

Likely cause:

- the app failed to start or a dependency it reports on (e.g. the database) is down, so `/api/actuator/health` returns `DOWN` or never comes up.

Recovery:

1. Check the pod logs for startup failures; confirm the database is reachable.
2. Rebuild image if the actuator dependency or `management.*` config was changed.
3. Let Jenkins update manifests and ArgoCD sync.

### Frontend serves but API calls fail

Symptoms:

- login page loads
- API calls return 404, 502, 503, or CORS-like browser errors

Checks:

```bash
kubectl -n application get ingress indezy-ingress -o yaml
curl -I https://indezy.swirlit.dev/api/swagger-ui.html
kubectl -n application logs deploy/indezy-server --tail=100
```

Likely causes:

- ingress `/api` path not routing to backend
- backend service not ready
- frontend production API URL changed away from `/api`
- backend context path changed away from `/api`

### ArgoCD keeps reverting manual changes

ArgoCD self-heal is enabled. Manual Kubernetes edits are not durable.

Recovery:

1. Make the change in Git.
2. Push to `main`.
3. Let Jenkins or ArgoCD sync the desired state.

### Jenkins pushed images but deployment did not change

Checks:

```bash
git log --oneline -5
Select-String -Path deployments\indezy-server.yaml -Pattern "image:"
Select-String -Path deployments\indezy-web.yaml -Pattern "image:"
```

Likely causes:

- Jenkins failed before the manifest commit.
- Git credentials could not push.
- ArgoCD has not synced the new commit.

## Backup And Recovery

Current state:

- application data is in shared PostgreSQL
- versioned migrations are not established yet
- automated DB backup and point-in-time recovery are backlog items

Operational expectation:

- make sure the infrastructure PostgreSQL backup policy covers the `indezy` database
- test restore procedures before production use
- document restore time objective and restore point objective once the app stores real user data

Manual safety backup before risky operations:

```bash
pg_dump -h postgres.infra.svc.cluster.local -U indezy_user -d indezy > indezy-backup.sql
```

Run that from an environment that can reach the cluster database and has the right credentials.

## Secret Rotation

Rotate:

- database password
- JWT secret
- infrastructure PostgreSQL admin password
- Google Maps API key if configured

Database password rotation must update:

- PostgreSQL role password
- `indezy-server-secret.DB_PASSWORD`
- database setup job expectations

JWT secret rotation invalidates existing tokens. Plan this as a user-visible session reset.

## Scaling Notes

Current deployment replicas:

- backend: 1
- frontend: 1

Before increasing backend replicas, verify:

- application is stateless between requests
- JWT validation does not depend on local memory
- database connection pool is configured for multiple pods
- migrations are safe under concurrent startup
- session records, if activated, handle multi-pod behavior

## Related Guides

- [Deployment](./deployment.md)
- [Security](./security.md)
- [Testing](./testing.md)
- [ADR Index](./adr/README.md)

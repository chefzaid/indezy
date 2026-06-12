# ADR 0004: GitOps Deployment Through Jenkins And ArgoCD

- Status: Accepted
- Date: 2026-06-12

## Context

Indezy targets the `ds-cluster` platform, which already provides Jenkins, ArgoCD, Nexus, K3s, Nginx Ingress, and shared PostgreSQL.

The repository contains:

- Dockerfiles for backend and frontend
- Kubernetes manifests under `deployments/`
- ArgoCD bootstrap manifest under `argocd/`
- Jenkins pipeline that builds images and updates manifests

## Decision

Use Jenkins to build and publish images, then update Kubernetes manifests in Git. Use ArgoCD to sync the desired state from Git into the cluster.

## Rationale

This keeps deployment state auditable in Git.

Jenkins handles build-time responsibilities and registry authentication.

ArgoCD handles runtime reconciliation, self-healing, pruning, and namespace creation.

## Consequences

Manual edits in Kubernetes are temporary because ArgoCD self-heal is enabled.

Manifest updates must be reviewed like code because they are the deployment source of truth.

Jenkins credentials for Git and Nexus are critical operational secrets.

The current pipeline should grow a stronger verification gate before image publication.

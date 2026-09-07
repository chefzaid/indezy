# ADR 0004: GitOps Deployment Through GitLab CI And ArgoCD

- Status: Accepted
- Date: 2026-06-12

The CI topology, artifact retention, and image-builder portions of this decision are superseded by [ADR 0007](./0007-explicit-delivery-jobs.md). Its repository ownership and GitOps decisions remain active.

## Context

Indezy targets the application-neutral `bm-cluster` platform, which provides a generic GitLab instance runner, Argo CD, GitLab Container Registry, K3s, NGINX Ingress, Vault, External Secrets, and shared PostgreSQL.

The repository contains:

- Dockerfiles for backend and frontend
- Kubernetes manifests under `infra/k8s/`
- ArgoCD bootstrap manifest under `infra/argocd/`
- GitLab CI pipeline that builds images and updates manifests

## Decision

Keep all Indezy-specific bootstrap and desired state in this repository. Use GitLab CI to verify code, retain short-lived job artifacts, publish immutable Generic Packages and images, and update only Kustomize image tags in Git. Reuse persistent dependency and registry-backed image-layer caches. Use Argo CD to sync the exact desired revision into the cluster.

## Rationale

This keeps deployment state auditable in Git.

GitLab CI handles build-time responsibilities with built-in job and registry credentials.

ArgoCD handles runtime reconciliation, self-healing, pruning, and namespace creation.

## Consequences

Manual edits in Kubernetes are temporary because ArgoCD self-heal is enabled.

Manifest updates must be reviewed like code because they are the deployment source of truth.

The cluster registry pull credential is a read-only project deploy token stored in Vault and projected by External Secrets.

The pipeline rejects stale desired-state updates and verifies the exact Argo CD revision after rollout.

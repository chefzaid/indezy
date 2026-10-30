# ADR 0007: Use Explicit Delivery Jobs and Non-Blocking Verification

- Status: Accepted
- Date: 2026-08-29

## Context

Indezy needs automatic code feedback, explicit control over browser and production work, durable quality reporting, reproducible GitLab configuration, and downloadable releases in addition to images.

## Decision

- Expose ordered `01-build`, `02-test`, `03-package`, `01-e2e`, `02-quality`, `03-security`, `01-release`, `02-deploy`, and manual `set-major-version` jobs. Numeric prefixes preserve the intended order in GitLab's alphabetically sorted stage boxes. Scheduled [reader canary](../testing.md#reader-canaries) and [Playwright](../testing.md#scheduled-e2e-runs) pipelines skip the reports, release, deploy and version jobs.
- Put required compilation in `01-build`, with the Checkstyle and SpotBugs gates: `package` checks Checkstyle and the job runs `spotbugs:check` after it, so a warning or a finding fails the build and release is unavailable. Put optional lint, unit tests and coverage in `02-test`, and required daemonless image validation in `03-package`.
- Keep optional manual Playwright in `01-e2e`; expose dependency/Sonar reporting through non-blocking `02-quality`, and digest-pinned Trivy dependency/IaC/secret reporting through independent non-blocking `03-security`. Quality runs automatically on the default branch and is manual elsewhere in standard mode; security is manual in standard mode. No verify job is a release dependency.
- Make `01-release` require compiled artifacts and successful package validation; `02-deploy` requires successful release.
- Let `PIPELINE_MODE=full` run quality and security automatically and automate release and deploy; E2E remains optional and manual.
- Let the platform's scheduled Sonar analysis run only build, test and quality: `.sonar-auto.json` declares the `SONAR_SCAN_ONLY=true` contract, under which package, E2E, security, release, deploy and version jobs are absent. A failed scanner submission fails the non-blocking quality job visibly; quality gates do not authorize release.
- Publish JUnit, coverage, build, and browser outputs as seven-day GitLab job artifacts.
- Build with daemonless Kaniko and 30-day registry-backed image-layer caches on unprivileged runners.
- Publish versioned, checksummed JAR and SPA archives to the Generic Package Registry and link them from a GitLab Release.
- Keep deployment GitOps-driven and verify the exact Argo CD revision.
- Own semantic version state in `VERSION`: commits advance patch, releases prepare the next minor, and the manual `set-major-version` action prepares `<major>.0.0` while synchronizing Maven and npm manifests.
- Keep CI, Sonar, CODEOWNERS, contribution templates, and idempotent GitLab/Sonar bootstrap configuration in the app repository. Keep credentials only in Vault and masked CI variables.

## Consequences

- Every change receives automatic compilation with the Checkstyle and SpotBugs gates, test/coverage feedback, and image validation; default-branch quality reporting is automatic, security reporting is manual in standard mode and automatic in full mode; tests remain non-blocking and browser load stays manual.
- GitLab displays a failed coverage-policy test without turning that policy into a delivery gate; SonarQube remains the durable code-quality dashboard.
- Release is structurally independent of E2E, Sonar, and audit status; deploy is structurally dependent on release status.
- GitLab Community Edition lacks some paid governance features; bootstrap configures the strongest available controls without pretending those features exist.
- Release reuses successful build outputs and is independent of quality-report status.

## Amendments

- 2026-09-07: default-branch `02-quality` became automatic, with the `SONAR_SCAN_ONLY` contract for the platform's scheduled analysis.
- 2026-10-23: `01-build` runs `spotbugs:check`, so SpotBugs blocks delivery as Checkstyle does; it adds about 11 seconds to the job.

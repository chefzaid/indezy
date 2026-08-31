# Indezy

Indezy is a full-stack web application that helps freelancers manage job applications, track project opportunities, and maintain organized dashboards. It is built for the French tech market and is designed to streamline freelance workflows with stats and visualization, AI analysis and insights, cloud sync, job-offer scraping, and more.

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-22.0.1-red.svg)](https://angular.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6.0.3-blue.svg)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE)

## GitLab Delivery

- [Run a pipeline](https://gitlab.swirlit.dev/swirlit/indezy/-/pipelines/new?ref=main)
- [Pipelines and delivery jobs](https://gitlab.swirlit.dev/swirlit/indezy/-/pipelines)
- [Versioned application packages](https://gitlab.swirlit.dev/swirlit/indezy/-/packages)
- [Container images](https://gitlab.swirlit.dev/swirlit/indezy/-/container_registry)
- [Releases](https://gitlab.swirlit.dev/swirlit/indezy/-/releases)

GitLab exposes `build`, `verify`, `release`, and `version` stages. Their jobs are ordered as `01-build`, `02-test`, `03-package`; `01-e2e`, `02-quality`, `03-security`; `01-release`, `02-deploy`; and `set-major-version`. Build and package are required; tests and their 80 percent coverage rule are non-blocking. Standard mode leaves E2E, quality, security, and release manual. `PIPELINE_MODE=full` runs non-blocking quality and Trivy security reporting automatically and automates release and deploy, while E2E remains manual.

Application versions start at `1.0.0` and are owned by [`VERSION`](./VERSION). Each new commit advances the patch component for its build (`1.0.1`, `1.0.2`, ...). A successful release tags and deploys that exact version, then prepares the next minor cycle (`1.1.0`, `1.2.0`, ...). To change the major version, start a pipeline with `NEW_MAJOR_VERSION` set to the desired integer and play `set-major-version`; it prepares `<major>.0.0` and synchronizes the Maven and npm manifests.

## Documentation

- [Features](./docs/features.md)
- [Architecture Overview and ADR Index](./docs/architecture.md)
- [Data Model Reference](./docs/data-model.md)
- [Development Guide](./docs/development.md)
- [Testing Guide](./docs/testing.md)
- [Deployment Guide](./docs/deployment.md)
- [Operations Runbook](./docs/operations.md)
- [Security Reference](./docs/security.md)
- [Infrastructure Layout](./docs/deployment.md#infrastructure-layout)

## Roadmap

The feature backlog and roadmap live in [TODO.md](./TODO.md).

## Quick Start

```bash
mask install
mask db-reset
mask run
```

`mask db-reset` prints the sample account credentials after rebuilding the local database.

Useful commands:

```bash
mask test
mask build
mask status
mask logs
mask stop
```

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE).

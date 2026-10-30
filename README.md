# Indezy

Indezy helps freelancers in the French tech market run their mission search: a feed of the freelance missions published on the main French IT job boards, filtered by daily rate, location and remote work, opportunities on a five-stage Kanban board (Contact, Tests & Entretiens, Offre, Refusé, Accepté), each refusal saying whether you or the recruiter refused, job-hunting seasons with their own dashboards, clients and ESNs with their contacts, interview steps, rates and margins, reminders and analytics, and once a mission is signed, its monthly activity reports (CRA), approved by the client or ESN through a link and printed as PDF in their template, then its invoices (Factur-X, with credit notes and the accountant's export). A browser extension adds the offer being read on any site. It is available in French and English. AI assistance and more job boards are on the [roadmap](./TODO.md).

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-22.0.1-red.svg)](https://angular.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6.0.3-blue.svg)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE)

## GitLab Delivery

Use the platform's `add-repos.sh` entry point to install or reconfigure this
repository through its [declarative onboarding contract](docs/deployment.md#add-or-reconfigure-this-repository).
This repository owns its hostname, runtime secrets, database setup and delivery configuration.

- [Run a pipeline](https://gitlab.swirlit.dev/swirlit/indezy/-/pipelines/new?ref=main)
- [Pipelines and delivery jobs](https://gitlab.swirlit.dev/swirlit/indezy/-/pipelines)
- [Versioned application packages](https://gitlab.swirlit.dev/swirlit/indezy/-/packages)
- [Container images](https://gitlab.swirlit.dev/swirlit/indezy/-/container_registry)
- [Releases](https://gitlab.swirlit.dev/swirlit/indezy/-/releases)

Build and package are required; tests, E2E, quality and security reports never block a release. Versions come from
[`VERSION`](./VERSION). The [Deployment Guide](docs/deployment.md) describes the pipeline, releases and versioning.

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

In production: [indezy.swirlit.dev](https://indezy.swirlit.dev).

The feature backlog and roadmap live in [TODO.md](./TODO.md).

## Quick Start

```bash
mask install
mask db-reset
mask run
```

`mask db-reset` prints the sample account's credentials. The [Development Guide](./docs/development.md) covers setup,
and `mask --help` lists the other commands.

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE).

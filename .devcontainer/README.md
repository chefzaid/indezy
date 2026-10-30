# Indezy Development Container

A development environment for Indezy in VS Code (or any Dev Containers client): the tools to build and test both
applications, next to a PostgreSQL database and pgAdmin, started from `infra/compose/compose.yaml`. The
[Development Guide](../docs/development.md) covers the rest of local development.

## What's Included

- **Development container** (`devcontainer` service): built from `.devcontainer/Dockerfile` (Ubuntu 24.04), with
  Java 25 (Temurin) and Node.js 26 from the features in `devcontainer.json`, `psql`, `pg_isready` and Git.
- **Chromium** with its system libraries, installed by Playwright, for the Karma unit tests and the Playwright
  end-to-end tests; `CHROME_BIN` points Karma at it.
- **PostgreSQL 18**, the major version production runs, initialized by `infra/database/init`; Hibernate creates the
  schema.
- **pgAdmin 4**, with the database connection configured.
- The VS Code extensions listed in `devcontainer.json` (Java, Spring Boot, Angular, ESLint, Playwright, YAML,
  SonarLint).

## Quick Start

1. Open the project in VS Code and choose **Reopen in Container**.
2. Wait for the first build and `post-create.sh` (dependencies, Chromium, a first compile): about 5 to 10 minutes.
3. Open a new terminal, so the aliases below are loaded, and start the applications.

| Alias | Runs |
|-------|------|
| `indezy-server` | the backend (`./mvnw spring-boot:run`) |
| `indezy-web` | the frontend (`npm start`) |
| `indezy-test-server` | backend tests, Checkstyle and SpotBugs (`./mvnw verify`) |
| `indezy-test-web` | frontend unit tests in headless Chromium (`npm run test:ci`) |
| `indezy-e2e` | Playwright end-to-end tests (`npm run test:e2e`) |
| `indezy-db` | `psql` on the development database |
| `cd-server`, `cd-web`, `workspace` | go to a project folder |

The container has neither [mask](../docs/development.md#setup) nor a Docker CLI; the aliases take the place of the
mask commands.

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:4200 | - |
| Backend API | http://localhost:8080/api | - |
| pgAdmin | http://localhost:5050 | admin@indezy.com / admin |
| PostgreSQL | localhost:5432 (`postgres:5432` inside the container) | indezy_user / indezy_password |

The backend runs with the `devcontainer` Spring profile (`SPRING_PROFILES_ACTIVE`), whose
`application-devcontainer.yml` points it at the `postgres` service. These credentials are for local development only.

## Troubleshooting

- **Ports in use**: 4200, 8080, 5432 and 5050 must be free on the host.
- **Database**: from the host, `docker compose -f infra/compose/compose.yaml ps` and `logs postgres` show the services;
  see the [Development Guide](../docs/development.md#docker-compose) for resetting the volume.
- **Browser tests cannot find Chrome**: open a new terminal so `CHROME_BIN` is set, or run
  `npx playwright install --with-deps chromium` in `indezy-web`.

## Updating the Container

Run **Dev Containers: Rebuild Container** from the VS Code command palette. The Maven, npm, Playwright and VS Code
extension caches are volumes, kept across rebuilds; `post-create.sh` adds its aliases to `~/.bashrc` only once.

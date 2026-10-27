# Indezy Development Container

A development environment for Indezy in VS Code (or any Dev Containers client): the tools to build and test both
applications, next to a PostgreSQL database and pgAdmin, started from `infra/compose/compose.yaml`.

## What's Included

### Services
- **Development container** (`devcontainer`): built from `.devcontainer/Dockerfile`, an Ubuntu 24.04 devcontainer
  image. The server's own `indezy-server/Dockerfile` builds the runtime image and holds no build tools.
- **PostgreSQL 18**: the major version production runs, with the extensions of `infra/database/init`; Hibernate
  creates the schema, and `mask db-reset` loads the development sample data.
- **pgAdmin 4**, with the database connection pre-configured.

### Tools
- Java 25 (Temurin) and Node.js 26, from the devcontainer features in `devcontainer.json`
- The Maven wrapper (`indezy-server/mvnw`) and the Angular CLI of the project (`npx ng`)
- Chromium with its system libraries, installed by Playwright, for the frontend unit tests (Karma) and end-to-end
  tests (Playwright); `CHROME_BIN` points Karma at it
- `psql` and `pg_isready`, Git
- The VS Code extensions listed in `.devcontainer/extensions.json`

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

The [maskfile](../maskfile.md) commands work too once `mask` is installed (`cargo install mask`, see
[development](../docs/development.md)).

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:4200 | - |
| Backend API | http://localhost:8080/api | - |
| pgAdmin | http://localhost:5050 | admin@indezy.com / admin |
| PostgreSQL | localhost:5432 (`postgres:5432` inside the container) | indezy_user / indezy_password |

The backend runs with the `devcontainer` Spring profile (`SPRING_PROFILES_ACTIVE`), whose
`application-devcontainer.yml` points it at the `postgres` service.

## Troubleshooting

Run these from the host: the container has no Docker CLI.

```bash
# Services and database logs
docker compose -f infra/compose/compose.yaml ps
docker compose -f infra/compose/compose.yaml logs postgres

# Reset the database. Needed once after moving from PostgreSQL 15: its data directory cannot be read by 18.
docker compose -f infra/compose/compose.yaml down -v
docker compose -f infra/compose/compose.yaml up -d postgres
```

- **Ports in use**: 4200, 8080, 5432 and 5050 must be free on the host.
- **Browser tests cannot find Chrome**: open a new terminal so `CHROME_BIN` is set, or run
  `npx playwright install --with-deps chromium` in `indezy-web`.

## Updating the Container

Run **Dev Containers: Rebuild Container** from the VS Code command palette. The Maven, npm, Playwright and VS Code
extension caches are volumes, kept across rebuilds; `post-create.sh` adds its aliases to `~/.bashrc` only once.

## Security Notes

The database and pgAdmin credentials are for local development only and must never be used elsewhere.

## Additional Resources

- [VS Code Dev Containers documentation](https://code.visualstudio.com/docs/devcontainers/containers)
- [Development guide](../docs/development.md)

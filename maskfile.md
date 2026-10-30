# Indezy development commands

Run them with [mask](https://github.com/jacobdeichert/mask) (`cargo install mask` or `brew install mask`) from the
repository root; `mask --help` lists them. Settings come from an optional `.env` (see `.env.example`).

## install

> Install the server and web dependencies

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
mvnw dependency:go-offline
(cd indezy-web && npm ci)
```

## build

> Build the server jar and the web app

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
mvnw clean package -DskipTests
(cd indezy-web && npm run build)
```

## test

> Run the server and web tests, with coverage

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
mvnw verify
(cd indezy-web && npm run test:ci)
echo "📊 Coverage: indezy-server/target/site/jacoco/index.html and indezy-web/coverage/index.html"
```

## test-indezy-server

> Run the server tests, Checkstyle and SpotBugs

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
mvnw verify
```

## test-indezy-web

> Run the web unit tests in headless Chrome

```bash
cd "$MASKFILE_DIR/indezy-web"
npm run test:ci
```

## run

> Build both applications, then run PostgreSQL, the server (in the background) and the web app

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
echo "🧹 Stopping the previous local run..."
bash infra/scripts/stop-local-ports.sh
mvnw clean package -DskipTests
(cd indezy-web && npm run build)
start_postgres

echo "🚀 Starting indezy-server (logs: indezy-server.log)..."
if on_windows_host; then
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File infra/scripts/start-backend.ps1
else
    (cd indezy-server && nohup ./mvnw spring-boot:run > ../indezy-server.log 2>&1 &)
fi
for _ in {1..60}; do
    http_ok "$backend_health_url" && break
    sleep 1
done
if ! http_ok "$backend_health_url"; then
    echo "❌ indezy-server did not become healthy. Recent logs:"
    tail -n 40 indezy-server.log indezy-server-error.log 2>/dev/null || true
    exit 1
fi

cd "$MASKFILE_DIR/indezy-web"
npm start
```

## run-indezy-server

> Run PostgreSQL and the server in the foreground

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
start_postgres
mvnw spring-boot:run
```

## run-indezy-web

> Run the web app's development server

```bash
cd "$MASKFILE_DIR/indezy-web"
npm start
```

## db-reset

> Wipe PostgreSQL and fill it with the sample data

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
echo "🧹 Stopping the local run before resetting the database..."
bash infra/scripts/stop-local-ports.sh
start_postgres
mvnw spring-boot:run -Dspring-boot.run.profiles=seed -Dspring-boot.run.arguments=--spring.main.web-application-type=none
echo ""
echo "✅ Sample database is ready: sign in as john.doe@example.com / password123, then run: mask run"
```

## status

> Show which local services answer

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
report() {
    if http_ok "$2"; then echo "✅ $1: $2"; else echo "❌ $1 is not answering on $2"; fi
}
report "Web app" "http://localhost:${FRONTEND_PORT:-4200}/"
report "Server" "$backend_health_url"
echo "   Swagger UI: http://localhost:${SERVER_PORT}/api/swagger-ui.html"
compose ps
```

## stop

> Stop the local server, web app and containers

```bash
source "$MASKFILE_DIR/infra/scripts/mask-helpers.sh"
bash infra/scripts/stop-local-ports.sh
compose down
echo "✅ All services stopped"
```

## logs

> Follow the server logs of `mask run`

```bash
tail -f "$MASKFILE_DIR/indezy-server.log"
```

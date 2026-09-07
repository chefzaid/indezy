#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repository_root"

phase="${1:-all}"

prepare_sources() {
  infra/scripts/set-project-version.sh "$APP_VERSION"
}

build_application() {
  kubectl kustomize infra/k8s >/dev/null
  kubectl apply --dry-run=client --validate=false \
    -f infra/argocd/application.yaml >/dev/null

  pushd indezy-server >/dev/null
  ./mvnw --batch-mode clean package -DskipTests
  artifact="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit)"
  test -n "$artifact"
  cp "$artifact" target/indezy-server.jar
  popd >/dev/null

  pushd indezy-web >/dev/null
  npm ci
  node -e '
    const angular = require("./angular.json");
    const replacements = angular.projects["indezy-web"].architect.build
      .configurations.production.fileReplacements ?? [];
    const configured = replacements.some(({ replace, with: target }) =>
      replace === "src/environments/environment.ts" &&
      target === "src/environments/environment.prod.ts");
    if (!configured) throw new Error("The production build must enable the SSO environment.");
  '
  npm run build -- --configuration=production
  tar -czf "indezy-web-${APP_VERSION}.tar.gz" -C dist indezy-web
  popd >/dev/null
}

test_application() {
  pushd indezy-server >/dev/null
  ./mvnw --batch-mode clean verify -Djacoco.haltOnFailure=false
  popd >/dev/null

  pushd indezy-web >/dev/null
  export CHROME_BIN="${CHROME_BIN:-/usr/bin/chromium-browser}"
  npm ci
  npm run lint
  npm run test:ci
  popd >/dev/null

  python3 infra/scripts/ci-coverage-check.py \
    indezy-server/target/site/jacoco/jacoco.xml \
    indezy-web/coverage/cobertura-coverage.xml
}

prepare_sources
case "$phase" in
  build)
    build_application
    ;;
  test)
    test_application
    ;;
  all)
    build_application
    test_application
    ;;
  *)
    printf 'Usage: %s [build|test|all]\n' "$0" >&2
    exit 2
    ;;
esac

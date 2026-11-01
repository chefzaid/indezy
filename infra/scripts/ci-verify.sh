#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repository_root"

phase="${1:-all}"

prepare_sources() {
  infra/scripts/set-project-version.sh "$APP_VERSION"
}

build_application() {
  infra/scripts/check-onboarding-revision.sh build
  python3 infra/scripts/test-onboarding.py
  kubectl kustomize infra/k8s >/dev/null
  kubectl kustomize infra/overlays/ha >/dev/null
  kubectl apply --dry-run=client --validate=false \
    -f infra/argocd/application.yaml >/dev/null

  pushd indezy-server >/dev/null
  # SpotBugs blocks delivery, as Checkstyle does through package's validate phase (ADR 0007).
  ./mvnw --batch-mode clean package -DskipTests spotbugs:check
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

# Runs a step for at most the given seconds. A step still running then prints the threads of every JVM and the
# processes left, and fails, so a hang names its test and the job keeps its reports instead of reaching the job's
# two-hour timeout with nothing to read.
within() {
  local seconds="$1" waited=0 step
  shift
  "$@" &
  step=$!
  while kill -0 "$step" 2>/dev/null; do
    if (( waited >= seconds )); then
      printf '%s still running after %s seconds; threads and processes left:\n' "$*" "$seconds" >&2
      jcmd -l 2>/dev/null | while read -r pid _; do jcmd "$pid" Thread.print 2>/dev/null || true; done
      ps >&2 || true
      kill "$step" 2>/dev/null || true
      return 124
    fi
    sleep 5
    waited=$((waited + 5))
  done
  wait "$step"
}

test_application() {
  pushd indezy-server >/dev/null
  # The tests log at DEBUG (application-test.properties): each class's output goes to its surefire report, kept as an
  # artifact, as the job log stops at GitLab's 4 MB within minutes.
  within 2400 ./mvnw --batch-mode clean verify -Djacoco.haltOnFailure=false -Dmaven.test.redirectTestOutputToFile=true
  popd >/dev/null

  pushd indezy-web >/dev/null
  if [[ -z "${CHROME_BIN:-}" ]]; then
    # The Ubuntu host's AppArmor profile for /usr/lib/chromium/chromium is stacked on the container's, which then
    # refuses Karma's signals: Karma cannot stop Chrome after the run and never exits. Under another name the same
    # binary runs under the container's profile alone.
    ln -f /usr/lib/chromium/chromium /usr/lib/chromium/chromium-karma
    export CHROME_BIN=/usr/lib/chromium/chromium-karma
  fi
  within 900 npm ci
  within 900 npm run lint
  within 1800 npm run test:ci
  within 600 npm run test:node
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

#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repository_root"

mode="${1:-verify}"
: "${APP_VERSION:?APP_VERSION is required}"
: "${REGISTRY_PUSH_HOST:?REGISTRY_PUSH_HOST is required}"
: "${KANIKO_EXECUTOR:?KANIKO_EXECUTOR is required}"
# Docker Hub limits anonymous pulls, so its base images come through the swirl group's GitLab Dependency Proxy; Kaniko
# still falls back to Docker Hub when the proxy cannot serve one. Images of other registries are pulled directly.
: "${CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX:?CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX is required}"
: "${CI_DEPENDENCY_PROXY_SERVER:?CI_DEPENDENCY_PROXY_SERVER is required}"
: "${CI_DEPENDENCY_PROXY_USER:?CI_DEPENDENCY_PROXY_USER is required}"
: "${CI_DEPENDENCY_PROXY_PASSWORD:?CI_DEPENDENCY_PROXY_PASSWORD is required}"

case "$mode" in
  verify)
    ;;
  publish)
    : "${CI_REGISTRY_USER:?CI_REGISTRY_USER is required}"
    : "${CI_REGISTRY_PASSWORD:?CI_REGISTRY_PASSWORD is required}"
    ;;
  *)
    printf 'Usage: %s [verify|publish]\n' "$0" >&2
    exit 2
    ;;
esac

# The proxy's credentials, under its host with and without the port, and in publish mode the registry's. jq reads the
# secrets from the environment, so they appear in no command line.
mkdir -p /kaniko/.docker
export CI_DEPENDENCY_PROXY_USER CI_DEPENDENCY_PROXY_PASSWORD
[[ "$mode" != publish ]] || export CI_REGISTRY_USER CI_REGISTRY_PASSWORD
jq -n --arg proxy "$CI_DEPENDENCY_PROXY_SERVER" --arg proxy_host "${CI_DEPENDENCY_PROXY_SERVER%:443}" \
  --arg registry "$REGISTRY_PUSH_HOST" --arg mode "$mode" '
  {username: $ENV.CI_DEPENDENCY_PROXY_USER, password: $ENV.CI_DEPENDENCY_PROXY_PASSWORD} as $proxy_auth
  | {auths: ({($proxy): $proxy_auth, ($proxy_host): $proxy_auth}
      + if $mode == "publish"
        then {($registry): {username: $ENV.CI_REGISTRY_USER, password: $ENV.CI_REGISTRY_PASSWORD}}
        else {} end)}' > /kaniko/.docker/config.json
export DOCKER_CONFIG=/kaniko/.docker

build_image() {
  local name="$1" context="$2" dockerfile="$3"
  local repository="$REGISTRY_PUSH_HOST/$CI_PROJECT_PATH/$name"
  local options=(
    --context "dir://$context"
    --dockerfile "$dockerfile"
    --destination "$repository:$APP_VERSION"
    --insecure-registry "$REGISTRY_PUSH_HOST"
    --registry-mirror "$CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX"
  )
  if [[ "$mode" == publish ]]; then
    options+=(--cache=true --cache-repo "$repository/cache" --cache-ttl=720h)
  else
    options+=(--no-push --cache=false)
  fi
  "$KANIKO_EXECUTOR" "${options[@]}"
}

build_image indezy-server "$repository_root/indezy-server" "$repository_root/indezy-server/Dockerfile.runtime"
build_image indezy-web "$repository_root/indezy-web" "$repository_root/indezy-web/Dockerfile.runtime"

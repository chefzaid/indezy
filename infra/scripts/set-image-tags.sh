#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <immutable-image-tag>" >&2
  exit 2
fi

tag="$1"
case "$tag" in
  ''|*[!A-Za-z0-9_.-]*)
    echo "Invalid image tag: $tag" >&2
    exit 2
    ;;
esac

script_dir="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
kustomization="$script_dir/../k8s/kustomization.yaml"
for image in indezy-server indezy-web; do
  sed -i "/name: registry.swirlit.dev\/swirlit\/indezy\/$image/{n;s/newTag: .*/newTag: $tag/;}" "$kustomization"
done

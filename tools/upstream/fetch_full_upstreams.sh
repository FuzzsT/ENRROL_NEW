#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-_upstream_downloads}"
mkdir -p "$ROOT/google" "$ROOT/admin-dpc"
GOOGLE_SHA="d42d7f196d2db3d22ba4fca1e74faa5bc9b58d4e"
ADMIN_SHA="2bc77ccd902f9b23de24fffbbf8336f22e502276"
curl -fL --retry 3 -o "$ROOT/google/android-testdpc-$GOOGLE_SHA.zip" \
  "https://github.com/googlesamples/android-testdpc/archive/$GOOGLE_SHA.zip"
curl -fL --retry 3 -o "$ROOT/admin-dpc/admin-dpc-$ADMIN_SHA.zip" \
  "https://github.com/ser-mk/admin-dpc/archive/$ADMIN_SHA.zip"
curl -fL --retry 3 -o "$ROOT/admin-dpc/admin-dpc-v0.1.apk" \
  "https://github.com/ser-mk/admin-dpc/releases/download/v0.1/admin-dpc.apk"
sha256sum "$ROOT/google/"* "$ROOT/admin-dpc/"* | tee "$ROOT/SHA256SUMS.txt"
printf 'Upstream download complete: %s\n' "$ROOT"

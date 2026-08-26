#!/usr/bin/env bash
set -euo pipefail
repo=${1:?usage: build_variant.sh <repo-root> <variant>}
variant=${2:?usage: build_variant.sh <repo-root> <variant>}
case "$variant" in
  EnterpriseDebug|EnterpriseRelease|SystemPrivilegedDebug|SystemPrivilegedRelease|LabDebug|LabRelease|TstDebug|TstRelease|EngDebug|EngRelease) ;;
  *) echo "unsupported DPC-AIO variant: $variant" >&2; exit 64 ;;
esac
cd "$repo" 2>/dev/null || { echo "missing Gradle wrapper: $repo/gradlew" >&2; exit 66; }
test -f ./gradlew || { echo "missing Gradle wrapper: $repo/gradlew" >&2; exit 66; }
chmod +x ./gradlew
exec ./gradlew ":app-dpc:assemble${variant}"

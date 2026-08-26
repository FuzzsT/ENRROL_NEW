#!/usr/bin/env bash
set -euo pipefail
REPO="${1:-local-localhost-app-system/dpc_android}"
command -v gh >/dev/null || { echo "GitHub CLI (gh) is required." >&2; exit 2; }
gh auth status >/dev/null

gh workflow run build-emergency-enrollment.yml --repo "$REPO"
echo "Emergency enrollment workflow started for $REPO"
latest="$(gh run list --repo "$REPO" --workflow build-emergency-enrollment.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
if [[ -n "$latest" ]]; then
  echo "Watching run $latest"
  gh run watch "$latest" --repo "$REPO" --exit-status
  echo "Release: https://github.com/$REPO/releases/tag/dpc-aio-emergency-enrollment"
fi

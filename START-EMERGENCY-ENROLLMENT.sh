#!/usr/bin/env bash
set -euo pipefail
REPO="${1:-FuzzsT/ENRROL_NEW}"
WORKFLOW="build-aio-enrollment.yml"
command -v gh >/dev/null || { echo "GitHub CLI (gh) is required." >&2; exit 2; }
gh auth status >/dev/null

gh workflow run "$WORKFLOW" --repo "$REPO" -f signing_mode=emergency
echo "Emergency enrollment mode started for $REPO"
latest="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId')"
if [[ -n "$latest" ]]; then
  echo "Watching run $latest"
  gh run watch "$latest" --repo "$REPO" --exit-status
  echo "Release: https://github.com/$REPO/releases/tag/dpc-aio-emergency-enrollment"
fi

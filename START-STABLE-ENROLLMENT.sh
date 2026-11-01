#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-FuzzsT/ENRROL_NEW}"
WORKFLOW="build-aio-enrollment.yml"

command -v gh >/dev/null || { echo "GitHub CLI (gh) is required." >&2; exit 2; }
gh auth status >/dev/null

echo "Starting NORMAL/STABLE enrollment build for $REPO"
echo "This launcher never falls back to emergency signing."

gh workflow run "$WORKFLOW" --repo "$REPO" -f signing_mode=stable
sleep 2
run_id="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId')"
[[ -n "$run_id" ]] || { echo "Could not resolve stable workflow run." >&2; exit 3; }

echo "Watching stable run $run_id"
if ! gh run watch "$run_id" --repo "$REPO" --exit-status; then
  echo "Stable build failed. Check stable signing secrets / expected certificate. Emergency fallback is intentionally disabled." >&2
  exit 4
fi

echo "Stable release: https://github.com/$REPO/releases/tag/dpc-aio-continuous"

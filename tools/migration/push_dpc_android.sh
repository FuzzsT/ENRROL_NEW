#!/usr/bin/env bash
set -euo pipefail
REMOTE="${1:-https://github.com/local-localhost-app-system/dpc_android.git}"
BRANCH="${2:-main}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [[ ! -d .git ]]; then
  git init
fi

git checkout -B "$BRANCH"
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi

old_sha="$(git ls-remote --heads origin "refs/heads/$BRANCH" | awk '{print $1}' | head -n1 || true)"
git add -A
if git diff --cached --quiet; then
  echo "No changes to commit."
  exit 0
fi

git -c user.name="DPC-AIO Migration" -c user.email="dpc-aio@users.noreply.github.com" \
  commit -m "Migrate DPC-AIO 1.1.4 enrollment-ready source"

if [[ -n "$old_sha" ]]; then
  echo "Replacing $BRANCH with force-with-lease against $old_sha"
  git push origin "HEAD:$BRANCH" "--force-with-lease=$BRANCH:$old_sha"
else
  echo "Remote branch does not exist; creating $BRANCH"
  git push -u origin "HEAD:$BRANCH"
fi

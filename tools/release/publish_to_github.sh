#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  tools/release/publish_to_github.sh --repo OWNER/REPO [options]

Options:
  --repo OWNER/REPO       Required GitHub repository target.
  --root PATH             Source root (default: repository root inferred from this script).
  --branch NAME           Branch to push (existing branch by default; main for a new repo).
  --message TEXT          Commit message for uncommitted source changes.
  --create-private        Create OWNER/REPO as a private repository if it does not exist.
  --create-public         Create OWNER/REPO as a public repository if it does not exist.
  --skip-final-preflight  Push source but do not require release secrets/variables yet.
  -h, --help              Show this help.

Safety:
  - no force push
  - no token/secret file handling
  - an existing mismatched origin is rejected
EOF
}

repo=""
root=""
branch=""
message="Initial import: DPC-AIO 1.1.3 GitHub Upload Ready"
create_visibility=""
skip_preflight=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) repo="${2:-}"; shift 2 ;;
    --root) root="${2:-}"; shift 2 ;;
    --branch) branch="${2:-}"; shift 2 ;;
    --message) message="${2:-}"; shift 2 ;;
    --create-private) create_visibility="private"; shift ;;
    --create-public) create_visibility="public"; shift ;;
    --skip-final-preflight) skip_preflight=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$repo" || ! "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "--repo OWNER/REPO is required." >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -z "$root" ]]; then
  root="$(cd "$script_dir/../.." && pwd)"
else
  root="$(cd "$root" && pwd)"
fi
cd "$root"

command -v git >/dev/null || { echo "git is required" >&2; exit 2; }
command -v gh >/dev/null || { echo "GitHub CLI (gh) is required" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 2; }

# Authentication is delegated to GitHub CLI; this script never reads/stores tokens.
gh auth status >/dev/null

if ! gh repo view "$repo" >/dev/null 2>&1; then
  if [[ -z "$create_visibility" ]]; then
    echo "Repository $repo does not exist or is not accessible." >&2
    echo "Re-run with --create-private or --create-public to create it explicitly." >&2
    exit 3
  fi
  if [[ "$create_visibility" == "private" ]]; then
    gh repo create "$repo" --private --description "DPC-AIO Android Device Policy Controller"
  else
    gh repo create "$repo" --public --description "DPC-AIO Android Device Policy Controller"
  fi
fi

new_git=0
if [[ ! -d .git ]]; then
  new_git=1
  if [[ -z "$branch" ]]; then branch="main"; fi
  if git init -b "$branch" >/dev/null 2>&1; then
    :
  else
    git init >/dev/null
    git checkout -b "$branch" >/dev/null
  fi
fi

current_branch="$(git branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "Detached HEAD is not supported by this helper." >&2
  exit 4
fi
if [[ -z "$branch" ]]; then
  branch="$current_branch"
elif [[ "$branch" != "$current_branch" ]]; then
  if [[ "$new_git" == "1" ]]; then
    git branch -M "$branch"
  else
    echo "Current branch is '$current_branch', requested '$branch'. Switch branches explicitly first." >&2
    exit 4
  fi
fi

expected_https="https://github.com/${repo}.git"
expected_ssh="git@github.com:${repo}.git"
if origin="$(git remote get-url origin 2>/dev/null)"; then
  if [[ "$origin" != "$expected_https" && "$origin" != "${expected_https%.git}" && "$origin" != "$expected_ssh" ]]; then
    echo "Existing origin points elsewhere: $origin" >&2
    echo "Expected $expected_https or $expected_ssh. Refusing to overwrite it." >&2
    exit 5
  fi
else
  git remote add origin "$expected_https"
fi

git add -A
if ! git diff --cached --quiet; then
  if ! git config user.email >/dev/null || ! git config user.name >/dev/null; then
    echo "Git identity is not configured. Set user.name and user.email before publishing." >&2
    exit 6
  fi
  git commit -m "$message"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean after commit; refusing to push." >&2
  exit 7
fi

# Intentionally no force flags: git push -u origin is the only push form used here.
git push -u origin "$branch"

echo "Source pushed to https://github.com/$repo on branch $branch."
if [[ "$skip_preflight" == "1" ]]; then
  echo "Final release preflight skipped by explicit request. Configure Actions secrets/variables before running the release workflow."
  exit 0
fi

if ! python3 "$root/tools/release/github_publish_preflight.py" --repo "$repo" --root "$root"; then
  echo "Push completed, but GitHub release preflight is BLOCKED." >&2
  echo "Configure the required GitHub Actions secret/variable names, then rerun:" >&2
  echo "  python3 tools/release/github_publish_preflight.py --repo $repo" >&2
  exit 8
fi

echo "GITHUB_PUBLISH: READY"

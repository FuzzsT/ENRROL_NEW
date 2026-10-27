#!/usr/bin/env bash
set -euo pipefail

repo="FuzzsT/ENRROL_NEW"
keystore=""
alias_name=""
expected_sha256=""
backup_dir="${HOME}/.dpc-aio/keystore-backups"
dispatch=false

usage() {
  cat <<'EOF'
Usage:
  import-existing-stable-signing.sh --keystore PATH [options]

Options:
  --repo OWNER/REPO          GitHub repository (default: FuzzsT/ENRROL_NEW)
  --keystore PATH            Existing JKS/PKCS12 keystore to restore (required)
  --alias ALIAS              Signing key alias; prompted if omitted
  --expected-sha256 HEX      Expected signing certificate SHA-256. If omitted,
                             the script tries the current dpc-aio-continuous release.
  --backup-dir PATH          Local backup directory outside the repository
  --dispatch                 Run build-aio-enrollment.yml after configuration
  -h, --help                 Show this help

The store password and key password are always read interactively without echo.
Secrets are sent to `gh secret set` over stdin and are never written to repo files.
EOF
}

while (($#)); do
  case "$1" in
    --repo) repo="${2:?missing value for --repo}"; shift 2 ;;
    --keystore) keystore="${2:?missing value for --keystore}"; shift 2 ;;
    --alias) alias_name="${2:?missing value for --alias}"; shift 2 ;;
    --expected-sha256) expected_sha256="${2:?missing value for --expected-sha256}"; shift 2 ;;
    --backup-dir) backup_dir="${2:?missing value for --backup-dir}"; shift 2 ;;
    --dispatch) dispatch=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

command -v keytool >/dev/null || { echo "keytool is required (JDK 21 recommended)." >&2; exit 1; }
command -v jarsigner >/dev/null || { echo "jarsigner is required (JDK 21 recommended)." >&2; exit 1; }
command -v jar >/dev/null || { echo "jar is required (JDK 21 recommended)." >&2; exit 1; }
command -v gh >/dev/null || { echo "GitHub CLI (gh) is required." >&2; exit 1; }
command -v base64 >/dev/null || { echo "base64 is required." >&2; exit 1; }

[[ -n "$keystore" ]] || { echo "--keystore is required." >&2; exit 2; }
[[ -f "$keystore" ]] || { echo "Keystore not found: $keystore" >&2; exit 1; }
keystore="$(cd "$(dirname "$keystore")" && pwd)/$(basename "$keystore")"

if [[ -z "$alias_name" ]]; then
  read -r -p "Signing key alias: " alias_name
fi
[[ -n "$alias_name" ]] || { echo "Signing key alias cannot be empty." >&2; exit 2; }

read -r -s -p "Keystore/store password: " store_password; echo >&2
read -r -s -p "Private-key password (press Enter to reuse store password): " key_password; echo >&2
if [[ -z "$key_password" ]]; then
  key_password="$store_password"
fi
trap 'unset store_password key_password keystore_b64 2>/dev/null || true' EXIT

keytool_output="$(keytool -list -v -keystore "$keystore" -storepass "$store_password" -alias "$alias_name" 2>&1)" || {
  echo "Keystore validation failed. Check store password and alias." >&2
  exit 1
}
observed_sha256="$(printf '%s\n' "$keytool_output" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
[[ "$observed_sha256" =~ ^[0-9A-F]{64}$ ]] || { echo "Could not determine SHA-256 signing certificate fingerprint." >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"; unset store_password key_password keystore_b64 2>/dev/null || true' EXIT
mkdir -p "$tmp/empty"
jar --create --file "$tmp/probe.jar" -C "$tmp/empty" . >/dev/null
jarsigner -keystore "$keystore" -storepass "$store_password" -keypass "$key_password" \
  -signedjar "$tmp/probe-signed.jar" "$tmp/probe.jar" "$alias_name" >/dev/null 2>&1 || {
  echo "Private-key validation failed. Check alias/key password." >&2
  exit 1
}

normalize_sha() {
  printf '%s' "$1" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'
}

if [[ -z "$expected_sha256" ]]; then
  release_url="https://github.com/${repo}/releases/download/dpc-aio-continuous/signing-cert-sha256.txt"
  if command -v curl >/dev/null && expected_raw="$(curl -fsSL --max-time 20 "$release_url" 2>/dev/null)"; then
    expected_sha256="$(normalize_sha "$expected_raw")"
  fi
fi

if [[ -n "$expected_sha256" ]]; then
  expected_sha256="$(normalize_sha "$expected_sha256")"
  [[ "$expected_sha256" =~ ^[0-9A-F]{64}$ ]] || { echo "Expected SHA-256 is not a 64-digit hex fingerprint." >&2; exit 2; }
  if [[ "$observed_sha256" != "$expected_sha256" ]]; then
    echo "SIGNER_MISMATCH: recovered keystore does not match expected stable signer." >&2
    echo "Expected: $expected_sha256" >&2
    echo "Observed: $observed_sha256" >&2
    exit 1
  fi
else
  echo "No historical stable fingerprint was available." >&2
  echo "Refusing to configure a recovered keystore without --expected-sha256." >&2
  exit 1
fi

mkdir -p "$backup_dir"
chmod 700 "$backup_dir" 2>/dev/null || true
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_path="$backup_dir/dpc-aio-stable-${stamp}-$(basename "$keystore")"
cp -p "$keystore" "$backup_path"
chmod 600 "$backup_path" 2>/dev/null || true

gh auth status >/dev/null

keystore_b64="$(base64 < "$keystore" | tr -d '\r\n')"
printf '%s' "$keystore_b64" | gh secret set DPC_AIO_RELEASE_KEYSTORE_B64 --repo "$repo"
printf '%s' "$store_password" | gh secret set DPC_AIO_RELEASE_STORE_PASSWORD --repo "$repo"
printf '%s' "$alias_name" | gh secret set DPC_AIO_RELEASE_KEY_ALIAS --repo "$repo"
printf '%s' "$key_password" | gh secret set DPC_AIO_RELEASE_KEY_PASSWORD --repo "$repo"
gh variable set DPC_AIO_EXPECTED_SIGNING_CERT_SHA256 --repo "$repo" --body "$observed_sha256"

printf 'Configured stable signer for %s\n' "$repo"
printf 'Certificate SHA-256: %s\n' "$observed_sha256"
printf 'Local backup: %s\n' "$backup_path"

if [[ "$dispatch" == true ]]; then
  gh workflow run build-aio-enrollment.yml --repo "$repo" --ref main
  echo "Dispatched build-aio-enrollment.yml on main."
else
  echo "Run when ready: gh workflow run build-aio-enrollment.yml --repo '$repo' --ref main"
fi

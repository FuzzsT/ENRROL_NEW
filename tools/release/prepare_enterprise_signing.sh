#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${GITHUB_ENV:?GITHUB_ENV is required}"

keystore="$RUNNER_TEMP/dpc-aio-enterprise-release.jks"
output_file="${GITHUB_OUTPUT:-/dev/null}"
umask 077

append_env() {
  local name="$1"
  local value="$2"
  printf '%s=%s\n' "$name" "$value" >> "$GITHUB_ENV"
}

append_output() {
  local name="$1"
  local value="$2"
  printf '%s=%s\n' "$name" "$value" >> "$output_file"
}

stable_values=(
  "${DPC_AIO_RELEASE_KEYSTORE_B64:-}"
  "${DPC_AIO_RELEASE_STORE_PASSWORD:-}"
  "${DPC_AIO_RELEASE_KEY_ALIAS:-}"
  "${DPC_AIO_RELEASE_KEY_PASSWORD:-}"
)
stable_count=0
for value in "${stable_values[@]}"; do
  [[ -n "$value" ]] && stable_count=$((stable_count + 1))
done

if [[ "$stable_count" -eq 4 ]]; then
  if [[ -z "${DPC_AIO_EXPECTED_SIGNING_CERT_SHA256:-}" ]]; then
    echo "STABLE_SIGNING_KEY_REQUIRED: DPC_AIO_EXPECTED_SIGNING_CERT_SHA256 is not configured" >&2
    exit 1
  fi

  printf '%s' "$DPC_AIO_RELEASE_KEYSTORE_B64" | base64 --decode > "$keystore"
  test -s "$keystore"

  expected="$(printf '%s' "$DPC_AIO_EXPECTED_SIGNING_CERT_SHA256" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
  append_env DPC_AIO_RELEASE_KEYSTORE_PATH "$keystore"
  append_env DPC_AIO_RELEASE_STORE_PASSWORD "$DPC_AIO_RELEASE_STORE_PASSWORD"
  append_env DPC_AIO_RELEASE_KEY_ALIAS "$DPC_AIO_RELEASE_KEY_ALIAS"
  append_env DPC_AIO_RELEASE_KEY_PASSWORD "$DPC_AIO_RELEASE_KEY_PASSWORD"
  append_env DPC_AIO_EXPECTED_SIGNING_CERT_SHA256 "$expected"
  append_env DPC_AIO_SIGNING_MODE "STABLE_SECRETS"
  append_output mode "stable-secrets"
  echo "Enterprise release signing mode: STABLE_SECRETS"
  exit 0
fi

if [[ "$stable_count" -ne 0 ]]; then
  echo "INCOMPLETE_STABLE_SIGNING_CONFIG: configure all four release signing secrets or remove the partial set" >&2
  exit 1
fi

if [[ "${GITHUB_EVENT_NAME:-}" != "workflow_dispatch" ]]; then
  echo "STABLE_SIGNING_KEY_REQUIRED: tag/push release builds require repository signing secrets" >&2
  exit 1
fi

password="${DPC_AIO_MANUAL_SIGNING_PASSWORD:-}"
if [[ -z "$password" ]]; then
  echo "MANUAL_SIGNING_PASSWORD_REQUIRED: enter release_signing_password when starting Run workflow" >&2
  exit 1
fi
if [[ "$password" == *$'\n'* || "$password" == *$'\r'* ]]; then
  echo "MANUAL_SIGNING_PASSWORD_INVALID: line breaks are not supported" >&2
  exit 1
fi
if (( ${#password} < 12 )); then
  echo "MANUAL_SIGNING_PASSWORD_INVALID: use at least 12 characters" >&2
  exit 1
fi

alias="${DPC_AIO_MANUAL_SIGNING_ALIAS:-dpc-aio-enterprise}"
if [[ ! "$alias" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "MANUAL_SIGNING_ALIAS_INVALID: use only letters, digits, dot, underscore, or dash" >&2
  exit 1
fi

export DPC_AIO_EFFECTIVE_SIGNING_PASSWORD="$password"
keytool_bin="$(command -v keytool)"
certificate="$RUNNER_TEMP/dpc-aio-enterprise-release.cer"
rm -f "$keystore" "$certificate"

"$keytool_bin" -genkeypair -noprompt \
  -alias "$alias" \
  -keyalg RSA \
  -keysize 3072 \
  -sigalg SHA256withRSA \
  -validity 3650 \
  -dname "CN=DPC-AIO Enterprise Bootstrap,OU=CI,O=DPC-AIO" \
  -keystore "$keystore" \
  -storetype JKS \
  -storepass:env DPC_AIO_EFFECTIVE_SIGNING_PASSWORD \
  -keypass:env DPC_AIO_EFFECTIVE_SIGNING_PASSWORD

"$keytool_bin" -exportcert \
  -alias "$alias" \
  -keystore "$keystore" \
  -storepass:env DPC_AIO_EFFECTIVE_SIGNING_PASSWORD \
  -file "$certificate" >/dev/null

expected="$(sha256sum "$certificate" | awk '{print toupper($1)}')"
rm -f "$certificate"
test -s "$keystore"

append_env DPC_AIO_RELEASE_KEYSTORE_PATH "$keystore"
append_env DPC_AIO_RELEASE_STORE_PASSWORD "$password"
append_env DPC_AIO_RELEASE_KEY_ALIAS "$alias"
append_env DPC_AIO_RELEASE_KEY_PASSWORD "$password"
append_env DPC_AIO_EXPECTED_SIGNING_CERT_SHA256 "$expected"
append_env DPC_AIO_SIGNING_MODE "GENERATED_BOOTSTRAP"
append_output mode "generated-bootstrap"
append_output signing_cert_sha256 "$expected"

echo "Enterprise release signing mode: GENERATED_BOOTSTRAP"
echo "Generated signing certificate SHA-256: $expected"
echo "Generated key is run-scoped; configure stable repository secrets before shipping update-compatible production releases."

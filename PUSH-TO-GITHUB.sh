#!/usr/bin/env bash
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/tools/migration/push_dpc_android.sh" "$@"

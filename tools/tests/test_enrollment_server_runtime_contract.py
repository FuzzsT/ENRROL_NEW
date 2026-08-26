#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
server = (ROOT / 'services/provisioning/src/server.mjs').read_text('utf-8')
store = (ROOT / 'services/provisioning/src/token-store.mjs').read_text('utf-8')
for marker in ['DPC_AIO_TOKEN_STORE_PATH', 'DPC_AIO_ENROLLMENT_SIGNING_PRIVATE_KEY_FILE', 'storagePath', 'createPrivateKey']:
    assert marker in server + store, f'missing runtime persistence/trust marker {marker}'
assert 'enrollment-token-store.json' in server
assert "bootstrap: {}" in server, 'default bootstrap must not require unsupported policy APIs'
assert 'DPC_AIO_ALLOW_EPHEMERAL_SIGNING_KEY' in server
print('test_enrollment_server_runtime_contract: PASS')

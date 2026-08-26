#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / 'apps/dpc/modules/core/model/src/main/kotlin/io/dpcaio/core/model/EnrollmentBootstrap.kt'
TRUST = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentTrustVerifier.kt'
CLIENT = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentHttpClient.kt'
for p in [MODEL, TRUST, CLIENT]: assert p.is_file(), f'{p.name} missing'
t = TRUST.read_text('utf-8')
c = CLIENT.read_text('utf-8')
for marker in ['Ed25519', 'expectedSessionId', 'expectedReservationId', 'expiresAt', 'nonce']:
    assert marker in t, f'trust verifier missing {marker}'
assert 'HttpsURLConnection' in c
assert 'http://' not in c, 'client must not accept insecure endpoint literals'
for forbidden in ['trustAll', 'ALLOW_ALL_HOSTNAME', 'HostnameVerifier { _, _ -> true', 'setDefaultHostnameVerifier']:
    assert forbidden not in t + c, f'permissive TLS forbidden: {forbidden}'
print('test_enrollment_trust_contract: PASS')

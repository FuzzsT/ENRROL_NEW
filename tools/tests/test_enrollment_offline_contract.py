#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
a = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeActivity.kt').read_text('utf-8')
p = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentConfigParser.kt').read_text('utf-8')
s = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentSessionStore.kt').read_text('utf-8')
assert 'intent.getBooleanExtra' in a and 'EXTRA_PROVISIONING_ALLOW_OFFLINE' in a
assert 'EnrollmentConfigParser.KEY_ALLOW_OFFLINE' in a
assert 'KEY_ALLOW_OFFLINE' in p and 'KEY_ALLOW_OFFLINE' in s
assert 'allowOffline = prefs.getBoolean' in s
print('test_enrollment_offline_contract: PASS')

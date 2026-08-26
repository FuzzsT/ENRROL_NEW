#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
gen = (ROOT / 'tools/provisioning/generate_provisioning.py').read_text('utf-8')
gradle = (ROOT / 'apps/dpc/app/build.gradle.kts').read_text('utf-8')
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text('utf-8')
for marker in ['--enrollment-endpoint', 'io.dpcaio.extra.ENROLLMENT_ENDPOINT', 'io.dpcaio.extra.ENROLLMENT_SOURCE']:
    assert marker in gen, f'generator missing {marker}'
for marker in ['DPC_AIO_ENROLLMENT_ENDPOINT', 'DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY', 'ENROLLMENT_SIGNING_PUBLIC_KEY', '--enrollment-endpoint']:
    assert marker in gradle, f'gradle missing {marker}'
for marker in ['enrollment_endpoint', 'enrollment_signing_public_key', 'DPC_AIO_ENROLLMENT_ENDPOINT', 'DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY']:
    assert marker in workflow, f'workflow missing {marker}'
print('test_enrollment_build_config_contract: PASS')

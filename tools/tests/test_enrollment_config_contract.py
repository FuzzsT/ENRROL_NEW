#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
PARSER = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentConfigParser.kt'
MODEL = ROOT / 'apps/dpc/modules/core/model/src/main/kotlin/io/dpcaio/core/model/EnrollmentModel.kt'
assert PARSER.is_file(), 'EnrollmentConfigParser.kt missing'
assert MODEL.is_file(), 'EnrollmentModel.kt missing'
parser = PARSER.read_text('utf-8')
model = MODEL.read_text('utf-8')
for marker in ['enrollmentToken', 'policyProfile', 'kmeUri', 'zeroTouch', 'GENERIC_ANDROID_ENTERPRISE']:
    assert marker in parser or marker in model, f'missing enrollment marker {marker}'
assert 'Build.MANUFACTURER' not in parser, 'source detection must not be manufacturer-based'
for source in ['QR', 'KME', 'ZERO_TOUCH', 'NFC', 'MANUAL_TOKEN', 'BYOD_WORK_PROFILE', 'GENERIC_ANDROID_ENTERPRISE']:
    assert source in model, f'missing source {source}'
print('test_enrollment_config_contract: PASS')

#!/usr/bin/env python3
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT))
from tools.verify_aio.release_report import ReleaseVerification, StepStatus

r=ReleaseVerification(version='1.0.1')
r.set_step('source','PASS')
r.set_step('apkBuild','BLOCKED', reason='GRADLE_EXACT_VERSION_MISSING')
r.set_step('deviceOwner','NOT_RUN')
r.set_step('fullOffline','NOT_RUN')
r.set_step('permissions','NOT_RUN')
r.set_step('components','NOT_RUN')
r.set_metadata({'token':'SECRET','password':'PW','note':'safe'})
data=json.loads(r.to_json())
assert data['verification']['sourceVerified'] is True
assert data['verification']['apkBuildVerified'] is False
assert data['verification']['deviceOwnerVerified'] is False
assert data['steps']['apkBuild']['status']=='BLOCKED'
text=json.dumps(data)
assert 'SECRET' not in text and 'PW' not in text and 'safe' in text
print('test_101_release_report: PASS')

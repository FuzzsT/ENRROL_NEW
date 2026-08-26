#!/usr/bin/env python3
from pathlib import Path
import json, os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
SCRIPT=ROOT/'tools/android_build_preflight.py'

def run(env, *args):
    cp=subprocess.run(['python3',str(SCRIPT),*args],cwd=ROOT,env=env,text=True,capture_output=True)
    return cp, json.loads(cp.stdout)

base=os.environ.copy()
base.pop('ANDROID_SDK_ROOT',None); base.pop('ANDROID_HOME',None)
cp,data=run(base)
assert cp.returncode==2, cp.stderr
assert data['state']=='BUILD_BLOCKED'
assert 'androidSdk' in data['blockers']

with tempfile.TemporaryDirectory() as td:
    sdk=Path(td)
    (sdk/'platforms/android-37').mkdir(parents=True)
    (sdk/'platforms/android-37/android.jar').write_bytes(b'fake')
    bt=sdk/'build-tools/37.0.0'; bt.mkdir(parents=True)
    apksigner=bt/'apksigner'; apksigner.write_text('#!/bin/sh\nexit 0\n'); apksigner.chmod(0o755)
    (sdk/'ndk/28.2.13676358').mkdir(parents=True)
    (sdk/'cmake/3.22.1').mkdir(parents=True)
    env=base.copy(); env['ANDROID_SDK_ROOT']=str(sdk)
    env.update({
      'DPC_AIO_RELEASE_KEYSTORE_PATH':'/tmp/fake.jks',
      'DPC_AIO_RELEASE_STORE_PASSWORD':'x',
      'DPC_AIO_RELEASE_KEY_ALIAS':'x',
      'DPC_AIO_RELEASE_KEY_PASSWORD':'x',
      'DPC_AIO_EXPECTED_SIGNING_CERT_SHA256':'00',
    })
    cp,data=run(env,'--require-signing')
    assert cp.returncode==0, cp.stderr+cp.stdout
    assert data['state']=='BUILD_READY', data
    assert all(data['checks'].values()), data
print('BUILD_PREFLIGHT_RUNTIME_111: PASS')

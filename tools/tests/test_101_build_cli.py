#!/usr/bin/env python3
from pathlib import Path
import sys, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from tools.verify_aio.build_resolver import parse_wrapper_requirement, inspect_supplied_gradle_zip, build_gradle_command

req=parse_wrapper_requirement(ROOT/'gradle/wrapper/gradle-wrapper.properties')
with tempfile.TemporaryDirectory() as td:
    td=Path(td)
    good=td/'gradle-9.7.0-bin.zip'
    with zipfile.ZipFile(good,'w') as z:
        z.writestr('gradle-9.7.0/bin/gradle','#!/bin/sh\necho Gradle 9.7.0\n')
    info=inspect_supplied_gradle_zip(good, req)
    assert info['version']=='9.7.0'
    assert len(info['sha256'])==64
    assert info['status']=='GRADLE_LOCAL_DISTRIBUTION'

    bad=td/'gradle-9.8.0-bin.zip'
    with zipfile.ZipFile(bad,'w') as z:
        z.writestr('gradle-9.8.0/bin/gradle','x')
    bad_info=inspect_supplied_gradle_zip(bad, req)
    assert bad_info['status']=='GRADLE_VERSION_MISMATCH', bad_info

cmd=build_gradle_command('/opt/gradle-9.7/bin/gradle', offline=True)
assert cmd[-2:]==['--offline', ':app-dpc:assembleEnterpriseDebug'], cmd
wrapper_before=(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
_ = build_gradle_command(str(ROOT/'gradlew'), offline=False)
assert (ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()==wrapper_before
print('test_101_build_cli: PASS')

import subprocess, json, os
p=subprocess.run([sys.executable,'-m','tools.verify_aio.cli','build-readiness','--root',str(ROOT)],cwd=ROOT,text=True,capture_output=True)
assert p.returncode in (0,2),p.stderr
out=json.loads(p.stdout)
assert out['requiredGradle']=='9.7.0'
assert out['status'] in ('BUILD_OFFLINE_READY','BUILD_ONLINE_REQUIRED','BUILD_BLOCKED'),out
assert isinstance(out['reasons'],list)
assert 'androidSdk' in out and 'dependenciesCached' in out
print('test_101_build_cli_readiness: PASS')

from tools.verify_aio.build_resolver import materialize_supplied_gradle_zip
with tempfile.TemporaryDirectory() as td:
    td=Path(td)
    z=td/'gradle-9.7.0-bin.zip'
    with zipfile.ZipFile(z,'w') as a:
        a.writestr('gradle-9.7.0/bin/gradle','#!/bin/sh\necho Gradle 9.7.0\n')
    exe=materialize_supplied_gradle_zip(z, req, td/'cache')
    assert exe.is_file() and exe.name=='gradle',exe
    assert 'gradle-9.7.0' in str(exe)
    evil=td/'evil.zip'
    with zipfile.ZipFile(evil,'w') as a:
        a.writestr('../escape','x')
    try:
        materialize_supplied_gradle_zip(evil, req, td/'cache2')
        raise AssertionError('path traversal zip must fail')
    except ValueError as e:
        assert 'unsafe' in str(e).lower() or 'invalid' in str(e).lower()
print('test_101_build_zip_materialize: PASS')

launcher=ROOT/'tools/verify-aio'
assert launcher.is_file(), launcher
assert launcher.stat().st_mode & 0o111, 'verify-aio launcher must be executable'
help_run=subprocess.run([str(launcher),'build-readiness','--root',str(ROOT),'--offline'],cwd=ROOT,text=True,capture_output=True)
assert help_run.returncode in (0,2),help_run.stderr
assert json.loads(help_run.stdout)['requiredGradle']=='9.7.0'
print('test_101_verify_aio_launcher: PASS')

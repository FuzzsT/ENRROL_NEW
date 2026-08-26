#!/usr/bin/env python3
from pathlib import Path
import sys,tempfile,json
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT))
from tools.offline_bundle.builder import create_bundle_dir, add_apk_file, validate_bundle

with tempfile.TemporaryDirectory() as td:
    td=Path(td); bundle=td/'bundle'
    create_bundle_dir(bundle, bundle_id='b1', key_id='k1')
    base=td/'random-name.apk'; base.write_bytes(b'base-content')
    split=td/'not-a-package-name.apk'; split.write_bytes(b'split-content')
    add_apk_file(bundle, base, package_name='com.example.app', version_code=7, role='base')
    add_apk_file(bundle, split, package_name='com.example.app', version_code=7, role='split', split_name='config.arm64_v8a')
    manifest=json.loads((bundle/'manifest.json').read_text())
    pkg=manifest['packages'][0]
    assert pkg['packageName']=='com.example.app'
    assert pkg['files'][0]['sha256'] and len(pkg['files'][0]['sha256'])==64
    result=validate_bundle(bundle)
    assert result.ok, result

    # Duplicate split must be rejected.
    add_apk_file(bundle, split, package_name='com.example.app', version_code=7, role='split', split_name='config.arm64_v8a')
    dup=validate_bundle(bundle)
    assert not dup.ok and 'DUPLICATE_SPLIT' in dup.codes, dup

with tempfile.TemporaryDirectory() as td:
    td=Path(td); bundle=td/'bundle'; create_bundle_dir(bundle,bundle_id='b2',key_id='k1')
    only=td/'split.apk'; only.write_bytes(b'x')
    add_apk_file(bundle, only, package_name='com.example.onlysplit', version_code=1, role='split', split_name='config.pl')
    r=validate_bundle(bundle)
    assert 'BASE_APK_MISSING' in r.codes, r
print('test_101_offline_bundle_builder: PASS')

from tools.offline_bundle.apk_inspector import ApkInspection
with tempfile.TemporaryDirectory() as td:
    td=Path(td); bundle=td/'bundle'; create_bundle_dir(bundle,bundle_id='b3',key_id='k1')
    apk=td/'base.apk'; apk.write_bytes(b'apk')
    inspected=ApkInspection('INSPECTED',package_name='com.real.app',version_code=9,version_name='9.0',min_sdk=29,target_sdk=37)
    try:
        add_apk_file(bundle, apk, package_name='com.wrong.app', version_code=9, role='base', inspection=inspected)
        raise AssertionError('package mismatch must fail')
    except ValueError as e:
        assert 'PACKAGE_NAME_MISMATCH' in str(e)
    add_apk_file(bundle, apk, package_name='com.real.app', version_code=9, role='base', inspection=inspected)
    m=json.loads((bundle/'manifest.json').read_text())
    assert m['packages'][0]['inspectionStatus']=='INSPECTED'
    assert m['packages'][0]['targetSdk']==37
print('test_101_offline_bundle_builder_inspection: PASS')

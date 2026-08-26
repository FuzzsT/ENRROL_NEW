#!/usr/bin/env python3
import tempfile
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.verify_aio.build_resolver import (
    parse_wrapper_requirement,
    choose_exact_gradle,
    evaluate_readiness,
)

root = ROOT
req = parse_wrapper_requirement(root / 'gradle/wrapper/gradle-wrapper.properties')
assert req.version == '9.7.0', req
assert req.distribution_name == 'gradle-9.7.0-bin.zip', req

candidates = [
    {'source': 'LOCAL', 'version': '9.8.0', 'path': '/opt/gradle-9.8'},
    {'source': 'CACHE', 'version': '9.7.0', 'path': '/cache/gradle-9.7'},
]
chosen = choose_exact_gradle(req, candidates)
assert chosen['source'] == 'CACHE', chosen
assert chosen['version'] == '9.7.0', chosen
assert choose_exact_gradle(req, [candidates[0]]) is None

ready = evaluate_readiness(
    requirement=req,
    gradle={'source':'CACHE','version':'9.7.0','path':'/cache/gradle-9.7'},
    java_major=21,
    android_sdk={'sdk':True,'platform':True,'build_tools':True},
    dependencies_cached=True,
    offline=True,
)
assert ready.status == 'BUILD_OFFLINE_READY', ready

blocked = evaluate_readiness(
    requirement=req,
    gradle=None,
    java_major=21,
    android_sdk={'sdk':True,'platform':True,'build_tools':True},
    dependencies_cached=False,
    offline=True,
)
assert blocked.status == 'BUILD_ONLINE_REQUIRED', blocked
assert 'GRADLE_EXACT_VERSION_MISSING' in blocked.reasons
assert 'DEPENDENCIES_MISSING' in blocked.reasons
print('test_101_build_resolver: PASS')

from tools.verify_aio.build_resolver import parse_catalog_requirements, probe_android_sdk, dependencies_cached
with tempfile.TemporaryDirectory() as td:
    td=Path(td)
    sdk=td/'sdk'; (sdk/'platforms/android-37').mkdir(parents=True); (sdk/'build-tools/37.0.0').mkdir(parents=True)
    sdk_state=probe_android_sdk(ROOT, {'ANDROID_SDK_ROOT':str(sdk)})
    assert sdk_state['sdk'] and sdk_state['platform'] and sdk_state['build_tools'], sdk_state
    reqs=parse_catalog_requirements(ROOT/'gradle/libs.versions.toml')
    assert ('com.android.tools.build','gradle','9.3.1') in reqs
    assert ('org.jetbrains.kotlin','kotlin-gradle-plugin','2.4.10') in reqs
    cache=td/'gradle-home/caches/modules-2/files-2.1'
    for group,artifact,version in reqs:
        (cache/group/artifact/version/'hash').mkdir(parents=True,exist_ok=True)
        (cache/group/artifact/version/'hash'/'artifact.jar').write_bytes(b'x')
    assert dependencies_cached(reqs, td/'gradle-home') is True
    # Remove one required artifact and ensure the preflight notices it.
    import shutil
    group,artifact,version=reqs[0]; shutil.rmtree(cache/group/artifact/version)
    assert dependencies_cached(reqs, td/'gradle-home') is False
print('test_101_build_resolver_extended: PASS')

blocked_sdk = evaluate_readiness(
    requirement=req, gradle=None, java_major=21,
    android_sdk={'sdk':False,'platform':False,'build_tools':False}, dependencies_cached=False, offline=True)
assert blocked_sdk.status == 'BUILD_BLOCKED', blocked_sdk
print('test_101_build_resolver_blocked_sdk: PASS')

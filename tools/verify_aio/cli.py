#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, subprocess
from pathlib import Path
from .build_resolver import (
    parse_wrapper_requirement, inspect_supplied_gradle_zip, build_gradle_command,
    parse_catalog_requirements, probe_android_sdk, dependencies_cached,
    probe_java_major, discover_gradle_candidates, choose_exact_gradle, evaluate_readiness,
)

def _collect(root: Path, gradle_zip: str | None, offline: bool) -> dict:
    req=parse_wrapper_requirement(root/'gradle/wrapper/gradle-wrapper.properties')
    gradle_home=Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))
    candidates=discover_gradle_candidates(req,gradle_home,os.environ.get('PATH'))
    supplied=None
    if gradle_zip:
        supplied=inspect_supplied_gradle_zip(Path(gradle_zip),req)
        if supplied['status']=='GRADLE_LOCAL_DISTRIBUTION':
            candidates.insert(0,{'source':'SUPPLIED_ZIP','version':supplied['version'],'path':supplied['path'],'sha256':supplied['sha256']})
    chosen=choose_exact_gradle(req,candidates)
    sdk=probe_android_sdk(root,os.environ)
    reqs=parse_catalog_requirements(root/'gradle/libs.versions.toml')
    deps=dependencies_cached(reqs,gradle_home)
    java=probe_java_major()
    readiness=evaluate_readiness(requirement=req,gradle=chosen,java_major=java,android_sdk=sdk,dependencies_cached=deps,offline=offline)
    return {
        'status':readiness.status,'reasons':list(readiness.reasons),'requiredGradle':req.version,
        'wrapperDistribution':req.distribution_name,'resolvedGradle':chosen,'supplied':supplied,
        'javaMajor':java,'androidSdk':sdk,'dependenciesCached':deps,'gradleHome':str(gradle_home),
    }

def main(argv=None) -> int:
    ap=argparse.ArgumentParser(prog='verify-aio')
    sub=ap.add_subparsers(dest='cmd', required=True)
    r=sub.add_parser('build-readiness'); r.add_argument('--root', default='.'); r.add_argument('--gradle-zip'); r.add_argument('--offline',action='store_true')
    b=sub.add_parser('build'); b.add_argument('--root', default='.'); b.add_argument('--offline', action='store_true'); b.add_argument('--gradle'); b.add_argument('--gradle-zip')
    sub.add_parser('device-preflight')
    sp=sub.add_parser('safe-plan'); sp.add_argument('--serial', required=True); sp.add_argument('--user', type=int, default=0)
    rs=sub.add_parser('run-safe'); rs.add_argument('--serial', required=True); rs.add_argument('--user', type=int, default=0)
    args=ap.parse_args(argv)
    if args.cmd=='device-preflight':
        from dataclasses import asdict
        from .device import run_device_preflight
        data=asdict(run_device_preflight())
        print(json.dumps(data,indent=2,sort_keys=True)); return 0 if data['status']=='READY' else 2
    if args.cmd=='safe-plan':
        from .device import plan_permission_tests, plan_component_tests, plan_full_offline_smoke
        commands=plan_permission_tests(args.serial,user_id=args.user)+plan_component_tests(args.serial,user_id=args.user)+plan_full_offline_smoke(args.serial)
        print(json.dumps({'mode':'SAFE','commands':commands},indent=2)); return 0
    if args.cmd=='run-safe':
        from .device import execute_safe_verification, SubprocessRunner
        try:
            data=execute_safe_verification(SubprocessRunner(),args.serial,user_id=args.user)
        except FileNotFoundError:
            data={'status':'BLOCKED','mode':'SAFE','reasons':['ADB_NOT_FOUND'],'results':[]}
        print(json.dumps(data,indent=2,sort_keys=True)); return 0 if data['status']=='PASS' else 2
    root=Path(args.root).resolve()
    if args.cmd=='build-readiness':
        data=_collect(root,args.gradle_zip,args.offline or True)
        print(json.dumps(data, indent=2, sort_keys=True)); return 0 if data['status']=='BUILD_OFFLINE_READY' else 2
    data=_collect(root,args.gradle_zip,args.offline)
    # Explicit --gradle may be used only if it reports the exact wrapper version.
    gradle=args.gradle
    if gradle:
        req=parse_wrapper_requirement(root/'gradle/wrapper/gradle-wrapper.properties')
        from .build_resolver import _gradle_version_from_executable
        version=_gradle_version_from_executable(Path(gradle))
        if version!=req.version:
            print(json.dumps({'status':'BUILD_BLOCKED','reasons':['GRADLE_VERSION_MISMATCH'],'requiredGradle':req.version,'resolvedGradleVersion':version},indent=2)); return 2
    elif data.get('resolvedGradle') and data['resolvedGradle'].get('source')!='SUPPLIED_ZIP':
        gradle=data['resolvedGradle']['path']
    elif not args.offline:
        gradle=str(root/'gradlew')
    else:
        print(json.dumps(data,indent=2,sort_keys=True)); return 2
    if args.offline and data['status']!='BUILD_OFFLINE_READY':
        print(json.dumps(data,indent=2,sort_keys=True)); return 2
    cmd=build_gradle_command(gradle, offline=args.offline)
    proc=subprocess.run(cmd,cwd=root)
    return proc.returncode

if __name__=='__main__': raise SystemExit(main())

from __future__ import annotations
import argparse,json,sys
from pathlib import Path
from .builder import create_bundle_dir,add_apk_file,validate_bundle
from .signing import sign_manifest,verify_manifest

def emit(obj,code=0):
    print(json.dumps(obj,ensure_ascii=False,sort_keys=True)); return code

def main(argv=None):
    ap=argparse.ArgumentParser(prog='bundle-tool'); sub=ap.add_subparsers(dest='cmd',required=True)
    c=sub.add_parser('create'); c.add_argument('bundle'); c.add_argument('--bundle-id',required=True); c.add_argument('--key-id',required=True)
    a=sub.add_parser('add-apk'); a.add_argument('bundle'); a.add_argument('apk'); a.add_argument('--package'); a.add_argument('--version-code',type=int); a.add_argument('--role',choices=['base','split'],required=True); a.add_argument('--split-name')
    for name in ('validate','inspect'):
        p=sub.add_parser(name); p.add_argument('bundle')
    s=sub.add_parser('sign'); s.add_argument('bundle'); s.add_argument('--key',required=True)
    v=sub.add_parser('verify'); v.add_argument('bundle'); v.add_argument('--trust-store',required=True)
    p=sub.add_parser('preview'); p.add_argument('bundle'); p.add_argument('--api',type=int,required=True); p.add_argument('--mode',required=True)
    ns=ap.parse_args(argv); b=Path(getattr(ns,'bundle','.') )
    if ns.cmd=='create': create_bundle_dir(b,bundle_id=ns.bundle_id,key_id=ns.key_id); return emit({'status':'CREATED','bundle':str(b)})
    if ns.cmd=='add-apk':
        from .apk_inspector import inspect_apk
        inspection=inspect_apk(Path(ns.apk))
        package_name=ns.package or inspection.package_name
        version_code=ns.version_code if ns.version_code is not None else inspection.version_code
        if not package_name or version_code is None:
            return emit({'status':'APK_METADATA_UNAVAILABLE','inspectionStatus':inspection.status,'required':['--package','--version-code']},2)
        try:
            item=add_apk_file(b,Path(ns.apk),package_name=package_name,version_code=version_code,role=ns.role,split_name=ns.split_name,inspection=inspection)
        except ValueError as e:
            return emit({'status':'APK_METADATA_MISMATCH','detail':str(e)},2)
        return emit({'status':'ADDED','file':item,'inspectionStatus':inspection.status})
    if ns.cmd=='validate':
        r=validate_bundle(b); return emit({'status':'VALID' if r.ok else 'INVALID','codes':list(r.codes)},0 if r.ok else 2)
    if ns.cmd=='inspect':
        m=json.loads((b/'manifest.json').read_text()); r=validate_bundle(b); return emit({'status':'VALID' if r.ok else 'INVALID','manifest':m,'codes':list(r.codes)},0 if r.ok else 2)
    if ns.cmd=='sign':
        sign_manifest(Path(ns.key),b/'manifest.json',b/'manifest.sig'); return emit({'status':'SIGNED'})
    if ns.cmd=='verify':
        r=verify_manifest(Path(ns.trust_store),b/'manifest.json',b/'manifest.sig'); return emit({'status':r.status,'keyId':r.key_id},0 if r.status=='VERIFIED' else 2)
    if ns.cmd=='preview':
        r=validate_bundle(b); m=json.loads((b/'manifest.json').read_text()); min_api=int(m.get('minimumAndroidApi',0)); modes=m.get('allowedModes',[])
        issues=list(r.codes)
        if ns.api<min_api: issues.append('ANDROID_API_TOO_OLD')
        if modes and ns.mode not in modes: issues.append('MODE_NOT_ALLOWED')
        return emit({'status':'HOST_PREVIEW_VALID' if not issues else 'HOST_PREVIEW_INVALID','issues':issues,'deviceCapabilityStatus':'DEVICE_VERIFICATION_REQUIRED'},0 if not issues else 2)
    return 2

if __name__=='__main__': raise SystemExit(main())

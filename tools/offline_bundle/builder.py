from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import hashlib, json, shutil

@dataclass(frozen=True)
class ValidationResult:
    ok: bool
    codes: tuple[str, ...]

def _sha256(path: Path)->str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for c in iter(lambda:f.read(1024*1024),b''): h.update(c)
    return h.hexdigest()

def _load(bundle:Path)->dict:
    return json.loads((bundle/'manifest.json').read_text(encoding='utf-8'))

def _save(bundle:Path,m:dict)->None:
    (bundle/'manifest.json').write_text(json.dumps(m,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf-8')

def create_bundle_dir(bundle:Path,*,bundle_id:str,key_id:str)->None:
    bundle=Path(bundle); (bundle/'apps').mkdir(parents=True,exist_ok=True); (bundle/'policies').mkdir(exist_ok=True); (bundle/'metadata').mkdir(exist_ok=True)
    _save(bundle,{'schemaVersion':1,'bundleId':bundle_id,'keyId':key_id,'packages':[]})

def add_apk_file(bundle:Path,source:Path,*,package_name:str,version_code:int,role:str,split_name:str|None=None,inspection=None)->dict:
    bundle=Path(bundle); source=Path(source)
    m=_load(bundle)
    if inspection is None:
        try:
            from .apk_inspector import inspect_apk
            inspection=inspect_apk(source)
        except Exception:
            inspection=None
    if inspection is not None and getattr(inspection,'status',None)=='INSPECTED':
        if inspection.package_name and inspection.package_name != package_name:
            raise ValueError(f'PACKAGE_NAME_MISMATCH:{inspection.package_name}!={package_name}')
        if inspection.version_code is not None and int(inspection.version_code) != int(version_code):
            raise ValueError(f'VERSION_MISMATCH:{inspection.version_code}!={version_code}')
    pkg=next((p for p in m['packages'] if p['packageName']==package_name),None)
    if pkg is None:
        pkg={'packageName':package_name,'versionCode':int(version_code),'files':[]}; m['packages'].append(pkg)
    if inspection is not None:
        pkg['inspectionStatus']=getattr(inspection,'status','UNAVAILABLE')
        for src_key,dst_key in [('version_name','versionName'),('min_sdk','minSdk'),('target_sdk','targetSdk'),('certificate_sha256','signingCertificateSha256')]:
            value=getattr(inspection,src_key,None)
            if value is not None: pkg[dst_key]=value
    sub='base.apk' if role=='base' else f"split_{split_name or 'unnamed'}.apk"
    dest=bundle/'apps'/package_name/sub; dest.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source,dest)
    item={'role':role,'path':dest.relative_to(bundle).as_posix(),'sha256':_sha256(dest)}
    if split_name: item['splitName']=split_name
    pkg['files'].append(item); _save(bundle,m); return item

def validate_bundle(bundle:Path)->ValidationResult:
    bundle=Path(bundle); m=_load(bundle); codes=[]
    for pkg in m.get('packages',[]):
        files=pkg.get('files',[])
        if not any(f.get('role')=='base' for f in files): codes.append('BASE_APK_MISSING')
        seen=set()
        for f in files:
            if f.get('role')=='split':
                sn=f.get('splitName')
                if sn in seen: codes.append('DUPLICATE_SPLIT')
                seen.add(sn)
            p=bundle/f['path']
            if not p.is_file(): codes.append('PACKAGE_FILE_MISSING'); continue
            if _sha256(p)!=f.get('sha256'): codes.append('APK_HASH_MISMATCH')
    uniq=tuple(dict.fromkeys(codes))
    return ValidationResult(not uniq, uniq)

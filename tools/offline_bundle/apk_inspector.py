from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import shutil, subprocess, re

@dataclass(frozen=True)
class ApkInspection:
    status:str
    package_name:str|None=None
    version_code:int|None=None
    version_name:str|None=None
    min_sdk:int|None=None
    target_sdk:int|None=None
    split_name:str|None=None
    certificate_sha256:str|None=None

class Runner:
    def run(self,args): return subprocess.run(args,text=True,capture_output=True)

def discover_tools(path_env: str|None=None)->dict[str,str]:
    out={}
    for name in ('apkanalyzer','apksigner','aapt2','aapt'):
        p=shutil.which(name,path=path_env)
        if p: out[name]=p
    return out

def _call(runner, args):
    p=runner.run(args)
    return p.stdout.strip() if p.returncode==0 else None

def _as_int(value):
    try: return int(str(value).strip())
    except Exception: return None

def inspect_apk(path: Path, *, runner=None, tools:dict[str,str]|None=None)->ApkInspection:
    path=Path(path); runner=runner or Runner(); tools=tools if tools is not None else discover_tools()
    analyzer=tools.get('apkanalyzer')
    if not analyzer:
        return ApkInspection('UNAVAILABLE')
    pkg=_call(runner,[analyzer,'manifest','application-id',str(path)])
    if not pkg: return ApkInspection('UNAVAILABLE')
    vc=_as_int(_call(runner,[analyzer,'manifest','version-code',str(path)]))
    vn=_call(runner,[analyzer,'manifest','version-name',str(path)])
    mn=_as_int(_call(runner,[analyzer,'manifest','min-sdk',str(path)]))
    tg=_as_int(_call(runner,[analyzer,'manifest','target-sdk',str(path)]))
    split=None
    # apkanalyzer currently has no stable one-word split-name command across versions;
    # leave unavailable rather than derive it from the filename.
    cert=None
    apksigner=tools.get('apksigner')
    if apksigner:
        out=_call(runner,[apksigner,'verify','--print-certs',str(path)]) or ''
        m=re.search(r'certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)',out)
        if m: cert=m.group(1).replace(':','').lower()
    return ApkInspection('INSPECTED',pkg,vc,vn,mn,tg,split,cert)

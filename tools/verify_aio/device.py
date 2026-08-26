from __future__ import annotations
from dataclasses import dataclass
import re, subprocess

@dataclass(frozen=True)
class DevicePreflight:
    status:str
    reasons:tuple[str,...]=()
    serial:str|None=None
    api_level:int|None=None
    users:tuple[int,...]=()
    device_owner:str|None=None
    profile_owner_user_ids:tuple[int,...]=()
    dpc_version:str|None=None

class SubprocessRunner:
    def run(self,args):
        return subprocess.run(args,text=True,capture_output=True)

def _package_from_component_line(line:str)->str|None:
    m=re.search(r'ComponentInfo\{([^/}]+)/',line)
    return m.group(1) if m else None

def run_device_preflight(runner=None)->DevicePreflight:
    runner=runner or SubprocessRunner()
    try:
        d=runner.run(['adb','devices'])
    except FileNotFoundError:
        return DevicePreflight('BLOCKED',('ADB_NOT_FOUND',))
    serials=[]
    if d.returncode==0:
        for line in d.stdout.splitlines()[1:]:
            parts=line.strip().split()
            if len(parts)>=2 and parts[1]=='device': serials.append(parts[0])
    if not serials:
        return DevicePreflight('BLOCKED',('ADB_DEVICE_MISSING',))
    serial=serials[0]; base=['adb','-s',serial,'shell']
    api_r=runner.run(base+['getprop','ro.build.version.sdk'])
    try: api=int(api_r.stdout.strip())
    except Exception: api=None
    users_r=runner.run(base+['pm','list','users'])
    users=tuple(int(x) for x in re.findall(r'UserInfo\{(\d+):',users_r.stdout))
    pol=runner.run(base+['dumpsys','device_policy'])
    lines=pol.stdout.splitlines(); do=None; pos=[]
    section=None
    for line in lines:
        if 'Device Owner:' in line: section=('do',None); continue
        m=re.search(r'Profile Owner \(User (\d+)\):',line)
        if m: section=('po',int(m.group(1))); continue
        pkg=_package_from_component_line(line)
        if pkg and section:
            if section[0]=='do' and do is None: do=pkg
            elif section[0]=='po': pos.append(section[1])
            section=None
    runner.run(base+['pm','path','io.dpcaio.app'])
    pkg=runner.run(base+['dumpsys','package','io.dpcaio.app'])
    vm=re.search(r'versionName=([^\s]+)',pkg.stdout); version=vm.group(1) if vm else None
    reasons=[]
    if api is None: reasons.append('ANDROID_API_UNKNOWN')
    return DevicePreflight('READY' if not reasons else 'BLOCKED',tuple(reasons),serial,api,users,do,tuple(sorted(set(pos))),version)

TEST_PACKAGE='io.dpcaio.testtarget'

def _adb_shell(serial:str,*parts:str)->list[str]:
    return ['adb','-s',serial,'shell',*parts]

def _verify_broadcast(serial:str, user_id:int, action:str, extras:list[str])->list[str]:
    return _adb_shell(serial,'am','broadcast','--user',str(user_id),'-a',action,'-n','io.dpcaio.app/.VerificationCommandReceiver',*extras)

def plan_permission_tests(serial:str, *, user_id:int)->list[list[str]]:
    p=TEST_PACKAGE; permission='android.permission.CAMERA'
    out=[]
    for desired in ('GRANTED','DENIED','DEFAULT'):
        out.append(_verify_broadcast(serial,user_id,'io.dpcaio.action.VERIFY_PERMISSION',[
            '--es','packageName',p,'--es','permission',permission,'--es','desired',desired,'--ei','targetUserId',str(user_id)
        ]))
    return out

def plan_component_tests(serial:str, *, user_id:int)->list[list[str]]:
    out=[]
    for desired in ('DISABLED','ENABLED','DEFAULT'):
        out.append(_verify_broadcast(serial,user_id,'io.dpcaio.action.VERIFY_COMPONENT',[
            '--es','desired',desired
        ]))
    return out

def plan_full_offline_smoke(serial:str)->list[list[str]]:
    # Safe plan: do not alter radios/network. The app-side FULL_OFFLINE instrumentation
    # is responsible for asserting zero backend calls while executing a local test bundle.
    return [
        _adb_shell(serial,'am','start','-n','io.dpcaio.app/.OfflineSetupActivity','--es','verificationMode','FULL_OFFLINE_SMOKE'),
        _adb_shell(serial,'dumpsys','package','io.dpcaio.app'),
        ['adb','-s',serial,'logcat','-d','-s','DpcAioOffline:*'],
    ]

def _parse_broadcast_data(stdout: str) -> dict:
    import json, re
    m=re.search(r'data="((?:\\.|[^"])*)"',stdout)
    if not m: return {}
    raw=m.group(1).replace('\\"','"').replace('\\\\','\\')
    try: return json.loads(raw)
    except Exception: return {}

def execute_safe_verification(runner, serial: str, *, user_id: int) -> dict:
    commands=plan_permission_tests(serial,user_id=user_id)+plan_component_tests(serial,user_id=user_id)
    results=[]
    for command in commands:
        proc=runner.run(command)
        payload=_parse_broadcast_data(getattr(proc,'stdout','') or '')
        verified=proc.returncode==0 and payload.get('status')=='VERIFIED'
        desired=None
        if '--es' in command:
            for i in range(len(command)-2):
                if command[i]=='--es' and command[i+1]=='desired': desired=command[i+2]
        kind='permission' if 'io.dpcaio.action.VERIFY_PERMISSION' in command else 'component'
        results.append({'kind':kind,'desired':desired,'status':'PASS' if verified else 'FAIL','observed':payload,'returnCode':proc.returncode})
    overall='PASS' if results and all(r['status']=='PASS' for r in results) else 'FAIL'
    return {'status':overall,'mode':'SAFE','results':results}

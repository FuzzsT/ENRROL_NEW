from __future__ import annotations
from dataclasses import dataclass, field
import json
from enum import Enum
from datetime import datetime, timezone
from .report import _redact

class StepStatus(str, Enum):
    PASS='PASS'; FAIL='FAIL'; SKIP='SKIP'; BLOCKED='BLOCKED'; NOT_RUN='NOT_RUN'

_VERIFY_MAP={
    'source':'sourceVerified', 'apkBuild':'apkBuildVerified', 'apkInstall':'apkInstalledVerified',
    'deviceOwner':'deviceOwnerVerified', 'workProfile':'workProfileVerified', 'fullOffline':'fullOfflineVerified',
    'permissions':'permissionsVerified', 'components':'componentsVerified',
}

@dataclass
class ReleaseVerification:
    version:str
    steps:dict=field(default_factory=dict)
    metadata:dict=field(default_factory=dict)
    generated_at:str=field(default_factory=lambda:datetime.now(timezone.utc).isoformat())
    def set_step(self,name:str,status:str|StepStatus,**details):
        value=StepStatus(status).value
        self.steps[name]={'status':value,**details}
    def set_metadata(self,value:dict): self.metadata=_redact(value)
    def to_dict(self):
        verification={field_name:(self.steps.get(step,{}).get('status')=='PASS') for step,field_name in _VERIFY_MAP.items()}
        return _redact({'version':self.version,'generatedAt':self.generated_at,'steps':self.steps,'verification':verification,'metadata':self.metadata})
    def to_json(self): return json.dumps(self.to_dict(),ensure_ascii=False,sort_keys=True,indent=2)

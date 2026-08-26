from __future__ import annotations
from dataclasses import dataclass, field, asdict
import json
from datetime import datetime, timezone

_SECRET_KEYS={'token','password','secret','privatekey','private_key','authorization','authheader','auth_header','kpekey','kpe_key'}

def _redact(value):
    if isinstance(value,dict):
        out={}
        for k,v in value.items():
            norm=str(k).lower().replace('-','').replace(' ','')
            if norm in _SECRET_KEYS or any(s in norm for s in ('password','token','privatekey','authorization')):
                continue
            out[k]=_redact(v)
        return out
    if isinstance(value,list): return [_redact(v) for v in value]
    return value

@dataclass
class VerificationResult:
    test_id:str
    expected:object
    observed:object
    status:str
    details:dict=field(default_factory=dict)

@dataclass
class VerificationReport:
    version:str
    results:list[VerificationResult]=field(default_factory=list)
    generated_at:str=field(default_factory=lambda:datetime.now(timezone.utc).isoformat())
    def add(self,result:VerificationResult): self.results.append(result)
    def to_dict(self):
        return _redact({'version':self.version,'generatedAt':self.generated_at,'results':[asdict(r) for r in self.results]})
    def to_json(self): return json.dumps(self.to_dict(),ensure_ascii=False,indent=2,sort_keys=True)

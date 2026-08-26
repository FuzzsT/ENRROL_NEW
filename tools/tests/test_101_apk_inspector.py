#!/usr/bin/env python3
from pathlib import Path
import sys,tempfile
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT))
from tools.offline_bundle.apk_inspector import inspect_apk

class FakeRunner:
    def __init__(self,m): self.m=m
    def run(self,args):
        key=' '.join(args); out=self.m.get(key,('',1))
        return type('R',(),{'stdout':out[0],'stderr':'','returncode':out[1]})()

with tempfile.TemporaryDirectory() as td:
    apk=Path(td)/'looks.like.a.package.apk'; apk.write_bytes(b'not-real')
    u=inspect_apk(apk,runner=FakeRunner({}),tools={})
    assert u.status=='UNAVAILABLE' and u.package_name is None, u
    m={
      f'apkanalyzer manifest application-id {apk}':('com.example.real\n',0),
      f'apkanalyzer manifest version-code {apk}':('42\n',0),
      f'apkanalyzer manifest version-name {apk}':('2.0\n',0),
      f'apkanalyzer manifest min-sdk {apk}':('29\n',0),
      f'apkanalyzer manifest target-sdk {apk}':('37\n',0),
    }
    i=inspect_apk(apk,runner=FakeRunner(m),tools={'apkanalyzer':'apkanalyzer'})
    assert i.status=='INSPECTED' and i.package_name=='com.example.real' and i.version_code==42, i
print('test_101_apk_inspector: PASS')

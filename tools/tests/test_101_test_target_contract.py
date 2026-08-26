#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
settings=(ROOT/'settings.gradle.kts').read_text()
assert '":aio-test-target"' in settings, 'test target not registered'
manifest=ROOT/'apps/aio-test-target/src/main/AndroidManifest.xml'
assert manifest.is_file(), manifest
text=manifest.read_text()
for token in ('io.dpcaio.testtarget','.TestEnabledActivity','.TestDisabledActivity','.TestAlias','.TestReceiver','.TestService','android.permission.CAMERA','android.permission.POST_NOTIFICATIONS','android.permission.READ_CONTACTS'):
    assert token in text, token
root=ET.parse(manifest).getroot(); ns='{http://schemas.android.com/apk/res/android}'
app=root.find('application'); assert app is not None
for node in list(app):
    if node.tag.endswith(('activity','receiver','service','activity-alias')):
        exported=node.get(ns+'exported')
        assert exported in ('false',None), (node.tag,node.attrib)
print('test_101_test_target_contract: PASS')

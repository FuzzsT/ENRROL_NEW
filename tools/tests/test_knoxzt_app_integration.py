from pathlib import Path
root=Path(__file__).resolve().parents[2]
settings=(root/'settings.gradle.kts').read_text()
app=(root/'apps/dpc/app/build.gradle.kts').read_text()
manifest=(root/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
texts='\n'.join(p.read_text(errors='ignore') for p in (root/'apps/dpc/app/src').rglob('*.kt'))
for module in ['knox-zt-core','knox-zt-android']:
    assert f'":{module}"' in settings, module
    assert f'project(":{module}")' in app, module
assert 'KnoxZtStartupController' in texts
assert 'KnoxZtRecoveryManager' in texts
assert 'KnoxZtManagerActivity' in texts
assert '.KnoxZtManagerActivity' in manifest
assert 'PACKAGE_INSTALL_RESULT' in manifest or 'KnoxZtRecoveryReceiver' in texts
print('test_knoxzt_app_integration: PASS')

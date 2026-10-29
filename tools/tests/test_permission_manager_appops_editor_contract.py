from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
activity = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppOpsVerifiedEditorActivity.kt').read_text()
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text()

required = [
    'Verified AppOps editor',
    'AppOp name',
    'AppOp mode',
    'Apply AppOp + verify',
    'allow',
    'ignore',
    'deny',
    'default',
    'getAppOps(',
    'setAppOp(',
    'before=',
    'after=',
    'verified=',
]

missing = [needle for needle in required if needle not in activity]
assert not missing, f'Missing AppOps editor contract markers: {missing}'
assert '.AppOpsVerifiedEditorActivity' in manifest
assert 'android:exported="false"' in manifest

print('PERMISSION_MANAGER_APPOPS_EDITOR_CONTRACT: PASS')

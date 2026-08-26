from pathlib import Path
root=Path(__file__).resolve().parents[2]
module=root/'apps/dpc/modules/knox/zero-trust/android'
assert module.exists(), 'missing knox-zt-android'
texts='\n'.join(p.read_text(errors='ignore') for base in [root/'apps/dpc/modules/knox/zero-trust/core', module, root/'apps/dpc/integrations/shizuku'] for p in base.rglob('*') if p.is_file())
for required in [
    'com.samsung.android.knox.zt.framework',
    'enableSystemApp',
    'installExistingPackage',
    'MATCH_UNINSTALLED_PACKAGES',
    'GET_SIGNING_CERTIFICATES',
    'MessageDigest.getInstance("SHA-256")',
    'HttpURLConnection',
    'AndroidPackageInstallerAdapter',
    'KnoxZtInstallStatusReceiver',
    'setPackageEnabled',
    'installExistingPackage',
]:
    assert required in texts, required
print('test_knoxzt_android_contract: PASS')

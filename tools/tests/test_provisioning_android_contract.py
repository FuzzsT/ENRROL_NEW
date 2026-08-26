#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def must(path, token):
    path = ROOT / path
    if not path.exists(): raise AssertionError(f"missing {path.relative_to(ROOT)}")
    text = path.read_text(encoding='utf-8', errors='ignore')
    if token not in text: raise AssertionError(f"{path.relative_to(ROOT)} missing {token}")
    return text


def main():
    mode = must('apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeActivity.kt', 'EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES')
    for token in ['PROVISIONING_MODE_FULLY_MANAGED_DEVICE', 'EXTRA_PROVISIONING_MODE', 'RESULT_OK']:
        if token not in mode: raise AssertionError(f"ProvisioningModeActivity missing {token}")
    must('apps/dpc/app/src/main/kotlin/io/dpcaio/app/PolicyComplianceActivity.kt', 'setResult(RESULT_OK)')
    manifest = must('apps/dpc/app/src/main/AndroidManifest.xml', 'android.app.action.GET_PROVISIONING_MODE')
    for token in ['android.app.action.ADMIN_POLICY_COMPLIANCE', 'android.permission.BIND_DEVICE_ADMIN']:
        if token not in manifest: raise AssertionError(f"manifest missing {token}")
    print('test_provisioning_android_contract: PASS')


if __name__ == '__main__': main()

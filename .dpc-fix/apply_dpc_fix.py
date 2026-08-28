from pathlib import Path

ROOT = Path.cwd()

def replace_once(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text('utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one match, found {count}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1), 'utf-8')

activity = 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt'
replace_once(activity, 'app.packageName !in loadedActivities', '!loadedActivities.containsKey(app.packageName)')
replace_once(activity, 'app.packageName in loadedActivities', 'loadedActivities.containsKey(app.packageName)')

selector = 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeSelector.kt'
replace_once(selector, '''    ): Int? = when (requestedMode?.lowercase() ?: MODE_AUTO) {
        MODE_WORK_PROFILE -> managedProfileMode.takeIf { it in allowedModes }
        MODE_FULLY_MANAGED -> fullyManagedMode.takeIf { it in allowedModes }
        MODE_AUTO -> when {
            fullyManagedMode in allowedModes -> fullyManagedMode
            managedProfileMode in allowedModes -> managedProfileMode
            else -> null
        }
        else -> null
    }
''', '''    ): Int? {
        val effectiveAllowedModes = allowedModes.ifEmpty {
            listOf(managedProfileMode, fullyManagedMode)
        }
        return when (requestedMode?.lowercase() ?: MODE_AUTO) {
            MODE_WORK_PROFILE -> managedProfileMode.takeIf { it in effectiveAllowedModes }
            MODE_FULLY_MANAGED -> fullyManagedMode.takeIf { it in effectiveAllowedModes }
            MODE_AUTO -> when {
                fullyManagedMode in effectiveAllowedModes -> fullyManagedMode
                managedProfileMode in effectiveAllowedModes -> managedProfileMode
                else -> null
            }
            else -> null
        }
    }
''')

replace_once('tools/tests/test_component_manager_ui_contract.py',
             "'Enable & Launch','Preview Batch Enable','Preview Batch Disable','Preview Restore Default','Batch Preview','Apply batch',",
             "'Enable & Launch','Preview Enable','Preview Disable','Preview Restore','Batch Preview','Apply batch',")

replace_once('tools/tests/test_work_profile_provisioning.py',
'''    check(ProvisioningModeSelector.select("work-profile", listOf(fm), fm, mp) == null)
    check(ProvisioningModeSelector.select("auto", listOf(fm, mp), fm, mp) == fm)
''',
'''    check(ProvisioningModeSelector.select("work-profile", listOf(fm), fm, mp) == null)
    check(ProvisioningModeSelector.select("auto", listOf(fm, mp), fm, mp) == fm)
    // TestDPC-compatible fallback: absent/empty allowed modes means both core modes are available.
    check(ProvisioningModeSelector.select("work-profile", emptyList(), fm, mp) == mp)
    check(ProvisioningModeSelector.select("fully-managed", emptyList(), fm, mp) == fm)
    check(ProvisioningModeSelector.select("auto", emptyList(), fm, mp) == fm)
''')

replace_once('tools/tests/test_qr_release_bundle_builder.py',
             "'--apk-name',APK_NAME],text=True",
             "'--apk-name',APK_NAME,'--qr-type','both'],text=True")
replace_once('tools/tests/test_qr_release_bundle_builder.py',
             "    assert index['apk']==APK_NAME\n",
             "    assert index['apk']==APK_NAME\n    assert index['qrType']=='both'\n")

(ROOT / 'tools/tests/test_activity_explorer_concurrent_map_contract.py').write_text('''#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt").read_text("utf-8")

assert "app.packageName !in loadedActivities" not in source
assert "app.packageName in loadedActivities" not in source
assert "!loadedActivities.containsKey(app.packageName)" in source
assert "loadedActivities.containsKey(app.packageName)" in source
print("ACTIVITY_EXPLORER_CONCURRENT_MAP_CONTRACT: PASS")
''', 'utf-8')
host = ROOT / 'tools/run_host_tests.sh'
host_text = host.read_text('utf-8')
host_marker = 'python3 "$ROOT/tools/tests/test_component_manager_shizuku_contract.py"\n'
if host_text.count(host_marker) != 2:
    raise SystemExit(f'tools/run_host_tests.sh: expected two component-manager markers, found {host_text.count(host_marker)}')
host.write_text(host_text.replace(host_marker, host_marker + 'python3 "$ROOT/tools/tests/test_activity_explorer_concurrent_map_contract.py"\n'), 'utf-8')

for workflow in ('.github/workflows/build-aio-enrollment.yml', '.github/workflows/build-emergency-enrollment.yml'):
    replace_once(workflow,
                 'description: "Typ QR provisioning"',
                 'description: "QR code: both = QR1 Work Profile + QR2 Fully Managed; work-profile = QR1 only; fully-managed = QR2 only"')
    replace_once(workflow,
                 '      - name: Resolve QR selection\n        shell: bash\n',
                 '      - name: Resolve QR selection\n        id: qr\n        shell: bash\n')
    replace_once(workflow,
                 '          echo "DPC_AIO_QR_TYPE=$qr_type" >> "$GITHUB_ENV"\n          echo "QR selection: $qr_type"\n',
                 '          echo "DPC_AIO_QR_TYPE=$qr_type" >> "$GITHUB_ENV"\n          echo "qr_type=$qr_type" >> "$GITHUB_OUTPUT"\n          echo "QR selection: $qr_type"\n')
    replace_once(workflow,
                 '      GH_TOKEN: ${{ github.token }}\n',
                 '      GH_TOKEN: ${{ github.token }}\n      DPC_AIO_QR_TYPE: ${{ needs.build.outputs.qr_type }}\n')

replace_once('.github/workflows/build-aio-enrollment.yml',
             '      signing_mode: ${{ steps.signing.outputs.mode }}\n      app_version: ${{ steps.metadata.outputs.app_version }}\n',
             '      signing_mode: ${{ steps.signing.outputs.mode }}\n      qr_type: ${{ steps.qr.outputs.qr_type }}\n      app_version: ${{ steps.metadata.outputs.app_version }}\n')
replace_once('.github/workflows/build-emergency-enrollment.yml',
             '    outputs:\n      app_version: ${{ steps.metadata.outputs.app_version }}\n',
             '    outputs:\n      qr_type: ${{ steps.qr.outputs.qr_type }}\n      app_version: ${{ steps.metadata.outputs.app_version }}\n')

aio = ROOT / '.github/workflows/build-aio-enrollment.yml'
text = aio.read_text('utf-8')
bad = 'check dist/work-profile-validation.json --json dist/work-profile-provisioning.json --qr dist//work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist/$DPC_AIO_RELEASE_APK_NAME" --apksigner "$signer" --expected-apk-url "$DPC_AIO_PROVISIONING_APK_URL" --expected-mode work-profile'
good = 'check dist/work-profile-validation.json --json dist/work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist/$DPC_AIO_RELEASE_APK_NAME" --apksigner "$signer" --expected-apk-url "$DPC_AIO_PROVISIONING_APK_URL" --expected-mode work-profile'
if text.count(bad) != 1:
    raise SystemExit(f'build-aio-enrollment.yml: malformed validator match count={text.count(bad)}')
aio.write_text(text.replace(bad, good, 1), 'utf-8')

(ROOT / 'tools/tests/test_122_workflow_qr_choice_contract.py').write_text(r'''#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
main = (ROOT / ".github/workflows/build-aio-enrollment.yml").read_text("utf-8")
emergency = (ROOT / ".github/workflows/build-emergency-enrollment.yml").read_text("utf-8")
workflow_dir = ROOT / ".github/workflows"
gradle = (ROOT / "apps/dpc/app/build.gradle.kts").read_text("utf-8")

for token in [
    "qr_type:",
    'description: "QR code: both = QR1 Work Profile + QR2 Fully Managed; work-profile = QR1 only; fully-managed = QR2 only"',
    'type: choice', 'default: "both"', '- both', '- work-profile', '- fully-managed',
    'DPC_AIO_QR_TYPE', 'release_signing_password:',
]:
    assert token in main, token

assert main.count("release_signing_password:") == 1
assert "qr_type:" in emergency
assert "release_signing_password:" not in emergency
assert "DPC_AIO_QR_TYPE" in emergency
assert sorted(p.name for p in workflow_dir.glob("*.yml")) == [
    "build-aio-enrollment.yml", "build-emergency-enrollment.yml",
]

for text in (main, emergency):
    assert text.count("    inputs:\n      qr_type:\n") == 1
    assert "id: qr" in text
    assert 'echo "qr_type=$qr_type" >> "$GITHUB_OUTPUT"' in text
    assert 'qr_type: ${{ steps.qr.outputs.qr_type }}' in text
    assert 'DPC_AIO_QR_TYPE: ${{ needs.build.outputs.qr_type }}' in text

for token in ['providers.environmentVariable("DPC_AIO_QR_TYPE")', '"both"', '"work-profile"', '"fully-managed"']:
    assert token in gradle, token
assert 'selectedQrModes' in gradle
assert 'work-profile-validation.json' in main
assert 'device-owner-validation.json' in main
expected_wp = 'check dist/work-profile-validation.json --json dist/work-profile-provisioning.json --qr dist/work-profile-qr.png --apk "dist/$DPC_AIO_RELEASE_APK_NAME"'
assert expected_wp in main
assert 'dist//work-profile-provisioning.json' not in main
print("PASS: workflow QR choice contract")
''', 'utf-8')

(ROOT / '.github/workflows/verify-qr-choice-host-suite.yml').unlink(missing_ok=True)
print('APPLY_DPC_FIX: PASS')

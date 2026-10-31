#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / '.github' / 'workflows'
PR = WORKFLOWS / 'pr-android-ci.yml'
RELEASE = WORKFLOWS / 'build-aio-enrollment.yml'


def main():
    workflow_files = sorted(p.name for p in WORKFLOWS.glob('*.yml')) + sorted(p.name for p in WORKFLOWS.glob('*.yaml'))
    assert workflow_files == ['build-aio-enrollment.yml', 'pr-android-ci.yml'], workflow_files

    text = PR.read_text(encoding='utf-8')
    assert 'cancel-in-progress: true' in text
    assert 'name: Run PIN and app lock contracts' in text
    assert 'name: Run package export contracts' in text
    assert text.count('test_pin_guard_contract.py') == 1
    assert text.count('test_app_lock_pin_contract.py') == 1
    assert text.count('test_install_pin_guard_contract.py') == 1
    assert text.count('test_package_export_contract.py') == 1
    assert text.count('test_package_export_ui_contract.py') == 1
    assert 'name: Run app security contracts' not in text

    release = RELEASE.read_text(encoding='utf-8')
    assert 'AOSP Device Owner runtime smoke' in release
    assert 'Publish release assets' in release
    assert 'AOSP Device Owner runtime smoke' not in text
    assert 'Publish release assets' not in text

    print('test_workflow_organization_contract: PASS')


if __name__ == '__main__':
    main()

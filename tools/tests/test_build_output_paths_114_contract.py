#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = [
    ROOT / '.github/workflows/build-aio-enrollment.yml',
    ROOT / 'plugins/chatgpt-companion/skills/dpc-aio-build/SKILL.md',
    ROOT / 'plugins/chatgpt-companion/skills/dpc-aio-enrollment/SKILL.md',
    ROOT / 'plugins/chatgpt-companion/skills/dpc-aio-enrollment/references/provisioning-contract.md',
    ROOT / 'docs/PROVISIONING-QR.md',
    ROOT / 'docs/superpowers/plans/2026-08-22-dpc-aio-chatgpt-plugin-full-upgrade.md',
]
errors = []
for path in FILES:
    text = path.read_text(encoding='utf-8')
    if 'apps/dpc/build/outputs' in text:
        errors.append(f'{path.relative_to(ROOT)} still points at obsolete apps/dpc/build/outputs')
workflow = FILES[0].read_text(encoding='utf-8')
if 'apps/dpc/app/build/outputs/apk/enterprise/release' not in workflow:
    errors.append('unified enrollment workflow does not locate APK under app module build outputs')
if 'apps/dpc/app/build/outputs/provisioning/enterprise/release' not in workflow:
    errors.append('unified enrollment workflow does not collect provisioning under app module build outputs')
if errors:
    print('BUILD_OUTPUT_PATHS_114_CONTRACT: FAIL')
    for error in errors:
        print(f'- {error}')
    raise SystemExit(1)
print('BUILD_OUTPUT_PATHS_114_CONTRACT: PASS')

#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
dash = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text(encoding='utf-8')
for token in [
    'DpcUiShell',
    'Enrollment',
    'Apps & Components',
    'Device & Policy',
    'Security & Credentials',
    'Network',
    'Work Profile / COPE',
    'OEM / Knox',
    'Diagnostics',
    'Advanced / Lab',
    'Activity Manager 3.0',
    'Favorites',
    'favoritesOnly',
]:
    assert token in dash, token
print('PASS: categorized dashboard menu contract')

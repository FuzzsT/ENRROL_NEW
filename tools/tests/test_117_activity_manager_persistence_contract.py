#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
store = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityFavoriteStore.kt'
assert store.exists(), 'ActivityFavoriteStore.kt missing'
text = store.read_text(encoding='utf-8')
for token in [
    'createDeviceProtectedStorageContext()',
    'getSharedPreferences(',
    'app:',
    'activity:',
    'toggleAppFavorite',
    'toggleActivityFavorite',
    'createGroup',
    'renameGroup',
    'deleteGroup',
    'setMembership',
    'members(',
]:
    assert token in text, token
for forbidden in ['ComponentControlRouter', 'setComponentEnabledSetting', 'startActivity(']:
    assert forbidden not in text, f'favorite store must not mutate/launch: {forbidden}'

inventory = (ROOT / 'apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt').read_text(encoding='utf-8')
assert 'fun listApps(' in inventory
assert 'InstalledAppDescriptor' in inventory
assert 'getInstalledApplications' in inventory or 'getInstalledPackages' in inventory
print('PASS: activity manager persistence/inventory contract')

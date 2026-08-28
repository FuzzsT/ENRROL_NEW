#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text('utf-8')
dashboard = (APP / 'AioDashboardActivity.kt').read_text('utf-8')

center_path = APP / 'TestDpcParityCenterActivity.kt'
detail_path = APP / 'TestDpcParityDetailActivity.kt'
fav_path = APP / 'TestDpcParityFavoriteStore.kt'
for path in [center_path, detail_path, fav_path]:
    assert path.is_file(), f'missing {path.name}'

center = center_path.read_text('utf-8')
detail = detail_path.read_text('utf-8')
fav = fav_path.read_text('utf-8')

for token in ['Google TestDPC parity', 'TestDPC Parity Center', 'TestDpcParityCenterActivity::class.java']:
    assert token in dashboard, f'dashboard missing {token}'
for activity in ['.TestDpcParityCenterActivity', '.TestDpcParityDetailActivity']:
    assert activity in manifest, f'manifest missing {activity}'
assert manifest.count('android:exported="false"') >= 2

for token in ['dpc_aio_testdpc_parity_favorites_v1', 'parity:', 'createDeviceProtectedStorageContext']:
    assert token in fav, f'favorite store missing {token}'
assert 'ActivityFavoriteStore' not in fav

for token in [
    'TestDpcParityCatalog.entries', 'TestDpcCapabilityResolver.resolve', 'AndroidParityRuntimeFactsProvider',
    'catalogued', 'implemented', 'available on this device',
    'Available', 'Unsupported', 'Deprecated', 'Device Owner', 'Profile Owner', 'COPE', 'Implementation state',
    'googleTitle', 'testDpcKey', 'category', 'description', 'EXTRA_PARITY_ID',
    'TestDpcParityFavoriteStore', 'DpcUiShell.scroll', 'setPaddingDp'
]:
    assert token in center, f'parity center missing {token}'

for token in [
    'EXTRA_PARITY_ID', 'TestDpcParityCatalog', 'TestDpcCapabilityResolver.resolve', 'AndroidParityRuntimeFactsProvider',
    'TestDPC key:', 'Google title:', 'Implementation state:', 'Availability:', 'Owner:', 'Minimum API:',
    'Features:', 'Replacement:', 'Open existing DPC-AIO screen', 'Execute', 'DpcUiShell.scroll', 'setPaddingDp'
]:
    assert token in detail, f'parity detail missing {token}'
assert 'DevicePolicyManager' not in detail, 'detail activity must not call DPM directly'

print('test_125_parity_ui_contract: PASS')

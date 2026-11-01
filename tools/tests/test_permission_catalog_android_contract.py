#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def need(path, tokens):
    text=(ROOT/path).read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path} missing {token}'

def main():
    catalog_path='apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionCatalog.kt'
    need(catalog_path, [
        'getAllPermissionGroups(', 'queryPermissionsByGroup(', 'GET_PERMISSIONS',
        'Manifest.permission::class.java.fields', 'Manifest.permission_group::class.java.fields'
    ])
    catalog=(ROOT/catalog_path).read_text(encoding='utf-8')
    assert 'PermissionInfo.PROTECTION_MASK_BASE' not in catalog, 'permission protection classification must not use deprecated PROTECTION_MASK_BASE'
    assert 'PermissionInfo.PROTECTION_MASK_FLAGS' not in catalog, 'permission protection classification must not use deprecated PROTECTION_MASK_FLAGS'
    assert 'PermissionInfoCompat.getProtection(info)' in catalog, 'permission protection classification must use PermissionInfoCompat.getProtection'
    assert 'PermissionInfoCompat.getProtectionFlags(info)' in catalog, 'permission protection classification must use PermissionInfoCompat.getProtectionFlags'
    assert 'info.protectionLevel' not in catalog, 'permission catalog must not read deprecated PermissionInfo.protectionLevel directly'
    assert '@Suppress("DEPRECATION")' not in catalog, 'permission catalog must not need broad deprecation suppression'
    assert 'rawProtectionLevel = PermissionInfoCompat.getProtection(info) or PermissionInfoCompat.getProtectionFlags(info)' in catalog, 'raw permission protection diagnostics must be reconstructed from compat base and flags'

    # User-facing Settings grants and hidden platform ACCESS_NOTIFICATIONS are distinct mechanisms.
    assert 'if (info.name in USER_SPECIAL_ACCESS)' in catalog, 'catalog must explicitly separate user-grantable special access from platform protection flags'
    marker='private val USER_SPECIAL_ACCESS = setOf('
    assert marker in catalog, 'USER_SPECIAL_ACCESS set missing'
    special_block=catalog.split(marker,1)[1].split(')',1)[0]
    assert 'android.permission.ACCESS_NOTIFICATIONS' not in special_block, 'ACCESS_NOTIFICATIONS is signature|privileged|appop, not notification-listener/user special access'
    hints=(ROOT/'apps/dpc/modules/permissions/core/src/main/kotlin/io/dpcaio/permission/PermissionCatalog.kt').read_text(encoding='utf-8')
    assert 'android.permission.ACCESS_NOTIFICATIONS' in hints, 'ACCESS_NOTIFICATIONS should remain discoverable as a platform permission hint'

    need('apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/SpecialAccessInspector.kt', [
        'OPSTR_GET_USAGE_STATS', 'canRequestPackageInstalls(', 'getEnabledListenerPackages('
    ])
    need('apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/SpecialAccessIntentFactory.kt', [
        'ACTION_MANAGE_OVERLAY_PERMISSION', 'ACTION_MANAGE_WRITE_SETTINGS',
        'ACTION_MANAGE_UNKNOWN_APP_SOURCES', 'ACTION_USAGE_ACCESS_SETTINGS',
        'ACTION_NOTIFICATION_LISTENER_SETTINGS'
    ])
    need('apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt', [
        'AndroidPermissionCatalog', 'PermissionCatalogClassifier', 'PermissionManager'
    ])
    print('test_permission_catalog_android_contract: PASS')

if __name__ == '__main__': main()

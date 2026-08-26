#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def need(path, tokens):
    text=(ROOT/path).read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path} missing {token}'

def main():
    need('apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionCatalog.kt', [
        'getAllPermissionGroups(', 'queryPermissionsByGroup(', 'GET_PERMISSIONS',
        'Manifest.permission::class.java.fields', 'Manifest.permission_group::class.java.fields'
    ])
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

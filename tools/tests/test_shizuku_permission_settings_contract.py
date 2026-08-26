#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def main():
    aidl=(ROOT/'apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl').read_text()
    impl=(ROOT/'apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt').read_text()
    for token in ['listPermissions()', 'grantRuntimePermission(', 'setAppOp(', 'getAppOps(', 'readSetting(', 'writeSetting(', 'deleteSetting(', 'listPermissionConfigFiles()', 'readPermissionConfig(', 'listSysconfigFiles()', 'readSysconfigFile(']:
        assert token in aidl, f'AIDL missing {token}'
    for token in ['/system/bin/pm', 'grant', '/system/bin/cmd', 'appops', '/system/bin/settings', 'get', 'put', 'delete', '/system/bin/ls', '/system/bin/cat', 'etc/permissions', 'etc/sysconfig']:
        assert token in impl, f'implementation missing {token}'
    assert 'ProcessBuilder(args)' in impl
    print('test_shizuku_permission_settings_contract: PASS')

if __name__ == '__main__': main()

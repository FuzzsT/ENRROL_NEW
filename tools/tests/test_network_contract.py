from pathlib import Path
root=Path(__file__).resolve().parents[2]
texts='\n'.join(p.read_text(errors='ignore') for base in ['apps/dpc/modules/network/core','apps/dpc/modules/network/android'] for p in (root/base).rglob('*') if p.is_file())
controller=(root/'apps/dpc/modules/network/android/src/main/kotlin/io/dpcaio/network/android/AlwaysOnVpnController.kt').read_text()
assert 'setGlobalPrivateDnsModeSpecifiedHost' in texts
assert 'setAlwaysOnVpnPackage' in texts
assert 'application/dns-message' in texts
assert 'runCatching {' in controller
assert 'dpm.getAlwaysOnVpnPackage(admin) == packageName' in controller
assert ': Boolean = dpm.setAlwaysOnVpnPackage' not in controller
for bad in ['/system/etc/hosts','su -c','KernelSU','CVE-2026']:
    assert bad not in texts
print('test_network_contract: PASS')

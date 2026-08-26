from pathlib import Path
root=Path(__file__).resolve().parents[2]
texts='\n'.join(p.read_text(errors='ignore') for base in ['apps/dpc/modules/network/core','apps/dpc/modules/network/android'] for p in (root/base).rglob('*') if p.is_file())
assert 'setGlobalPrivateDnsModeSpecifiedHost' in texts
assert 'setAlwaysOnVpnPackage' in texts
assert 'application/dns-message' in texts
for bad in ['/system/etc/hosts','su -c','KernelSU','CVE-2026']:
    assert bad not in texts
print('test_network_contract: PASS')

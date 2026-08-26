from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
base=ROOT/'apps/dpc/modules/oem-internals'
assert (base/'core').exists() and (base/'android').exists(), 'OEM internals modules missing'
manifest=(base/'android/src/main/AndroidManifest.xml').read_text('utf-8')
assert 'android:process=":oem_lab"' in manifest
assert 'android:exported="false"' in manifest
text='\n'.join(p.read_text('utf-8', errors='ignore') for p in base.rglob('*') if p.is_file() and p.suffix in {'.kt','.kts','.xml'})
for forbidden in ['VMRuntime.setHiddenApiExemptions','setHiddenApiExemptions','su -c','Runtime.getRuntime().exec("su','DexClassLoader','PathClassLoader']:
    assert forbidden not in text, forbidden
assert 'OemInternalCatalog' in text
assert 'OemCircuitBreaker' in text
assert 'timeoutMillis' in text
assert 'Class.forName' in text
# Raw hard-coded Binder transaction calls are forbidden.
import re
assert not re.search(r'\.transact\s*\(\s*\d+', text)
print('test_oem_internals_110_safety: PASS')

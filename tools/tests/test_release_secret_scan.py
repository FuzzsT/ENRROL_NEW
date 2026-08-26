from pathlib import Path
from tempfile import TemporaryDirectory
import importlib.util

root=Path(__file__).resolve().parents[2]
module_path=root/'tools/release/scan_release_tree.py'
spec=importlib.util.spec_from_file_location('release_scan', module_path)
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
with TemporaryDirectory() as td:
    p=Path(td)
    (p/'ok.txt').write_text('public test data')
    assert mod.scan(p) == []
    (p/'bad.pem').write_text('-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n')
    findings=mod.scan(p)
    assert any('private key material' in x for x in findings), findings
with TemporaryDirectory() as td:
    p=Path(td)
    (p/'scanner.py').write_text("MARKER = '-----BEGIN PRIVATE KEY-----'\n")
    assert mod.scan(p) == [], mod.scan(p)

with TemporaryDirectory() as td:
    p=Path(td); (p/'dpc-aio-lab-private.pem').write_text('not-even-a-key')
    findings=mod.scan(p)
    assert any('forbidden filename' in x for x in findings), findings
print('test_release_secret_scan: PASS')

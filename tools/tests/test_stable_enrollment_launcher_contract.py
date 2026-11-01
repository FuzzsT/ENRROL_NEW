from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ps1 = (ROOT / 'START-STABLE-ENROLLMENT.ps1').read_text(encoding='utf-8')
bat = (ROOT / 'START-STABLE-ENROLLMENT.bat').read_text(encoding='utf-8')
sh = (ROOT / 'START-STABLE-ENROLLMENT.sh').read_text(encoding='utf-8')
workflow = (ROOT / '.github/workflows/build-aio-enrollment.yml').read_text(encoding='utf-8')

for name, text in [('PowerShell', ps1), ('shell', sh)]:
    assert 'signing_mode=stable' in text, f'{name} launcher must explicitly request stable signing'
    assert 'signing_mode=emergency' not in text, f'{name} launcher must never request emergency signing'
    assert 'dpc-aio-continuous' in text, f'{name} launcher must point users at the stable rolling release'

assert 'START-STABLE-ENROLLMENT.ps1' in bat, 'BAT wrapper must invoke the stable PowerShell launcher'
assert 'signing_mode=stable' not in bat, 'BAT wrapper should delegate rather than duplicate workflow logic'

assert 'Prepare stable signer' in workflow, 'Unified workflow must retain the stable signer path'
assert 'DPC-AIO-enterpriseRelease-enrollment' in workflow, 'Stable path must build the normal enterprise release artifact'
assert 'release_tag="dpc-aio-continuous"' in workflow, 'Stable rolling release tag must remain dpc-aio-continuous'

print('STABLE_ENROLLMENT_LAUNCHER_CONTRACT: PASS')

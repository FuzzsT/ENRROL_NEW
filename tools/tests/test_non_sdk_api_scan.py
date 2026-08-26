from pathlib import Path
root=Path(__file__).resolve().parents[2]
patterns=[
    'com.android.internal',
    'dalvik.system.VMRuntime',
    'setHiddenApiExemptions',
    'hiddenApiExemptions',
]
findings=[]
for path in (root/'apps/dpc').rglob('*'):
    if not path.is_file() or path.suffix not in {'.kt','.java','.xml','.aidl','.cpp','.h'}:
        continue
    text=path.read_text('utf-8', errors='ignore')
    for pattern in patterns:
        if pattern in text:
            findings.append(f'{path.relative_to(root)}: {pattern}')
assert not findings, 'non-SDK bypass markers found:\n'+'\n'.join(findings)
print('test_non_sdk_api_scan: PASS')

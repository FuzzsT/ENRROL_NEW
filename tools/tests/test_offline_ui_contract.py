from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt'
manifest=(ROOT/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
dash=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text()
assert p.is_file(), p
text=p.read_text()
for needle in ['FULL OFFLINE','OFFLINE THEN SYNC','Import offline bundle','Preview deployment','Apply supported','OFFLINE_VERIFIED','FULL_OFFLINE_READY']:
    assert needle in text, needle
assert 'Ignore and finish' not in text
assert '.OfflineSetupActivity' in manifest
assert 'Full Offline Setup' in dash
print('OFFLINE_UI_CONTRACT: PASS')

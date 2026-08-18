from pathlib import Path
root=Path(__file__).resolve().parents[2]
app=(root/'app-dpc/build.gradle.kts').read_text()
assert 'implementation(project(":knox-mock-android"))' in app
manifest=(root/'knox-mock-android/src/main/AndroidManifest.xml').read_text()
assert 'protectionLevel="signature"' in manifest
assert 'android:exported="true"' in manifest
print('test_knox_mock_contract: PASS')

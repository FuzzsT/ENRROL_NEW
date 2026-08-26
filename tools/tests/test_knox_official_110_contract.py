from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
settings=(ROOT/'settings.gradle.kts').read_text('utf-8')
core=(ROOT/'apps/dpc/modules/knox/official/core/build.gradle.kts')
android=(ROOT/'apps/dpc/modules/knox/official/android/build.gradle.kts')
assert core.exists() and android.exists(), 'Knox official modules missing'
ct=core.read_text('utf-8'); at=android.read_text('utf-8')
assert ':knox-official-core' in settings and ':knox-official-android' in settings
assert 'KNOX_SDK_JAR' in at and 'compileOnly' in at
assert 'http://' not in at and 'https://' not in at
assert not list((ROOT/'apps/dpc/modules/knox/official').rglob('*.jar')), 'private Knox JAR bundled'
inv=(ROOT/'apps/dpc/modules/knox/official/android/src/main/kotlin/io/dpcaio/knox/official/android/KnoxSdkInventory.kt').read_text('utf-8')
assert 'UNSUPPORTED_DEVICE' in inv and 'Class.forName' in inv
print('test_knox_official_110_contract: PASS')

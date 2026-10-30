#!/usr/bin/env python3
from pathlib import Path
import importlib.util
import re
import tempfile
ROOT = Path(__file__).resolve().parents[2]
errors=[]
app=(ROOT/'apps/dpc/app/build.gradle.kts').read_text()
libs=(ROOT/'gradle/libs.versions.toml').read_text()
native=(ROOT/'apps/dpc/integrations/native-diagnostics/build.gradle.kts').read_text()
workflow=(ROOT/'.github/workflows/build-aio-enrollment.yml').read_text()
preflight=ROOT/'tools/android_build_preflight.py'
vm=re.search(r'versionName\s*=\s*"1\.1\.(\d+)"', app)
if not vm or int(vm.group(1)) < 1: errors.append('versionName must be 1.1.1 or later 1.1.x')
m=re.search(r'versionCode\s*=\s*(\d+)',app)
if not m or int(m.group(1))<22: errors.append('versionCode must be >=22')
if 'ndk = "28.2.13676358"' not in libs: errors.append('NDK must be pinned in version catalog')
if 'cmake = "3.22.1"' not in libs: errors.append('CMake must be pinned in version catalog')
if 'ndkVersion = libs.versions.ndk.get()' not in native: errors.append('native-diagnostics must use pinned NDK')
if 'version = libs.versions.cmake.get()' not in native: errors.append('native-diagnostics must use pinned CMake')
if not preflight.exists(): errors.append('android_build_preflight.py missing')
else:
    text=preflight.read_text()
    for marker in ['compileSdk','gradleWrapper','androidSdk','buildTools','ndk','cmake','signing']:
        if marker not in text: errors.append(f'preflight missing evidence key {marker}')
    if 'BUILD_READY' not in text or 'BUILD_BLOCKED' not in text: errors.append('preflight must expose stable readiness states')
    try:
        spec=importlib.util.spec_from_file_location('android_build_preflight_contract', preflight)
        if not spec or not spec.loader:
            raise RuntimeError('could not import android_build_preflight.py')
        module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            sdk=Path(tmp)
            minor=sdk/'platforms'/'android-37.2'
            minor.mkdir(parents=True)
            expected=minor/'android.jar'
            expected.write_bytes(b'contract')
            observed=module.resolve_compile_sdk_jar(sdk, '37')
            if observed != expected:
                errors.append(f'preflight must resolve canonical minor compileSdk path; got {observed!r}')
    except Exception as exc:
        errors.append(f'preflight minor compileSdk resolution failed: {exc}')
if 'android_build_preflight.py --require-signing' not in workflow: errors.append('workflow must execute build preflight before Gradle build')
if 'build-environment.json' not in workflow: errors.append('workflow must preserve build environment evidence')
if errors:
    raise SystemExit('BUILD_RUNTIME_READINESS_111_CONTRACT: FAIL\n- ' + '\n- '.join(errors))
print('BUILD_RUNTIME_READINESS_111_CONTRACT: PASS')

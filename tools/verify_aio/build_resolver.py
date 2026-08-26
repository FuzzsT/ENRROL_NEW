from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import hashlib
import zipfile
import tomllib
import os
from urllib.parse import urlparse
import re

@dataclass(frozen=True)
class BuildRequirement:
    version: str
    distribution_url: str
    distribution_name: str

@dataclass(frozen=True)
class BuildReadiness:
    status: str
    reasons: tuple[str, ...]


def parse_wrapper_requirement(path: Path) -> BuildRequirement:
    props = {}
    for raw in path.read_text(encoding='utf-8').splitlines():
        if not raw or raw.lstrip().startswith('#') or '=' not in raw:
            continue
        k, v = raw.split('=', 1)
        props[k.strip()] = v.strip().replace('\\:', ':')
    url = props.get('distributionUrl', '')
    name = Path(urlparse(url).path).name
    match = re.fullmatch(r'gradle-([0-9]+(?:\.[0-9]+)+)-(?:bin|all)\.zip', name)
    if not match:
        raise ValueError(f'Unsupported Gradle wrapper distribution: {url!r}')
    return BuildRequirement(match.group(1), url, name)


def choose_exact_gradle(requirement: BuildRequirement, candidates: list[dict]) -> dict | None:
    for candidate in candidates:
        if str(candidate.get('version')) == requirement.version:
            return candidate
    return None


def evaluate_readiness(
    *, requirement: BuildRequirement, gradle: dict | None, java_major: int | None,
    android_sdk: dict, dependencies_cached: bool, offline: bool,
) -> BuildReadiness:
    reasons: list[str] = []
    if gradle is None:
        reasons.append('GRADLE_EXACT_VERSION_MISSING')
    elif str(gradle.get('version')) != requirement.version:
        reasons.append('GRADLE_VERSION_MISMATCH')
    if java_major != 21:
        reasons.append('JAVA_VERSION_MISMATCH')
    if not android_sdk.get('sdk'):
        reasons.append('ANDROID_SDK_MISSING')
    elif not android_sdk.get('platform'):
        reasons.append('ANDROID_PLATFORM_MISSING')
    elif not android_sdk.get('build_tools'):
        reasons.append('BUILD_TOOLS_MISSING')
    if not dependencies_cached:
        reasons.append('DEPENDENCIES_MISSING')
    if not reasons:
        return BuildReadiness('BUILD_OFFLINE_READY' if offline else 'BUILD_READY', ())
    hard_blockers={'JAVA_VERSION_MISMATCH','ANDROID_SDK_MISSING','ANDROID_PLATFORM_MISSING','BUILD_TOOLS_MISSING','GRADLE_VERSION_MISMATCH'}
    if any(r in hard_blockers for r in reasons):
        return BuildReadiness('BUILD_BLOCKED', tuple(reasons))
    if offline and any(r in reasons for r in ('GRADLE_EXACT_VERSION_MISSING', 'DEPENDENCIES_MISSING')):
        return BuildReadiness('BUILD_ONLINE_REQUIRED', tuple(reasons))
    return BuildReadiness('BUILD_BLOCKED', tuple(reasons))


def _sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()

def inspect_supplied_gradle_zip(path: Path, requirement: BuildRequirement) -> dict:
    path=Path(path)
    digest=_sha256(path)
    version=None
    try:
        with zipfile.ZipFile(path) as z:
            roots={name.split('/',1)[0] for name in z.namelist() if '/' in name}
        versions=[]
        for root in roots:
            m=re.fullmatch(r'gradle-([0-9]+(?:\.[0-9]+)+)', root)
            if m:
                versions.append(m.group(1))
        if len(set(versions))==1:
            version=versions[0]
    except zipfile.BadZipFile:
        return {'status':'GRADLE_DISTRIBUTION_INVALID','version':None,'sha256':digest,'path':str(path)}
    status='GRADLE_LOCAL_DISTRIBUTION' if version==requirement.version else 'GRADLE_VERSION_MISMATCH'
    return {'status':status,'version':version,'sha256':digest,'path':str(path)}

def build_gradle_command(executable: str, *, offline: bool) -> list[str]:
    cmd=[executable]
    if offline:
        cmd.append('--offline')
    cmd.append(':app-dpc:assembleEnterpriseDebug')
    return cmd


def parse_catalog_requirements(path: Path) -> list[tuple[str,str,str]]:
    data=tomllib.loads(Path(path).read_text(encoding='utf-8'))
    versions=data.get('versions',{})
    reqs=[
        ('com.android.tools.build','gradle',str(versions.get('agp',''))),
        ('org.jetbrains.kotlin','kotlin-gradle-plugin',str(versions.get('kotlin',''))),
        ('androidx.core','core-ktx',str(versions.get('androidxCore',''))),
        ('dev.rikka.shizuku','api',str(versions.get('shizuku',''))),
        ('dev.rikka.shizuku','provider',str(versions.get('shizuku',''))),
    ]
    return [r for r in reqs if r[2]]

def required_compile_sdk(root: Path) -> int:
    data=tomllib.loads((Path(root)/'gradle/libs.versions.toml').read_text(encoding='utf-8'))
    return int(data['versions']['compileSdk'])

def probe_android_sdk(root: Path, env: dict | None = None) -> dict:
    env=env or os.environ
    raw=env.get('ANDROID_SDK_ROOT') or env.get('ANDROID_HOME')
    if not raw:
        return {'sdk':False,'platform':False,'build_tools':False,'path':None}
    sdk=Path(raw)
    compile_sdk=required_compile_sdk(Path(root))
    platform=(sdk/'platforms'/f'android-{compile_sdk}').is_dir()
    bt=sdk/'build-tools'
    build_tools=bt.is_dir() and any(p.is_dir() for p in bt.iterdir())
    return {'sdk':sdk.is_dir(),'platform':platform,'build_tools':build_tools,'path':str(sdk),'compileSdk':compile_sdk}

def dependencies_cached(requirements: list[tuple[str,str,str]], gradle_home: Path) -> bool:
    base=Path(gradle_home)/'caches/modules-2/files-2.1'
    for group,artifact,version in requirements:
        d=base/group/artifact/version
        if not d.is_dir() or not any(p.is_file() for p in d.rglob('*')):
            return False
    return True


def probe_java_major(runner=None) -> int | None:
    import subprocess
    runner = runner or (lambda args: subprocess.run(args, text=True, capture_output=True))
    try:
        proc=runner(['java','-version'])
        text=(getattr(proc,'stderr','') or '')+'\n'+(getattr(proc,'stdout','') or '')
        m=re.search(r'version "([0-9]+)(?:\.|")',text)
        return int(m.group(1)) if m else None
    except Exception:
        return None

def _gradle_version_from_executable(path: Path) -> str | None:
    import subprocess
    try:
        proc=subprocess.run([str(path),'--version'],text=True,capture_output=True,timeout=10)
        text=proc.stdout+'\n'+proc.stderr
        m=re.search(r'Gradle\s+([0-9]+(?:\.[0-9]+)+)',text)
        return m.group(1) if proc.returncode==0 and m else None
    except Exception:
        return None

def discover_gradle_candidates(requirement: BuildRequirement, gradle_home: Path, path_env: str | None = None) -> list[dict]:
    import shutil
    out=[]; gh=Path(gradle_home)
    dists=gh/'wrapper/dists'/requirement.distribution_name.removesuffix('.zip')
    if dists.is_dir():
        for exe in dists.glob(f'*/gradle-{requirement.version}/bin/gradle'):
            if exe.is_file(): out.append({'source':'CACHE','version':requirement.version,'path':str(exe)})
    found=shutil.which('gradle', path=path_env)
    if found:
        v=_gradle_version_from_executable(Path(found))
        if v: out.append({'source':'LOCAL','version':v,'path':found})
    return out

def materialize_supplied_gradle_zip(path: Path, requirement: BuildRequirement, cache_dir: Path) -> Path:
    path=Path(path); cache_dir=Path(cache_dir)
    info=inspect_supplied_gradle_zip(path,requirement)
    if info['status']!='GRADLE_LOCAL_DISTRIBUTION':
        raise ValueError(f"invalid Gradle distribution: {info['status']}")
    digest=info['sha256']; target=cache_dir/digest
    exe=target/f'gradle-{requirement.version}'/'bin'/'gradle'
    if exe.is_file():
        return exe
    target.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path) as z:
        for member in z.infolist():
            name=member.filename
            dest=(target/name).resolve()
            if target.resolve() not in dest.parents and dest!=target.resolve():
                raise ValueError(f'unsafe Gradle ZIP path: {name}')
        z.extractall(target)
    if not exe.is_file():
        raise ValueError('invalid Gradle distribution: executable missing')
    try: exe.chmod(exe.stat().st_mode | 0o111)
    except OSError: pass
    return exe

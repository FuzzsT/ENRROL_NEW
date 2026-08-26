from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
settings = (ROOT / 'settings.gradle.kts').read_text(encoding='utf-8')
manifest = (ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
dashboard = (ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt').read_text(encoding='utf-8')
registry_path = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt'
center_path = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ModuleCenterActivity.kt'

assert registry_path.is_file(), 'DpcModuleRegistry.kt missing'
assert center_path.is_file(), 'ModuleCenterActivity.kt missing'
registry = registry_path.read_text(encoding='utf-8')
center = center_path.read_text(encoding='utf-8')

# Every Gradle module that forms the DPC application must be represented in Module Center.
project_ids = set(re.findall(r'project\("(:[^"]+)"\)\.projectDir', settings))
verification_only = {':aio-test-target'}
module_ids = project_ids - {':app-dpc'} - verification_only
missing = sorted(m for m in module_ids if f'"{m}"' not in registry)
assert not missing, f'modules missing from registry: {missing}'

# DPC-AIO is one application: every application module is also a direct app dependency.
app_gradle = (ROOT / 'apps/dpc/app/build.gradle.kts').read_text(encoding='utf-8')
direct = set(re.findall(r'project\("(:[^"]+)"\)', app_gradle))
missing_direct = sorted(module_ids - direct)
assert not missing_direct, f'modules not directly owned by :app-dpc: {missing_direct}'

# The whole project graph must be reachable from :app-dpc, directly or transitively.
mapping = dict(re.findall(r'project\("(:[^"]+)"\)\.projectDir = file\("([^"]+)"\)', settings))
graph = {project: set() for project in mapping}
for project, rel in mapping.items():
    gradle = ROOT / rel / 'build.gradle.kts'
    if gradle.is_file():
        graph[project] = set(re.findall(r'project\("(:[^"]+)"\)', gradle.read_text(encoding='utf-8')))
seen = set()
stack = [':app-dpc']
while stack:
    project = stack.pop()
    if project in seen:
        continue
    seen.add(project)
    stack.extend(graph.get(project, ()))
unreachable = sorted((project_ids - verification_only) - seen)
assert not unreachable, f'Gradle modules unreachable from :app-dpc: {unreachable}'

assert 'ModuleCenterActivity' in dashboard, 'dashboard must expose Module Center'
assert '.ModuleCenterActivity' in manifest, 'Module Center must be declared in manifest'
for marker in ['Integrated', 'UI', 'Core', 'Integration', 'Lab']:
    assert marker in center or marker in registry, marker

print(f'MODULE_CENTER: PASS ({len(module_ids)} modules represented; {len(project_ids)} Gradle projects reachable)')

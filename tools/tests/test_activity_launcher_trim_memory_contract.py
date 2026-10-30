from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
hub_path = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt'
assert hub_path.is_file(), 'ActivityLauncherHubActivity.kt missing'
hub = hub_path.read_text(encoding='utf-8')

assert 'override fun onTrimMemory(level: Int)' in hub, 'onTrimMemory override missing'
assert 'TRIM_MEMORY_RUNNING_LOW' not in hub, 'deprecated TRIM_MEMORY_RUNNING_LOW must not be referenced'
assert 'private const val TRIM_MEMORY_RUNNING_LOW_COMPAT = 10' in hub, 'legacy running-low threshold must be preserved as compatibility constant'
assert 'if (level >= TRIM_MEMORY_RUNNING_LOW_COMPAT) iconCache.evictAll()' in hub, 'icon cache eviction must preserve the legacy threshold'

print('ACTIVITY_LAUNCHER_TRIM_MEMORY_CONTRACT: PASS')

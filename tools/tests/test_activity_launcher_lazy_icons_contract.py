from pathlib import Path

root = Path(__file__).resolve().parents[2]
launcher = (root / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt").read_text(encoding="utf-8")

assert "val icon: Drawable?" not in launcher, "AppEntry must not retain one Drawable per installed package"
assert "LruCache<String, Drawable>" in launcher, "launcher should use a bounded icon cache"
assert "private fun loadAppIcon(packageName: String): Drawable?" in launcher
assert "packageManager.getApplicationIcon(packageName)" in launcher
assert "iconCache.get(packageName)" in launcher
assert "iconCache.put(packageName, loaded)" in launcher
assert "Executors.newFixedThreadPool" in launcher, "cache misses must not load icons on the UI thread"
assert "iconExecutor.submit" in launcher
assert "private fun bindAppIcon" in launcher
assert "runOnUiThread" in launcher
assert "val icon = loadAppIcon(app.packageName)" not in launcher, "renderApp must not synchronously load a cache miss"
assert "renderGeneration" in launcher, "icon requests must be scoped to the current render generation"
assert "generation != renderGeneration" in launcher
assert "if (isFinishing || isDestroyed) return@runOnUiThread" in launcher, "inventory completion must be discarded after destruction"
assert "iconExecutor.isShutdown" in launcher, "icon submission must be guarded after executor shutdown"
assert "ICON_CACHE_SIZE" in launcher

# Favorites/Recent counters must reflect the current installed inventory, not stale persisted package names.
assert "private fun installedPackageNames(): Set<String>" in launcher, "launcher should centralize installed package membership"
assert "favoritePackages.count { it in installedPackages }" in launcher, "Favorites overview count must ignore uninstalled packages"
assert "recentPackages.count { it in installedPackages }" in launcher, "Recent overview count must ignore uninstalled packages"

print("ACTIVITY_LAUNCHER_LAZY_ICONS_CONTRACT: PASS")

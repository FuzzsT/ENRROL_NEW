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

# Large app inventories must not inflate every card in one UI-thread render pass.
assert "INITIAL_RENDER_LIMIT" in launcher, "launcher must cap the initial number of materialized app cards"
assert "RENDER_PAGE_SIZE" in launcher, "launcher must define an incremental render page size"
assert "visibleAppLimit" in launcher, "launcher must track the current materialized app limit"
assert "shown.take(visibleAppLimit)" in launcher, "launcher must render only the current page of matching apps"
assert '"Load more' in launcher, "launcher must expose an explicit way to materialize the next page"
assert "visibleAppLimit + RENDER_PAGE_SIZE" in launcher, "Load more must advance by a bounded page size"
assert "renderStats(shown)" in launcher, "overview stats must continue to reflect all matching apps, not only the materialized page"

print("ACTIVITY_LAUNCHER_LAZY_ICONS_CONTRACT: PASS")

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

# Loading another page must append only the new slice instead of rebuilding the full growing prefix.
assert "private fun appendNextPage" in launcher, "launcher must isolate append-only page growth"
append_start = launcher.index("private fun appendNextPage")
append_end = launcher.index("private fun renderStats", append_start)
append_body = launcher[append_start:append_end]
assert "shown.subList(start, nextLimit)" in append_body, "Load more must materialize only the newly requested slice"
assert "renderApps(resetLimit = false)" not in append_body, "Load more must not rebuild the whole visible prefix"

# Favorite changes do not change USER/SYSTEM membership, so preserve the amount already materialized.
toggle_start = launcher.index("private fun toggleFavorite")
toggle_end = launcher.index("private fun recordRecent", toggle_start)
toggle_body = launcher[toggle_start:toggle_end]
assert "renderApps(resetLimit = false)" in toggle_body, "favorite updates must preserve the current render limit"

# Returning from another Activity must refresh the package inventory without allowing an older scan to win.
assert "private var inventoryGeneration" in launcher, "launcher must track installed-inventory scan generations"
assert "override fun onRestart()" in launcher, "launcher must refresh installed packages when returning after stop"
restart_start = launcher.index("override fun onRestart()")
restart_end = launcher.index("override fun onResume()", restart_start)
restart_body = launcher[restart_start:restart_end]
assert "discoverInstalledApps" in restart_body, "onRestart must trigger a fresh installed-app inventory"
discover_start = launcher.index("private fun discoverInstalledApps")
discover_end = launcher.index("private fun toEntry", discover_start)
discover_body = launcher[discover_start:discover_end]
assert "++inventoryGeneration" in discover_body, "each inventory scan must get a monotonically increasing generation"
assert "isFinishing || isDestroyed" in discover_body, "inventory completion must be discarded after destruction"
assert "return@runOnUiThread" in discover_body, "destroyed/stale inventory completion must return before mutating UI state"
assert "generation != inventoryGeneration" in discover_body, "stale installed-app scans must not overwrite newer inventory"
assert "renderApps(resetLimit = resetLimit)" in discover_body, "restart refresh should be able to preserve already materialized depth"
assert "iconCache.evictAll()" in discover_body, "accepted inventory refresh must invalidate cached icons so package updates cannot show stale artwork"
assert discover_body.index("generation != inventoryGeneration") < discover_body.index("iconCache.evictAll()") < discover_body.index("renderApps(resetLimit = resetLimit)"), \
    "icon cache must be invalidated only after accepting the latest inventory and before rerendering cards"

print("ACTIVITY_LAUNCHER_LAZY_ICONS_CONTRACT: PASS")

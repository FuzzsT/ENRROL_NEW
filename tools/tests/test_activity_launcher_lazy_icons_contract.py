from pathlib import Path

root = Path(__file__).resolve().parents[2]
launcher = (root / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt").read_text(encoding="utf-8")

assert "val icon: Drawable?" not in launcher, "AppEntry must not retain one Drawable per installed package"
assert "LruCache<String, Drawable>" in launcher, "launcher should use a bounded icon cache"
assert "private fun loadAppIcon(packageName: String): Drawable?" in launcher
assert "packageManager.getApplicationIcon(packageName)" in launcher
assert "iconCache.get(packageName)" in launcher
assert "iconCache.put(packageName, loaded)" in launcher
assert "loadAppIcon(app.packageName)" in launcher
assert "ICON_CACHE_SIZE" in launcher
print("ACTIVITY_LAUNCHER_LAZY_ICONS_CONTRACT: PASS")

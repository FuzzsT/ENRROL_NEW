from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")

assert '":self-update-core"' in settings, "settings must include :self-update-core"
assert 'apps/dpc/modules/self-update/core' in settings, "settings must map self-update core module"

models_path = ROOT / "apps/dpc/modules/self-update/core/src/main/kotlin/io/dpcaio/selfupdate/SelfUpdateModels.kt"
planner_path = ROOT / "apps/dpc/modules/self-update/core/src/main/kotlin/io/dpcaio/selfupdate/SelfUpdatePlanner.kt"
assert models_path.is_file(), "SelfUpdateModels.kt missing"
assert planner_path.is_file(), "SelfUpdatePlanner.kt missing"

models = models_path.read_text(encoding="utf-8")
planner = planner_path.read_text(encoding="utf-8")

for token in [
    "enum class UpdateChannel",
    "data class SelfUpdateManifest",
    "enum class SelfUpdateState",
    "enum class SelfUpdateInstallPolicy",
    "ASK_ALWAYS",
    "SILENT_IF_ALLOWED",
    "AUTO_SILENT",
    "WAITING_USER",
    "VERIFIED",
    "schemaVersion",
    "packageName",
    "versionCode",
    "apkUrl",
    "apkSha256",
    "apkSize",
    "releaseNotesUrl",
]:
    assert token in models, f"Self Update model contract missing: {token}"

for token in [
    "class SelfUpdatePlanner",
    "fun evaluate(",
    "schemaVersion != 1",
    "manifest.packageName != installedPackage",
    "manifest.versionCode <= installedVersionCode",
    "https://",
    "apkSha256",
]:
    assert token in planner, f"Self Update planner contract missing: {token}"

print("Self Update core contract: PASS")

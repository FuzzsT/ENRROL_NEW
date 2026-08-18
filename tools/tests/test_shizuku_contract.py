#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def must(path: Path, token: str):
    if not path.exists():
        raise AssertionError(f"missing {path.relative_to(ROOT)}")
    text = path.read_text(encoding="utf-8", errors="ignore")
    if token not in text:
        raise AssertionError(f"{path.relative_to(ROOT)} missing {token}")
    return text


def main():
    runtime = ROOT / "shizuku-adapter/src/main/kotlin/io/dpcaio/shizuku/AndroidShizukuRuntime.kt"
    service = ROOT / "shizuku-adapter/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt"
    client = ROOT / "shizuku-adapter/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt"
    activity_executor = ROOT / "shizuku-adapter/src/main/kotlin/io/dpcaio/shizuku/ShizukuActivityRouteExecutor.kt"
    manifest = ROOT / "shizuku-adapter/src/main/AndroidManifest.xml"
    catalog = ROOT / "gradle/libs.versions.toml"

    runtime_text = must(runtime, "Shizuku.checkSelfPermission()")
    for token in ["Shizuku.getUid()", "Shizuku.getVersion()", "Shizuku.requestPermission("]:
        if token not in runtime_text:
            raise AssertionError(f"runtime missing {token}")

    service_text = must(service, 'ProcessBuilder(args)')
    for token in ['"/system/bin/am", "start"', '"/system/bin/am", "broadcast"']:
        if token not in service_text:
            raise AssertionError(f"typed am route missing {token}")
    for forbidden in ["Runtime.getRuntime().exec", "newProcess(", "HiddenApiBypass", "setHiddenApiExemptions"]:
        if forbidden in service_text:
            raise AssertionError(f"unsafe Shizuku service token: {forbidden}")

    client_text = must(client, "Shizuku.UserServiceArgs")
    for token in ["Shizuku.bindUserService", ".processNameSuffix(", ".version("]:
        if token not in client_text:
            raise AssertionError(f"client missing {token}")

    activity_text = must(activity_executor, "ActivityRoute.SHIZUKU")
    if "client.startActivity(" not in activity_text:
        raise AssertionError("Shizuku activity executor must invoke typed user-service startActivity")

    must(manifest, "rikka.shizuku.ShizukuProvider")
    must(catalog, 'shizuku = "13.1.5"')
    print("test_shizuku_contract: PASS")


if __name__ == "__main__":
    main()

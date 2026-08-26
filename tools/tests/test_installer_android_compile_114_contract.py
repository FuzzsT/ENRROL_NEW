from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidApkPlusStager.kt"


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")
    assert "val baseApk = plan.baseApk" in text
    assert "listOf(plan.baseApk)" not in text
    assert "listOf(baseApk)" in text
    assert "path == plan.baseApk" not in text
    assert text.count("path == baseApk") >= 2
    print("installer android compile contract: PASS")


if __name__ == "__main__":
    main()

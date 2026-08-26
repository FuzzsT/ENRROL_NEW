from __future__ import annotations

import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD_SCRIPT = ROOT / "plugins/chatgpt-companion/skills/dpc-aio-build/scripts/build_variant.sh"
VERIFY_SCRIPT = ROOT / "plugins/chatgpt-companion/skills/dpc-aio-verify/scripts/verify_repo.sh"

errors: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def run_build_tests() -> None:
    if not BUILD_SCRIPT.is_file():
        errors.append(f"missing build script: {BUILD_SCRIPT.relative_to(ROOT)}")
        return

    missing = subprocess.run(
        [str(BUILD_SCRIPT), "/definitely/missing/dpc-aio", "EnterpriseDebug"],
        text=True,
        capture_output=True,
    )
    check(missing.returncode != 0, "missing repo root must fail")
    check("missing Gradle wrapper" in missing.stderr or "No such file" in missing.stderr,
          "missing repo root must report a clear wrapper/path error")

    with tempfile.TemporaryDirectory() as temp:
        repo = Path(temp)
        no_wrapper = subprocess.run(
            [str(BUILD_SCRIPT), str(repo), "EnterpriseDebug"],
            text=True,
            capture_output=True,
        )
        check(no_wrapper.returncode != 0, "repo without gradlew must fail")
        check("missing Gradle wrapper" in no_wrapper.stderr,
              "repo without gradlew must report missing Gradle wrapper")

    with tempfile.TemporaryDirectory() as temp:
        repo = Path(temp)
        log = repo / "gradle-args.txt"
        gradlew = repo / "gradlew"
        gradlew.write_text(
            "#!/usr/bin/env bash\n"
            "printf '%s\\n' \"$@\" > \"$FAKE_GRADLE_LOG\"\n"
            "exit \"${FAKE_GRADLE_EXIT:-0}\"\n",
            encoding="utf-8",
        )
        gradlew.chmod(0o755)
        env = os.environ.copy()
        env["FAKE_GRADLE_LOG"] = str(log)
        env["FAKE_GRADLE_EXIT"] = "0"
        ok = subprocess.run(
            [str(BUILD_SCRIPT), str(repo), "EnterpriseDebug"],
            text=True,
            capture_output=True,
            env=env,
        )
        check(ok.returncode == 0, f"fake Gradle success must return 0, got {ok.returncode}: {ok.stderr}")
        recorded = log.read_text(encoding="utf-8").splitlines() if log.exists() else []
        check(recorded == [":app-dpc:assembleEnterpriseDebug"],
              f"expected exact Gradle task, got {recorded!r}")

        env["FAKE_GRADLE_EXIT"] = "23"
        failed = subprocess.run(
            [str(BUILD_SCRIPT), str(repo), "EnterpriseDebug"],
            text=True,
            capture_output=True,
            env=env,
        )
        check(failed.returncode == 23,
              f"Gradle exit code must propagate unchanged; expected 23 got {failed.returncode}")

        unsupported = subprocess.run(
            [str(BUILD_SCRIPT), str(repo), "NotARealVariant"],
            text=True,
            capture_output=True,
            env=env,
        )
        check(unsupported.returncode == 64, "unsupported variant must return EX_USAGE 64")
        check("unsupported DPC-AIO variant" in unsupported.stderr,
              "unsupported variant must emit a clear error")


run_build_tests()


def _write_verifier(path: Path, name: str, exit_code: int = 0) -> None:
    path.write_text(
        "#!/usr/bin/env python3\n"
        "import os, sys\n"
        f"name={name!r}\n"
        "log=os.environ.get('FAKE_VERIFY_LOG')\n"
        "if log:\n"
        "    with open(log, 'a', encoding='utf-8') as handle:\n"
        "        handle.write(name + '\\n')\n"
        f"sys.exit({exit_code})\n",
        encoding="utf-8",
    )


def _make_verify_repo(root: Path) -> Path:
    (root / "gradle/wrapper").mkdir(parents=True)
    (root / "tools").mkdir(parents=True)
    (root / "src").mkdir(parents=True)
    (root / "gradlew").write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
    (root / "gradle/wrapper/gradle-wrapper.jar").write_bytes(b"jar")
    (root / "gradle/wrapper/gradle-wrapper.properties").write_text("distributionUrl=fake\n", encoding="utf-8")
    (root / "src/Clean.kt").write_text("class Clean\n", encoding="utf-8")
    _write_verifier(root / "tools/verify_project.py", "verify_project.py")
    _write_verifier(root / "tools/verify_android_contracts.py", "verify_android_contracts.py")
    _write_verifier(root / "tools/release_gate.py", "release_gate.py")
    return root


def run_verify_tests() -> None:
    if not VERIFY_SCRIPT.is_file():
        errors.append(f"missing verify script: {VERIFY_SCRIPT.relative_to(ROOT)}")
        return

    with tempfile.TemporaryDirectory() as temp:
        repo = _make_verify_repo(Path(temp))
        log = repo / "verify.log"
        env = os.environ.copy()
        env["FAKE_VERIFY_LOG"] = str(log)
        ok = subprocess.run([str(VERIFY_SCRIPT), str(repo)], text=True, capture_output=True, env=env)
        check(ok.returncode == 0, f"clean fake repo must verify, got {ok.returncode}: {ok.stderr}")
        ran = log.read_text(encoding="utf-8").splitlines() if log.exists() else []
        check(ran == ["verify_project.py", "verify_android_contracts.py", "release_gate.py"],
              f"expected all verifier scripts in order, got {ran!r}")

        hidden = repo / "src/Hidden.kt"
        hidden.write_text("val bad = android.os.UserHandle.myUserId()\n", encoding="utf-8")
        hidden_run = subprocess.run([str(VERIFY_SCRIPT), str(repo)], text=True, capture_output=True, env=env)
        check(hidden_run.returncode != 0, "hidden UserHandle helper must fail verification")
        check("hidden UserHandle user-id API reference found" in hidden_run.stderr,
              "hidden UserHandle failure must be explicit")
        hidden.unlink()

        (repo / "gradle/wrapper/gradle-wrapper.jar").unlink()
        missing_wrapper = subprocess.run([str(VERIFY_SCRIPT), str(repo)], text=True, capture_output=True, env=env)
        check(missing_wrapper.returncode != 0, "missing gradle-wrapper.jar must fail")
        (repo / "gradle/wrapper/gradle-wrapper.jar").write_bytes(b"jar")
        (repo / "gradle/wrapper/gradle-wrapper.properties").unlink()
        missing_props = subprocess.run([str(VERIFY_SCRIPT), str(repo)], text=True, capture_output=True, env=env)
        check(missing_props.returncode != 0, "missing gradle-wrapper.properties must fail")

    with tempfile.TemporaryDirectory() as temp:
        repo = _make_verify_repo(Path(temp))
        _write_verifier(repo / "tools/verify_android_contracts.py", "verify_android_contracts.py", exit_code=17)
        failed = subprocess.run([str(VERIFY_SCRIPT), str(repo)], text=True, capture_output=True)
        check(failed.returncode == 17,
              f"verifier failure must propagate unchanged; expected 17 got {failed.returncode}")

run_verify_tests()

if errors:
    print("PLUGIN_SCRIPT_TESTS: FAIL")
    for error in errors:
        print(f" - {error}")
    raise SystemExit(1)

print("PLUGIN_SCRIPT_TESTS: PASS")

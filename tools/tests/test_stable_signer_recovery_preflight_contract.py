from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS1 = ROOT / "RECOVER-STABLE-SIGNER.ps1"
CI = ROOT / ".github" / "workflows" / "pr-android-ci.yml"


def main() -> None:
    ps1 = PS1.read_text(encoding="utf-8")
    ci = CI.read_text(encoding="utf-8")

    required_ps1 = [
        "function Resolve-RecoveryTool",
        "Resolve-RecoveryTool -Name 'openssl'",
        "Resolve-RecoveryTool -Name 'keytool'",
        "Get-Command apksigner",
        "$env:JAVA_HOME",
        "$env:ANDROID_SDK_ROOT",
        "$env:ANDROID_HOME",
        "RECOVERY_PREFLIGHT_MISSING",
        "RECOVERY_PREFLIGHT: PASS",
        "if ($Apply)",
        "Get-Command gh",
    ]
    missing = [item for item in required_ps1 if item not in ps1]
    assert not missing, f"missing recovery preflight markers: {missing}"

    assert "Git\\usr\\bin\\openssl.exe" in ps1, "openssl must have Git for Windows fallback"
    assert "bin\\keytool.exe" in ps1, "keytool must have JAVA_HOME fallback"
    assert "build-tools" in ps1 and "apksigner.bat" in ps1, "apksigner must resolve from Android SDK build-tools"

    gh_check = ps1.find("Get-Command gh")
    apply_gate = ps1.find("if ($Apply)")
    assert apply_gate >= 0 and gh_check > apply_gate, "gh must be required only for Apply/Dispatch path"

    assert "test_stable_signer_recovery_preflight_contract.py" in ci, (
        "stable signer recovery preflight contract must be executed by Android CI"
    )

    print("STABLE_SIGNER_RECOVERY_PREFLIGHT_CONTRACT: PASS")


if __name__ == "__main__":
    main()

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

gradle = (ROOT / "apps/dpc/app/build.gradle.kts").read_text(encoding="utf-8")
generator = (ROOT / "tools/provisioning/generate_provisioning.py").read_text(encoding="utf-8")

assert "fully-managed-qr.png" in generator, (
    "generator must emit the fully-managed compatibility alias"
)

marker = 'if ("fully-managed" !in selectedQrModes) {'
start = gradle.index(marker)
end = gradle.index("val compatibilityMode", start)
cleanup = gradle[start:end]

assert '"fully-managed-qr.png"' in cleanup, (
    "fully-managed cleanup must remove compatibility alias"
)

print("QR_STALE_ALIAS_CLEANUP_129: PASS")

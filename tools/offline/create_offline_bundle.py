#!/usr/bin/env python3
"""Build a deterministic signed DPC-AIO offline deployment bundle.

The private Ed25519 key is read only for signing manifest.json and is never
copied into the output archive. Package identity metadata is declared by the
operator and is re-verified on-device before installation.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import zipfile
from pathlib import Path, PurePosixPath

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

ZIP_DATE = (1980, 1, 1, 0, 0, 0)
ALLOWED_CAPABILITIES = {"PACKAGE_INSTALL", "PERMISSION_CONTROL", "COMPONENT_CONTROL", "DEVICE_POLICY"}


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def safe_archive_path(value: str) -> str:
    p = PurePosixPath(value)
    if not value or p.is_absolute() or ".." in p.parts or "\\" in value:
        raise ValueError(f"unsafe archive path: {value!r}")
    return str(p)


def require_file(value: str, label: str) -> Path:
    p = Path(value).expanduser().resolve()
    if not p.is_file():
        raise ValueError(f"{label} not found: {p}")
    return p


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, ZIP_DATE)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    info.create_system = 3
    return info


def write_bytes(z: zipfile.ZipFile, name: str, data: bytes) -> None:
    z.writestr(zip_info(name), data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build_manifest(spec: dict) -> tuple[dict, dict[str, Path]]:
    schema = int(spec.get("schemaVersion", 1))
    if schema != 1:
        raise ValueError(f"unsupported schemaVersion: {schema}")
    bundle_id = str(spec.get("bundleId", "")).strip()
    if not bundle_id:
        raise ValueError("bundleId is required")
    caps = sorted({str(v) for v in spec.get("requiredCapabilities", [])})
    unknown = set(caps) - ALLOWED_CAPABILITIES
    if unknown:
        raise ValueError(f"unsupported requiredCapabilities: {sorted(unknown)}")
    allowed_modes = sorted({str(v) for v in spec.get("allowedModes", [])})
    if not allowed_modes:
        raise ValueError("allowedModes must not be empty")

    archive_sources: dict[str, Path] = {}
    manifest_packages: list[dict] = []
    for pkg in sorted(spec.get("packages", []), key=lambda p: str(p.get("packageName", ""))):
        package_name = str(pkg.get("packageName", "")).strip()
        if not package_name:
            raise ValueError("packageName is required")
        version_code = int(pkg.get("versionCode", 0))
        if version_code < 1:
            raise ValueError(f"versionCode must be positive for {package_name}")
        signer = str(pkg.get("signingCertificateSha256", "")).lower().strip()
        if len(signer) != 64 or any(c not in "0123456789abcdef" for c in signer):
            raise ValueError(f"signingCertificateSha256 must be 64 hex chars for {package_name}")
        manifest_files: list[dict] = []
        for item in sorted(pkg.get("files", []), key=lambda f: str(f.get("path", ""))):
            source = require_file(str(item.get("source", "")), f"package source for {package_name}")
            archive_path = safe_archive_path(str(item.get("path", "")))
            if archive_path in archive_sources:
                raise ValueError(f"duplicate archive path: {archive_path}")
            archive_sources[archive_path] = source
            manifest_files.append({
                "path": archive_path,
                "required": bool(item.get("required", True)),
                "sha256": sha256_file(source),
            })
        if not manifest_files:
            raise ValueError(f"at least one APK file is required for {package_name}")
        if not any(f["path"].endswith("/base.apk") or f["path"] == "base.apk" for f in manifest_files):
            raise ValueError(f"base.apk is required for {package_name}")
        manifest_packages.append({
            "files": manifest_files,
            "packageName": package_name,
            "signingCertificateSha256": signer,
            "versionCode": version_code,
        })

    policy_value = spec.get("policy")
    policy_path: str | None = None
    if policy_value:
        if not isinstance(policy_value, dict):
            raise ValueError("policy must be an object with source/path")
        policy_source = require_file(str(policy_value.get("source", "")), "policy source")
        policy_path = safe_archive_path(str(policy_value.get("path", "")))
        if policy_path in archive_sources:
            raise ValueError(f"duplicate archive path: {policy_path}")
        archive_sources[policy_path] = policy_source

    manifest: dict[str, object] = {
        "allowedModes": allowed_modes,
        "bundleId": bundle_id,
        "minimumAndroidApi": int(spec.get("minimumAndroidApi", 33)),
        "minimumDpcVersion": str(spec.get("minimumDpcVersion", "1.0.0")),
        "organizationId": str(spec.get("organizationId", "")),
        "packages": manifest_packages,
        "requiredCapabilities": caps,
        "schemaVersion": schema,
    }
    if policy_path:
        manifest["policy"] = policy_path
    return manifest, archive_sources


def load_private_key(path: Path) -> Ed25519PrivateKey:
    key = serialization.load_pem_private_key(path.read_bytes(), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        raise ValueError("private key must be Ed25519 PKCS#8 PEM")
    return key


def create_bundle(spec_path: Path, private_key_path: Path, out_path: Path) -> None:
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    manifest, archive_sources = build_manifest(spec)
    manifest_bytes = canonical_json_bytes(manifest)
    signature = load_private_key(private_key_path).sign(manifest_bytes)
    entries: dict[str, bytes | Path] = {
        "manifest.json": manifest_bytes,
        "manifest.sig": base64.b64encode(signature) + b"\n",
        **archive_sources,
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    temp = out_path.with_suffix(out_path.suffix + ".tmp")
    try:
        with zipfile.ZipFile(temp, "w") as z:
            for name in sorted(entries):
                value = entries[name]
                data = value.read_bytes() if isinstance(value, Path) else value
                write_bytes(z, name, data)
        temp.replace(out_path)
    finally:
        temp.unlink(missing_ok=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", required=True, type=Path)
    ap.add_argument("--private-key", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()
    create_bundle(args.spec.resolve(), args.private_key.resolve(), args.out.resolve())
    print(args.out.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

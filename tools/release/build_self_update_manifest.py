#!/usr/bin/env python3
"""Build the deterministic schema-v1 DPC-AIO self-update manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse


def https_url(value: str, field: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme.lower() != "https" or not parsed.netloc:
        raise ValueError(f"{field} must be an absolute HTTPS URL")
    return value


def normalize_channel(value: str) -> str:
    channel = value.strip().lower()
    if channel not in {"stable", "continuous"}:
        raise ValueError("channel must be stable or continuous")
    return channel


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_manifest(args: argparse.Namespace) -> dict[str, object]:
    apk = Path(args.apk)
    if not apk.is_file():
        raise ValueError(f"APK does not exist: {apk}")
    if not args.package.strip():
        raise ValueError("package must not be empty")
    if not args.version_name.strip():
        raise ValueError("version-name must not be empty")
    if args.version_code <= 0:
        raise ValueError("version-code must be positive")

    return {
        "schemaVersion": 1,
        "channel": normalize_channel(args.channel),
        "packageName": args.package.strip(),
        "versionName": args.version_name.strip(),
        "versionCode": args.version_code,
        "apkUrl": https_url(args.apk_url, "apk-url"),
        "apkSha256": file_sha256(apk),
        "apkSize": apk.stat().st_size,
        "publishedAt": args.published_at.strip(),
        "releaseNotesUrl": https_url(args.release_notes_url, "release-notes-url"),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--channel", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--published-at", required=True)
    parser.add_argument("--release-notes-url", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        payload = build_manifest(args)
    except ValueError as exc:
        print(f"SELF_UPDATE_MANIFEST_ERROR: {exc}")
        return 2

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(payload, ensure_ascii=False, indent=2, separators=(",", ": ")) + "\n"
    out.write_text(encoded, encoding="utf-8", newline="\n")
    print(f"SELF_UPDATE_MANIFEST: {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

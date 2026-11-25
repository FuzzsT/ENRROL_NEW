#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Verify a published DPC-AIO Self Update manifest against the release APK.")
    p.add_argument("--manifest", type=Path, required=True)
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--tag", required=True)
    p.add_argument("--channel", required=True)
    p.add_argument("--package", required=True)
    p.add_argument("--version-code", required=True, type=int)
    p.add_argument("--version-name", required=True)
    p.add_argument("--apk-url", required=True)
    p.add_argument("--apk-sha256", required=True)
    return p.parse_args()


def main() -> int:
    args = parse_args()
    payload = json.loads(args.manifest.read_text(encoding="utf-8"))
    expected = {
        "channel": args.channel,
        "packageName": args.package,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "apkUrl": args.apk_url,
        "apkSha256": args.apk_sha256.lower(),
    }
    for key, value in expected.items():
        observed = payload.get(key)
        if observed != value:
            raise SystemExit(
                f"REPAIR_SELF_UPDATE_MANIFEST_VERIFY_FAILED tag={args.tag} "
                f"field={key} expected={value!r} observed={observed!r}"
            )

    observed_size = int(payload.get("apkSize", 0))
    actual_size = args.apk.stat().st_size
    if observed_size != actual_size:
        raise SystemExit(
            f"REPAIR_SELF_UPDATE_MANIFEST_VERIFY_FAILED tag={args.tag} "
            f"field=apkSize expected={actual_size} observed={observed_size}"
        )

    print(
        f"REPAIR_SELF_UPDATE_MANIFEST_VERIFIED tag={args.tag} "
        f"channel={args.channel} version={args.version_name} "
        f"code={args.version_code} sha256={args.apk_sha256.lower()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

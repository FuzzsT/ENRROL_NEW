#!/usr/bin/env python3
"""Generate DPC-AIO LAB test tokens. Not compatible with Samsung KLM/KPE."""
from __future__ import annotations
import argparse, base64, secrets, time
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

PREFIX = "DPC-AIO-LAB1"

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--private-key", default=str(Path(__file__).with_name("dpc-aio-lab-private.pem")))
    ap.add_argument("--audience", default="io.dpcaio.app")
    ap.add_argument("--track", choices=["lab", "tst", "eng"], default="lab")
    ap.add_argument("--days", type=int, default=365)
    ap.add_argument("--scope", action="append", dest="scopes")
    args = ap.parse_args()
    key = serialization.load_pem_private_key(Path(args.private_key).read_bytes(), password=None)
    now = int(time.time())
    scopes = args.scopes or [
        "knox.mock.active",
        "app.manage",
        "policy.test",
        "license.callback.test",
        "offline.startup",
    ]
    payload = "\n".join([
        "iss=DPC-AIO-LAB",
        f"aud={args.audience}",
        f"track={args.track}",
        "licenseType=KLM_TEST_ONLY",
        f"iat={now}",
        f"exp={now + args.days * 86400}",
        f"nonce={secrets.token_urlsafe(16)}",
        f"scopes={','.join(scopes)}",
    ])
    sig = key.sign(payload.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
    print(f"{PREFIX}.{b64url(payload.encode('utf-8'))}.{b64url(sig)}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

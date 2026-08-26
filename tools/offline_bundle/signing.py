from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import json
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.exceptions import InvalidSignature

@dataclass(frozen=True)
class SignatureResult:
    status: str
    key_id: str | None = None

def sign_manifest(private_key_path: Path, manifest_path: Path, signature_path: Path) -> Path:
    private_key_path=Path(private_key_path); manifest_path=Path(manifest_path); signature_path=Path(signature_path)
    key=serialization.load_pem_private_key(private_key_path.read_bytes(),password=None)
    if not isinstance(key,Ed25519PrivateKey):
        raise ValueError('private key must be Ed25519')
    sig=key.sign(manifest_path.read_bytes())
    signature_path.write_bytes(sig)
    return signature_path

def verify_manifest(trust_store_path: Path, manifest_path: Path, signature_path: Path) -> SignatureResult:
    manifest_path=Path(manifest_path); signature_path=Path(signature_path); trust_store_path=Path(trust_store_path)
    try:
        manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
    except Exception:
        return SignatureResult('MANIFEST_INVALID')
    key_id=manifest.get('keyId')
    store=json.loads(trust_store_path.read_text(encoding='utf-8'))
    if key_id not in store:
        return SignatureResult('UNKNOWN_SIGNING_KEY',key_id)
    pub=serialization.load_pem_public_key(Path(store[key_id]).read_bytes())
    if not isinstance(pub,Ed25519PublicKey):
        return SignatureResult('PUBLIC_KEY_INVALID',key_id)
    try:
        pub.verify(signature_path.read_bytes(),manifest_path.read_bytes())
    except (InvalidSignature,FileNotFoundError):
        return SignatureResult('SIGNATURE_INVALID',key_id)
    return SignatureResult('VERIFIED',key_id)

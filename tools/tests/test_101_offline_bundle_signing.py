#!/usr/bin/env python3
from pathlib import Path
import sys,tempfile,json
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT))
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from tools.offline_bundle.builder import create_bundle_dir
from tools.offline_bundle.signing import sign_manifest, verify_manifest

with tempfile.TemporaryDirectory() as td:
    td=Path(td); bundle=td/'bundle'; create_bundle_dir(bundle,bundle_id='signed',key_id='prod-01')
    priv=Ed25519PrivateKey.generate(); pub=priv.public_key()
    priv_path=td/'company-ed25519.key'
    priv_path.write_bytes(priv.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    pub_path=td/'prod-01.pub'
    pub_path.write_bytes(pub.public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
    trust=td/'trust.json'; trust.write_text(json.dumps({'prod-01':str(pub_path)}))
    sig=sign_manifest(priv_path,bundle/'manifest.json',bundle/'manifest.sig')
    assert sig.is_file()
    ok=verify_manifest(trust,bundle/'manifest.json',bundle/'manifest.sig')
    assert ok.status=='VERIFIED', ok
    assert not any('company-ed25519.key' in str(p) for p in bundle.rglob('*'))

    data=json.loads((bundle/'manifest.json').read_text()); data['bundleId']='tampered'; (bundle/'manifest.json').write_text(json.dumps(data))
    bad=verify_manifest(trust,bundle/'manifest.json',bundle/'manifest.sig')
    assert bad.status=='SIGNATURE_INVALID', bad

    data['keyId']='prod-02'; (bundle/'manifest.json').write_text(json.dumps(data))
    unknown=verify_manifest(trust,bundle/'manifest.json',bundle/'manifest.sig')
    assert unknown.status=='UNKNOWN_SIGNING_KEY', unknown
print('test_101_offline_bundle_signing: PASS')

import base64, hashlib, json, subprocess, sys, tempfile, zipfile
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / 'tools/offline/create_offline_bundle.py'


def run():
    with tempfile.TemporaryDirectory() as td:
        d = Path(td)
        key = Ed25519PrivateKey.generate()
        key_path = d / 'offline-private.pem'
        key_path.write_bytes(key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ))
        base = d / 'base.apk'; base.write_bytes(b'base-apk-test')
        split = d / 'split.apk'; split.write_bytes(b'split-apk-test')
        policy = d / 'policy.json'; policy.write_text('{"permissions":{"defaultPolicy":"PROMPT"}}')
        spec = {
            'schemaVersion': 1,
            'bundleId': 'enterprise-offline-test',
            'organizationId': 'example-pl-001',
            'minimumDpcVersion': '1.0.0',
            'minimumAndroidApi': 33,
            'allowedModes': ['FULLY_MANAGED'],
            'requiredCapabilities': ['PACKAGE_INSTALL', 'PERMISSION_CONTROL'],
            'policy': {'source': str(policy), 'path': 'policies/enterprise.json'},
            'packages': [{
                'packageName': 'com.example.agent',
                'versionCode': 42,
                'signingCertificateSha256': 'ab' * 32,
                'files': [
                    {'source': str(base), 'path': 'packages/agent/base.apk', 'required': True},
                    {'source': str(split), 'path': 'packages/agent/split_config.arm64_v8a.apk', 'required': True},
                ],
            }],
        }
        spec_path = d / 'spec.json'; spec_path.write_text(json.dumps(spec))
        out1 = d / 'one.zip'; out2 = d / 'two.zip'
        for out in (out1, out2):
            subprocess.run([sys.executable, str(TOOL), '--spec', str(spec_path), '--private-key', str(key_path), '--out', str(out)], check=True)
        assert hashlib.sha256(out1.read_bytes()).digest() == hashlib.sha256(out2.read_bytes()).digest(), 'bundle must be deterministic'
        with zipfile.ZipFile(out1) as z:
            names = z.namelist()
            assert names == sorted(names), names
            assert 'manifest.json' in names and 'manifest.sig' in names
            assert 'offline-private.pem' not in names and all('private' not in n.lower() for n in names)
            manifest_bytes = z.read('manifest.json')
            manifest = json.loads(manifest_bytes)
            assert manifest['policy'] == 'policies/enterprise.json'
            files = manifest['packages'][0]['files']
            assert files[0]['sha256'] == hashlib.sha256(base.read_bytes()).hexdigest()
            assert files[1]['sha256'] == hashlib.sha256(split.read_bytes()).hexdigest()
            signature = base64.b64decode(z.read('manifest.sig'))
            key.public_key().verify(signature, manifest_bytes)
            assert z.read('policies/enterprise.json') == policy.read_bytes()
            assert z.read('packages/agent/base.apk') == base.read_bytes()
        print('OFFLINE_BUNDLE_TOOL: PASS')

if __name__ == '__main__':
    run()

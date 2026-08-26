#!/usr/bin/env python3
from pathlib import Path
import base64, time
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ROOT=Path(__file__).resolve().parents[2]

def unb64(s:str)->bytes:
    return base64.urlsafe_b64decode(s + '=' * ((4-len(s)%4)%4))

def main():
    token=(ROOT/'lab/license/dpc-aio-lab-klm.token').read_text().strip()
    public=serialization.load_pem_public_key((ROOT/'lab/license/dpc-aio-lab-public.pem').read_bytes())
    parts=token.split('.')
    assert len(parts)==3 and parts[0]=='DPC-AIO-LAB1'
    payload=unb64(parts[1])
    public.verify(unb64(parts[2]), payload, ec.ECDSA(hashes.SHA256()))
    claims=dict(line.split('=',1) for line in payload.decode().splitlines())
    assert claims['iss']=='DPC-AIO-LAB'
    assert claims['aud']=='io.dpcaio.app'
    assert claims['track']=='lab'
    assert claims['licenseType']=='KLM_TEST_ONLY'
    assert int(claims['exp'])>int(claims['iat'])
    assert 'knox.mock.active' in claims['scopes'].split(',')
    app_asset=ROOT/'apps/dpc/app/src/lab/assets/knox_lab/dpc-aio-lab-klm.token'
    assert app_asset.read_text().strip()==token
    assert not (ROOT/'apps/dpc/app/src/lab/assets/knox_lab/dpc-aio-lab-private.pem').exists()
    print('test_knox_lab_token_bundle: PASS')

if __name__=='__main__': main()

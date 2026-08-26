#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def main():
    gradle=(ROOT/'apps/dpc/app/build.gradle.kts').read_text()
    provider=ROOT/'apps/dpc/app/src/lab/java/io/dpcaio/app/KnoxLabLicenseProvider.kt'
    flavor_provider=ROOT/'apps/dpc/app/src/lab/java/io/dpcaio/app/KnoxFlavorLicenseProvider.kt'
    token=ROOT/'apps/dpc/app/src/lab/assets/knox_lab/dpc-aio-lab-klm.token'
    lab_module=ROOT/'apps/dpc/lab/knox-license/src/main/kotlin/io/dpcaio/knox/license/lab/KnoxLabLicense.kt'
    main_tree=ROOT/'apps/dpc/app/src/main'
    assert provider.exists() and flavor_provider.exists(), 'lab providers missing'
    assert token.exists(), 'lab token asset missing'
    assert lab_module.exists(), 'lab verifier module missing'
    assert 'implementation(project(":knox-license-core"))' in gradle
    assert 'implementation(project(":knox-license-lab"))' in gradle
    txt=provider.read_text()
    for required in ['buildTrack = "lab"', 'SIMULATED_ACTIVE', 'KnoxLabLicenseVerifier']:
        assert required in txt, f'lab provider missing {required}'
    for p in main_tree.rglob('*'):
        if p.is_file() and p.suffix in {'.kt','.java','.xml','.kts'}:
            t=p.read_text(errors='ignore')
            assert 'DPC-AIO-LAB1' not in t
            assert 'KnoxLabLicenseVerifier' not in t
            assert 'dpc-aio-lab-klm.token' not in t
    print('test_knox_lab_license_contract: PASS')

if __name__ == '__main__': main()

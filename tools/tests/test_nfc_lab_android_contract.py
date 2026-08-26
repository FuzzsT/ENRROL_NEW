from pathlib import Path
root=Path(__file__).resolve().parents[2]
module=root/'apps/dpc/modules/nfc-lab/android'
assert module.exists(), 'missing nfc-lab-android'
texts='\n'.join(p.read_text(errors='ignore') for p in module.rglob('*') if p.is_file())
for required in [
    'HostApduService','processCommandApdu','NfcA','NfcB','NfcF','NfcV','IsoDep','Ndef','NdefFormatable','NfcBarcode','MifareClassic','MifareUltralight',
    'authenticateSectorWithKeyA','authenticateSectorWithKeyB','readPages','writePage','transceive','NfcReplayValidator'
]:
    assert required in texts, required
print('test_nfc_lab_android_contract: PASS')

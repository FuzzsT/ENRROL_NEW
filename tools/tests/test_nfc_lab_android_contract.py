from pathlib import Path
root=Path(__file__).resolve().parents[2]
module=root/'apps/dpc/modules/nfc-lab/android'
assert module.exists(), 'missing nfc-lab-android'
texts='\n'.join(p.read_text(errors='ignore') for p in module.rglob('*') if p.is_file())
xml=(module/'src/main/res/xml/dpcaio_host_apdu_service.xml').read_text()
strings=(module/'src/main/res/values/strings.xml').read_text()
assert 'android:description="DPC-AIO synthetic test"' not in xml, 'HCE aid-group description must be a string resource reference'
assert 'android:description="@string/dpcaio_nfc_aid_group_description"' in xml
assert 'name="dpcaio_nfc_aid_group_description"' in strings
for required in [
    'HostApduService','processCommandApdu','NfcA','NfcB','NfcF','NfcV','IsoDep','Ndef','NdefFormatable','NfcBarcode','MifareClassic','MifareUltralight',
    'authenticateSectorWithKeyA','authenticateSectorWithKeyB','readPages','writePage','transceive','NfcReplayValidator'
]:
    assert required in texts, required
print('test_nfc_lab_android_contract: PASS')

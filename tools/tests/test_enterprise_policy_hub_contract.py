from pathlib import Path
root=Path(__file__).resolve().parents[2]
catalog_path=root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnterprisePolicyCatalog.kt'
hub_path=root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnterprisePolicyHubActivity.kt'
manifest=(root/'apps/dpc/app/src/main/AndroidManifest.xml').read_text()
assert catalog_path.is_file(), 'catalog missing'
assert hub_path.is_file(), 'hub missing'
c=catalog_path.read_text(); h=hub_path.read_text()
for pid in ['usb_data','auto_time','auto_timezone','thread_network','nfc_radio','nfc_changes','app_functions','local_network_permission']:
    assert f'"{pid}"' in c, pid
for token in ['minApi = 31','minApi = 35','minApi = 36','minApi = 37','RiskClass.HIGH','VisibilityClass.EXPERIMENTAL']:
    assert token in c, token
for token in ['CapabilityResolver.resolve','ManagementContextFactory.create','AndroidDevicePolicyGateway','AlertDialog.Builder','ACCESS_LOCAL_NETWORK','PolicyResult']:
    assert token in h, token
assert '.EnterprisePolicyHubActivity' in manifest
print('test_enterprise_policy_hub_contract: PASS')

from pathlib import Path
root = Path(__file__).resolve().parents[2]
registry = (root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt').read_text()
center = (root/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ModuleCenterActivity.kt').read_text()
for module in [':knox-license-lab', ':knox-mock-core', ':knox-mock-android', ':nfc-lab-core', ':nfc-lab-android', ':lab-tools']:
    pos = registry.index(f'"{module}"')
    window = registry[pos:pos+500]
    assert 'VisibilityClass.LAB' in window, f'{module} must be LAB'
for module in [':dhizuku-compat', ':shizuku-adapter']:
    pos = registry.index(f'"{module}"')
    window = registry[pos:pos+500]
    assert 'VisibilityClass.HIDDEN' in window, f'{module} must be HIDDEN'
for token in ['Show hidden', 'Developer / Lab', 'CapabilityResolver.resolve', 'ManagementContextFactory.create', 'selectedFilter', 'resolution.executable', 'resolution.availability.name']:
    assert token in center, token
for filter_id in ['all','available','unavailable','samsung_knox','lab']:
    assert f'"{filter_id}"' in center, filter_id
print('test_module_visibility_contract: PASS')

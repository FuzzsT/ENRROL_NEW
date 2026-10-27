#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app'
PLATFORM = ROOT / 'apps/dpc/modules/platform/compat/src/main/kotlin/io/dpcaio/platform/AndroidUserId.kt'

errors = []
activity = (APP / 'ActivityExplorerActivity.kt').read_text()
knox = (APP / 'KnoxEnterpriseCenterActivity.kt').read_text()
offline_policy = (APP / 'OfflinePolicyApplier.kt').read_text()
offline_setup = (APP / 'OfflineSetupActivity.kt').read_text()
platform = PLATFORM.read_text()

if 'UserHandle.of(' in activity or 'UserHandle.of(' in offline_policy:
    errors.append('app-dpc must use only public UserHandle APIs; UserHandle.of is not in the public SDK')
if 'fun representativeUid(userId: Int): Int' not in platform:
    errors.append('AndroidUserId must expose representativeUid(userId) for public UserHandle.getUserHandleForUid conversion')
if 'UserHandle.getUserHandleForUid(AndroidUserId.representativeUid(targetUserId))' not in activity:
    errors.append('ActivityExplorer must resolve target users through public UserHandle.getUserHandleForUid')
if 'UserHandle.getUserHandleForUid(AndroidUserId.representativeUid(targetUserId))' not in offline_policy:
    errors.append('OfflinePolicyApplier must resolve target users through public UserHandle.getUserHandleForUid')
if 'ModernUi.scrollPage(this, body)' not in activity:
    errors.append('ActivityExplorer must use the shared ModernUi scroll surface')
if 'setContentView(ScrollView(this)' in activity:
    errors.append('ActivityExplorer must not regress to the legacy raw ScrollView surface')
if 'management.deviceOwner' in knox or 'management.profileOwner' in knox:
    errors.append('KnoxEnterpriseCenter must use ManagementContext.ownership, not removed deviceOwner/profileOwner fields')
if 'val ownerSatisfied = management.ownership != OwnershipMode.NONE' not in knox:
    errors.append('KnoxEnterpriseCenter must derive ownerSatisfied from OwnershipMode')
if 'val manifest = inspected.manifest' not in offline_policy:
    errors.append('OfflinePolicyApplier must capture inspected.manifest into a local val before use')
if 'AndroidOfflinePolicyReader().read(bundleFile, manifest.policyPath)' not in offline_policy:
    errors.append('OfflinePolicyApplier must use the non-null local manifest')
if offline_setup.count('val manifest = inspected?.manifest') < 2:
    errors.append('OfflineSetupActivity preview/apply paths must capture nullable manifest locally')
if 'inspected.manifest.bundleId' in offline_setup or 'inspected.manifest.packages' in offline_setup:
    errors.append('OfflineSetupActivity must not repeatedly smart-cast public inspected.manifest')

if errors:
    print('APP_DPC_COMPILE_114_CONTRACT_V2: FAIL')
    for e in errors:
        print(f'- {e}')
    raise SystemExit(1)
print('APP_DPC_COMPILE_114_CONTRACT_V2: PASS')

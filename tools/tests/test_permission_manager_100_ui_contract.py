from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
t=p.read_text()
for n in ['Target user ID','Actual','DPC','AppOp','Route','Grant','Deny','Default','Prompt','Auto Grant','Auto Deny','Batch Grant','Batch Deny','Batch Default','Batch Preview','Apply supported','Restore previous DPC states','Show only anomalies','AndroidPermissionManagerGateway','PermissionBatchTransaction','Choose package','Choose user','RuntimeTargetPicker.showPackagePicker','RuntimeTargetPicker.showUserPicker','AppOpsVerifiedEditorActivity.EXTRA_TARGET_USER_ID']:
    assert n in t,n
assert 'targetUserId' in t
for token in ['ModernUi.page','ModernUi.hero','ModernUi.section','ModernUi.field','ModernUi.card','ModernUi.action','ModernUi.outputCard']:
    assert token in t, token
print('PERMISSION_MANAGER_100_UI_CONTRACT: PASS')

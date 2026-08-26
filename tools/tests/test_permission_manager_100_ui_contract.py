from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt'
t=p.read_text()
for n in ['Target user ID','Actual','DPC','AppOp','Route','Grant','Deny','Default','Prompt','Auto Grant','Auto Deny','Batch Grant','Batch Deny','Batch Default','Batch Preview','Apply supported','Restore previous DPC states','Show only anomalies','AndroidPermissionManagerGateway','PermissionBatchTransaction']:
    assert n in t,n
assert 'targetUserId' in t
print('PERMISSION_MANAGER_100_UI_CONTRACT: PASS')

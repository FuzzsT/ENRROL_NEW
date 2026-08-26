from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
text=(ROOT/'apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxEnterpriseCenterActivity.kt').read_text('utf-8')
for needle in ['Capability Matrix','ANDROID_DPM','KNOX_OFFICIAL','SAMSUNG_SEM','OEM_INTERNAL','Owner=','License=','Permission=','Readback=','ALLOW_WITH_CONFIRMATION']:
    assert needle in text, f'missing {needle}'
assert text.count('class KnoxEnterpriseCenterActivity') == 1
print('test_samsung_enterprise_center_110_ui: PASS')

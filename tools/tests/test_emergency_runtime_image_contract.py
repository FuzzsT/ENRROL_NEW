from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/build-aio-enrollment.yml'
text = WORKFLOW.read_text(encoding='utf-8')

assert 'Prepare emergency ephemeral signer' in text, \
    'Unified enrollment workflow must retain emergency signing mode'
assert text.count('target: aosp_atd') >= 2, \
    'Device Owner primary and retry smoke must use account-free AOSP ATD images'
smoke = text[text.index('- name: AOSP Device Owner runtime smoke'):text.index('- name: Build QR release bundle')]
assert 'target: default' not in smoke, \
    'Device Owner smoke must not use the default image that can ship preexisting accounts'
assert text.count('api-level: 35') >= 2, \
    'Device Owner smoke should retain Android 15 / API 35 runtime coverage'
assert text.count('arch: x86_64') >= 2, \
    'Device Owner smoke should retain x86_64 CI acceleration'

print('EMERGENCY_RUNTIME_IMAGE_CONTRACT: PASS')

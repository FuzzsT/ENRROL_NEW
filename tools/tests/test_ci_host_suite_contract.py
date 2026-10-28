from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/pr-android-ci.yml'
text = WORKFLOW.read_text(encoding='utf-8')

assert 'Run host test suite' in text, 'PR CI must expose the repository host verification suite as an explicit gate'
assert './tools/run_host_tests.sh' in text or 'bash tools/run_host_tests.sh' in text, \
    'PR CI must execute tools/run_host_tests.sh, not only a hand-picked source-contract subset'

pull_request_start = text.index('  pull_request:')
workflow_dispatch_start = text.index('  workflow_dispatch:', pull_request_start)
pull_request_block = text[pull_request_start:workflow_dispatch_start]
assert 'paths:' not in pull_request_block, \
    'Required PR checks must run for every pull request to main; path filters can leave required checks pending forever'

print('CI_HOST_SUITE_CONTRACT: PASS')

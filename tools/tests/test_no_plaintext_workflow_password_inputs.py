from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / '.github' / 'workflows'

SENSITIVE_INPUT_RE = re.compile(r'(?im)^\s{6,}([A-Za-z0-9_]*(?:password|passwd|secret|token)[A-Za-z0-9_]*)\s*:\s*$')
INPUT_REFERENCE_RE = re.compile(r'\$\{\{\s*inputs\.([A-Za-z0-9_]+)\s*\}\}')

violations = []
for path in sorted(WORKFLOWS.glob('*.yml')) + sorted(WORKFLOWS.glob('*.yaml')):
    text = path.read_text(encoding='utf-8')
    sensitive = {m.group(1) for m in SENSITIVE_INPUT_RE.finditer(text)}
    for name in sorted(sensitive):
        refs = list(re.finditer(rf'\$\{{\{{\s*inputs\.{re.escape(name)}\s*\}}\}}', text))
        violations.append(f'{path.relative_to(ROOT)}: workflow_dispatch input {name!r} is sensitive and must not be used for secrets; use GitHub Actions secrets or a local secure prompt instead')

assert not violations, 'Plaintext/sensitive workflow_dispatch inputs are forbidden:\n' + '\n'.join(violations)
print('NO_PLAINTEXT_WORKFLOW_PASSWORD_INPUTS: PASS')

#!/usr/bin/env python3
from pathlib import Path
import json
import os
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / 'tools' / 'release' / 'fetch_migration_update_matrix_artifact.py'


def write_fake_gh(path: Path, log: Path) -> None:
    script = f'''#!/usr/bin/env python3
import json, sys
from pathlib import Path
log = Path({str(log)!r})
with log.open('a', encoding='utf-8') as fh:
    fh.write(json.dumps(sys.argv[1:]) + "\\n")
args = sys.argv[1:]
if args[:2] == ['run', 'list']:
    print(json.dumps([
        {{'databaseId': 902, 'status': 'completed', 'conclusion': 'failure', 'event': 'workflow_dispatch', 'workflowName': 'Migration Update Compatibility Matrix', 'createdAt': '2026-11-01T20:00:00Z', 'headBranch': 'main', 'url': 'https://example.invalid/902'}},
        {{'databaseId': 901, 'status': 'completed', 'conclusion': 'success', 'event': 'workflow_dispatch', 'workflowName': 'Migration Update Compatibility Matrix', 'createdAt': '2026-11-01T19:00:00Z', 'headBranch': 'main', 'url': 'https://example.invalid/901'}}
    ]))
    raise SystemExit(0)
if args[:2] == ['run', 'view']:
    run_id = int(args[2])
    print(json.dumps({{'databaseId': run_id, 'workflowName': 'Migration Update Compatibility Matrix', 'status': 'completed', 'conclusion': 'success', 'event': 'workflow_dispatch', 'headBranch': 'main', 'headSha': 'abc', 'url': f'https://example.invalid/{{run_id}}'}}))
    raise SystemExit(0)
if args[:2] == ['run', 'download']:
    out = Path(args[args.index('--dir') + 1])
    out.mkdir(parents=True, exist_ok=True)
    (out / 'migration-update-matrix.json').write_text('{{"status":"MIGRATION_UPDATE_MATRIX_PASS"}}', encoding='utf-8')
    raise SystemExit(0)
print('unexpected gh args', args, file=sys.stderr)
raise SystemExit(9)
'''
    path.write_text(script, encoding='utf-8')
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


def main() -> None:
    assert TOOL.is_file(), 'missing migration update matrix artifact fetcher'
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        fake = tmp / 'gh'
        log = tmp / 'gh.log'
        out = tmp / 'out'
        write_fake_gh(fake, log)
        env = os.environ.copy()
        env['PATH'] = str(tmp) + os.pathsep + env.get('PATH', '')
        cp = subprocess.run([
            sys.executable, str(TOOL),
            '--repo', 'FuzzsT/ENRROL_NEW',
            '--output-dir', str(out),
            '--no-wait',
        ], text=True, capture_output=True, env=env)
        assert cp.returncode == 0, cp.stdout + cp.stderr
        assert 'Run ID: 901' in cp.stdout, cp.stdout
        calls = [json.loads(line) for line in log.read_text(encoding='utf-8').splitlines()]
        list_call = next((c for c in calls if c[:2] == ['run', 'list']), None)
        assert list_call is not None, calls
        joined = ' '.join(list_call)
        assert '--workflow migration-update-matrix.yml' in joined, list_call
        assert '--event workflow_dispatch' in joined, list_call
        assert '--status completed' in joined, list_call
        assert any(c[:3] == ['run', 'view', '901'] for c in calls), calls
        assert any(c[:3] == ['run', 'download', '901'] for c in calls), calls
        assert not any(c[:3] == ['run', 'download', '902'] for c in calls), calls
    print('MIGRATION_UPDATE_MATRIX_ARTIFACT_AUTO_RUNID: PASS')


if __name__ == '__main__':
    main()

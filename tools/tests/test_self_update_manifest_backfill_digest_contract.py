#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github' / 'workflows' / 'build-aio-enrollment.yml'


def main():
    workflow = WORKFLOW.read_text(encoding='utf-8')

    required = [
        'gh api "repos/${GITHUB_REPOSITORY}/releases/tags/${tag}"',
        'select(.name=="DPC-AIO-enterprise-release.apk")',
        '.digest',
        'expected_sha="${asset_digest#sha256:}"',
        'actual_sha="$(sha256sum DPC-AIO-enterprise-release.apk',
        'REPAIR_SELF_UPDATE_DIGEST_MISMATCH',
        'if [[ -f DPC-AIO-enterprise-release.apk.sha256 ]]; then',
        'REPAIR_SELF_UPDATE_SIDECAR_MISMATCH',
    ]
    for token in required:
        assert token in workflow, f'self-update backfill digest contract missing: {token}'

    assert "--pattern 'DPC-AIO-enterprise-release.apk'" in workflow
    assert "--pattern 'DPC-AIO-enterprise-release.apk.sha256'" in workflow
    assert "--pattern 'DPC-AIO-enterprise-release.apk.sha256' \\\n                --clobber || true" in workflow, (
        'historical releases without an APK sha256 sidecar must remain repairable'
    )
    assert '[[ "$actual_sha" == "$expected_sha" ]]' in workflow, (
        'downloaded APK must match GitHub Release asset digest before manifest generation'
    )
    assert '[[ "$sidecar_sha" == "$expected_sha" ]]' in workflow, (
        'when a sidecar exists it must agree with the GitHub Release asset digest'
    )

    print('SELF_UPDATE_BACKFILL_DIGEST_CONTRACT: PASS')


if __name__ == '__main__':
    main()

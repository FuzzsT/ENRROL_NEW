#!/usr/bin/env python3
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[2]
errors=[]
read=lambda p:(ROOT/p).read_text('utf-8')

workflow_dir=ROOT/'.github/workflows'
expected_workflows={
    'build-aio-enrollment.yml': 'Build AIO + enrollment QR',
    'build-emergency-enrollment.yml': 'Emergency enrollment (ephemeral signing)',
}
workflows={
    path.name: path
    for path in workflow_dir.iterdir()
    if path.is_file() and path.suffix in {'.yml', '.yaml'}
}
if workflows.keys() != expected_workflows.keys():
    errors.append(f'unexpected workflow topology: {sorted(workflows)}')
for filename, expected_name in expected_workflows.items():
    path=workflows.get(filename)
    if path is None:
        continue
    workflow_text=path.read_text('utf-8')
    name_match=re.search(r'^name:\s*(.+?)\s*$', workflow_text, re.M)
    observed_name=name_match.group(1).strip('"\'') if name_match else None
    if observed_name != expected_name:
        errors.append(f'unexpected workflow name for {filename}: {observed_name!r}')
    if '${{ github.run_id }}-${{ github.run_attempt }}' not in workflow_text:
        errors.append(f'{filename} artifact names are not unique per run attempt')
    if 'GH_TOKEN: ${{ github.token }}' not in workflow_text:
        errors.append(f'{filename} publish job does not use automatic github.token')

app=read('apps/dpc/app/build.gradle.kts')
vm=re.search(r'versionName\s*=\s*"([0-9.]+)"', app)
if not vm or tuple(map(int,vm.group(1).split('.'))) < (1,1,3): errors.append('versionName >=1.1.3 required')
m=re.search(r'versionCode\s*=\s*(\d+)', app)
if not m or int(m.group(1)) < 24: errors.append('versionCode >=24 required')

readme=read('README.md')
if 'DPC-AIO 1.1.' not in readme:
    errors.append('README does not identify a 1.1.x release')

prov=read('docs/releases/SOURCE-COMMIT.txt')
if '0.6.7' in prov or '1.1.' not in prov:
    errors.append('SOURCE-COMMIT provenance is stale')

gitignore=read('.gitignore')
for marker in ['dist/','.env','*.log','*.apk','*.aab','.cxx/']:
    if marker not in gitignore: errors.append(f'.gitignore missing {marker}')

licdoc=ROOT/'docs/GITHUB-PUBLISHING.md'
if not licdoc.is_file(): errors.append('missing docs/GITHUB-PUBLISHING.md')
else:
    text=licdoc.read_text('utf-8')
    if 'license' not in text.lower() or 'GitHub Actions' not in text:
        errors.append('GitHub publishing doc missing licensing/actions guidance')

wf=read('.github/workflows/build-aio-enrollment.yml')
if not re.search(r'^permissions:\n\s+contents:\s+read\s*$', wf, re.M):
    errors.append('workflow top-level contents: read missing')
if not re.search(r'^\s{2}build:\n(?:.|\n)*?^\s{4}permissions:\n\s{6}contents:\s+read\s*$', wf, re.M):
    errors.append('build job contents: read missing')
if not re.search(r'^\s{2}publish:\n(?:.|\n)*?^\s{4}permissions:\n\s{6}contents:\s+write\s*$', wf, re.M):
    errors.append('publish job contents: write missing')
if 'needs: build' not in wf: errors.append('publish job does not depend on build')
if 'actions/download-artifact@' not in wf: errors.append('publish job missing download-artifact')
for marker in [
    'tools/release/read_apk_signing_cert.py',
    'tools/gradle_source_audit.py --root . --json-out dist/gradle-source-audit.json',
    'DPC_AIO_SOURCE_AUDIT_ARTIFACT_NAME',
]:
    if marker not in wf: errors.append(f'workflow missing signing/source diagnostic: {marker}')

# Secret signing material must not be job-global. It may appear only in step-level env blocks.
head_to_steps=wf.split('    steps:',1)[0]
for secret_name in [
    'DPC_AIO_RELEASE_KEYSTORE_B64',
    'DPC_AIO_RELEASE_STORE_PASSWORD',
    'DPC_AIO_RELEASE_KEY_ALIAS',
    'DPC_AIO_RELEASE_KEY_PASSWORD',
]:
    if secret_name in head_to_steps:
        errors.append(f'signing secret exposed in job env: {secret_name}')

# All external actions must be immutable full-SHA pins.
uses=re.findall(r'^\s*uses:\s*([^#\s]+)',wf,re.M)
if not uses: errors.append('workflow has no actions')
for use in uses:
    if use.startswith('./'):
        continue
    if not re.search(r'@[0-9a-fA-F]{40}$',use):
        errors.append(f'action is not full-SHA pinned: {use}')

if 'DPC-AIO-enterprise-debug.tag.downloaded.apk' in wf:
    errors.append('stale debug-named release download path remains')

# Keystore must be destroyed before third-party emulator action runs.
cleanup=wf.find('Cleanup release signing material before runtime')
emu=wf.find('ReactiveCircus/android-emulator-runner@')
if cleanup < 0 or emu < 0 or cleanup > emu:
    errors.append('signing material is not cleaned before emulator action')

# Publishing must happen in the dedicated write-permission job.
pub_idx=wf.find('\n  publish:')
if pub_idx < 0:
    errors.append('publish job missing')
else:
    before=wf[:pub_idx]
    after=wf[pub_idx:]
    for marker in ['Publish continuous enrollment assets','Publish tag release assets']:
        if marker in before or marker not in after:
            errors.append(f'{marker} not isolated to publish job')

report=json.loads(read('RELEASE-VERIFICATION.json'))
rv=report.get('version','0.0.0')
if tuple(map(int,rv.split('.'))) < (1,1,3): errors.append('release report version <1.1.3')
if report.get('sourceEvidence',{}).get('githubUploadReadiness113')!='PASS':
    errors.append('githubUploadReadiness113 evidence missing')

if errors:
    raise SystemExit('GITHUB_UPLOAD_READY_113: FAIL\n- '+'\n- '.join(errors))
print('GITHUB_UPLOAD_READY_113: PASS')

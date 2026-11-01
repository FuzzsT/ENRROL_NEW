param(
    [Parameter(Mandatory = $true)]
    [string]$BundleDir,

    [Parameter(Mandatory = $true)]
    [string]$OldKeystore,

    [string]$Repo = 'FuzzsT/ENRROL_NEW'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tool = Join-Path $Root 'tools\release\configure_migration_signing.py'

if (-not (Test-Path -LiteralPath $Tool -PathType Leaf)) {
    throw "MIGRATION_LAUNCHER_TOOL_MISSING: $Tool"
}

$Python = Get-Command python -ErrorAction SilentlyContinue
if (-not $Python) {
    $Python = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $Python) {
    throw 'MIGRATION_LAUNCHER_PYTHON_MISSING: Python was not found in PATH.'
}

Write-Host 'MIGRATION_LAUNCHER_AUDIT: audit-only; no GitHub changes will be made.'

$Arguments = @(
    $Tool,
    '--bundle-dir', $BundleDir,
    '--old-keystore', $OldKeystore,
    '--repo', $Repo
)

& $Python.Source @Arguments
$ExitCode = $LASTEXITCODE
if ($ExitCode -ne 0) {
    throw "MIGRATION_LAUNCHER_FAILED: configure_migration_signing.py exited with code $ExitCode"
}

Write-Host 'MIGRATION_LAUNCHER_AUDIT: PASS'

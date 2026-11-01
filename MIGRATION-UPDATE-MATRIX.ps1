param(
    [Parameter(Mandatory = $true)]
    [string]$ReportLe27,

    [Parameter(Mandatory = $true)]
    [string]$Report28_32,

    [Parameter(Mandatory = $true)]
    [string]$Report33Plus,

    [string]$Repo = 'FuzzsT/ENRROL_NEW',
    [string]$Ref = 'main',
    [switch]$Dispatch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tool = Join-Path $root 'tools\release\dispatch_migration_update_matrix.py'

if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
    throw "MIGRATION_UPDATE_MATRIX_WINDOWS_LAUNCHER: helper missing: $tool"
}

$python = Get-Command python -ErrorAction SilentlyContinue
$pythonArgs = @()
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
    if ($python) {
        $pythonArgs += '-3'
    }
}
if (-not $python) {
    throw 'MIGRATION_UPDATE_MATRIX_WINDOWS_LAUNCHER: Python 3 not found'
}

$argsList = @(
    $tool,
    '--report-le27', $ReportLe27,
    '--report-28-32', $Report28_32,
    '--report-33-plus', $Report33Plus,
    '--repo', $Repo,
    '--ref', $Ref
)

if ($Dispatch) {
    $argsList += '--dispatch'
}

Write-Host 'MIGRATION_UPDATE_MATRIX_WINDOWS_LAUNCHER'
Write-Host ("Mode: {0}" -f ($(if ($Dispatch) { 'DISPATCH' } else { 'AUDIT-ONLY' })))
Write-Host ("Repository: {0}" -f $Repo)
Write-Host ("Ref: {0}" -f $Ref)

& $python.Source @pythonArgs @argsList
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    throw "MIGRATION_UPDATE_MATRIX_WINDOWS_LAUNCHER: helper failed with exit code $exitCode"
}

exit 0

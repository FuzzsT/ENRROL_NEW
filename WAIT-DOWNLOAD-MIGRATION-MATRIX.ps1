param(
    [Parameter(Mandatory = $true)]
    [long]$RunId,

    [Parameter(Mandatory = $true)]
    [string]$OutputDir,

    [string]$Repo = 'FuzzsT/ENRROL_NEW',
    [switch]$NoWait
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tool = Join-Path $root 'tools\release\fetch_migration_update_matrix_artifact.py'

if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
    throw "MIGRATION_UPDATE_MATRIX_ARTIFACT_WINDOWS_LAUNCHER: helper missing: $tool"
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
    throw 'MIGRATION_UPDATE_MATRIX_ARTIFACT_WINDOWS_LAUNCHER: Python 3 not found'
}

$argsList = @(
    $tool,
    '--repo', $Repo,
    '--run-id', $RunId,
    '--output-dir', $OutputDir
)

if ($NoWait) {
    $argsList += '--no-wait'
}

Write-Host 'MIGRATION_UPDATE_MATRIX_ARTIFACT_WINDOWS_LAUNCHER'
Write-Host ("Repository: {0}" -f $Repo)
Write-Host ("Run ID: {0}" -f $RunId)
Write-Host ("Output: {0}" -f $OutputDir)
Write-Host ("Wait: {0}" -f ($(if ($NoWait) { 'NO' } else { 'YES' })))

& $python.Source @pythonArgs @argsList
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    throw "MIGRATION_UPDATE_MATRIX_ARTIFACT_WINDOWS_LAUNCHER: helper failed with exit code $exitCode"
}

exit 0

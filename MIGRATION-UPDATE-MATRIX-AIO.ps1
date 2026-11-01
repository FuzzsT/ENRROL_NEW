param(
    [string]$ReportLe27,
    [string]$Report28_32,
    [string]$Report33Plus,
    [string]$Repo = 'FuzzsT/ENRROL_NEW',
    [string]$Ref = 'main',
    [switch]$Dispatch,
    [switch]$Fetch,
    [long]$RunId,
    [string]$OutputDir,
    [switch]$NoWait
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$matrixLauncher = Join-Path $root 'MIGRATION-UPDATE-MATRIX.ps1'
$artifactLauncher = Join-Path $root 'WAIT-DOWNLOAD-MIGRATION-MATRIX.ps1'

Write-Host 'MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER'
Write-Host ("Repository: {0}" -f $Repo)

if (-not (Test-Path -LiteralPath $matrixLauncher -PathType Leaf)) {
    throw "MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: matrix launcher missing: $matrixLauncher"
}
if (-not (Test-Path -LiteralPath $artifactLauncher -PathType Leaf)) {
    throw "MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: artifact launcher missing: $artifactLauncher"
}

if ($Fetch -and $Dispatch) {
    throw 'MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: Dispatch and Fetch are separate explicit stages'
}

if ($Fetch) {
    if ($RunId -le 0) {
        throw 'MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: Fetch requires RunId > 0'
    }
    if ([string]::IsNullOrWhiteSpace($OutputDir)) {
        throw 'MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: Fetch requires OutputDir'
    }

    Write-Host 'Mode: FETCH'
    $fetchArgs = @(
        '-RunId', $RunId,
        '-OutputDir', $OutputDir,
        '-Repo', $Repo
    )
    if ($NoWait) {
        $fetchArgs += '-NoWait'
    }

    & $artifactLauncher @fetchArgs
    if ($LASTEXITCODE -ne 0) {
        throw "MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: artifact stage failed with exit code $LASTEXITCODE"
    }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($ReportLe27) -or
    [string]::IsNullOrWhiteSpace($Report28_32) -or
    [string]::IsNullOrWhiteSpace($Report33Plus)) {
    throw 'MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: audit/dispatch stage requires all three report paths'
}

Write-Host ("Mode: {0}" -f ($(if ($Dispatch) { 'DISPATCH' } else { 'AUDIT-ONLY' })))
$matrixArgs = @(
    '-ReportLe27', $ReportLe27,
    '-Report28_32', $Report28_32,
    '-Report33Plus', $Report33Plus,
    '-Repo', $Repo,
    '-Ref', $Ref
)
if ($Dispatch) {
    $matrixArgs += '-Dispatch'
}

& $matrixLauncher @matrixArgs
if ($LASTEXITCODE -ne 0) {
    throw "MIGRATION_UPDATE_MATRIX_AIO_WINDOWS_LAUNCHER: matrix stage failed with exit code $LASTEXITCODE"
}

exit 0

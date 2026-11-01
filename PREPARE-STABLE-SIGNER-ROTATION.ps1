param(
    [string]$OldKeystore,
    [int]$RotationMinSdkVersion = 33
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tool = Join-Path $root 'tools\release\prepare_signing_rotation.py'

if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
    throw "Rotation tool not found: $tool"
}

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $python) {
    throw 'ROTATION_PREFLIGHT_MISSING: Python was not found in PATH.'
}

if (-not $OldKeystore) {
    $recoveryRoot = Join-Path $HOME 'DPC-AIO-stable-signing-recovery'
    if (Test-Path -LiteralPath $recoveryRoot -PathType Container) {
        $OldKeystore = Get-ChildItem -LiteralPath $recoveryRoot -Recurse -File -Filter 'dpc-aio-enterprise-release.p12' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTimeUtc -Descending |
            Select-Object -First 1 -ExpandProperty FullName
    }
}

if (-not $OldKeystore) {
    throw 'OLD_SIGNER_MISSING: run RECOVER-STABLE-SIGNER.bat first or pass -OldKeystore <path>.'
}

$resolvedOldKeystore = (Resolve-Path -LiteralPath $OldKeystore).Path

Write-Host 'DPC-AIO stable signer rotation preparation'
Write-Host 'This does not publish an APK, modify GitHub Secrets, or dispatch a workflow.'
Write-Host 'The historical signer is used only to authorize a signing lineage to a new key.'
Write-Host ("Old signer: {0}" -f $resolvedOldKeystore)
Write-Host ("Rotation minimum SDK: {0}" -f $RotationMinSdkVersion)
Write-Host 'Passwords are requested by Python getpass and are not passed on the command line.'

$argsList = @(
    $tool,
    '--old-keystore', $resolvedOldKeystore,
    '--rotation-min-sdk-version', $RotationMinSdkVersion
)

if ($python.Name -ieq 'py.exe' -or $python.Name -ieq 'py') {
    & $python.Source -3 @argsList
} else {
    & $python.Source @argsList
}
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    throw "Stable signer rotation preparation failed with exit code $exitCode."
}

Write-Host 'Rotation preparation completed locally. Validate the generated lineage before any release integration.'

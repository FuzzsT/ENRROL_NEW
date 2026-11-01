param(
    [switch]$Apply,
    [switch]$Dispatch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tool = Join-Path $root 'tools\release\recover_stable_signer_verified.py'

if (-not (Test-Path -LiteralPath $tool)) {
    throw "Recovery tool not found: $tool"
}

if ($Dispatch -and -not $Apply) {
    throw '-Dispatch requires -Apply because dispatch is allowed only after verified GitHub stable-signing configuration.'
}

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $python) {
    throw 'Python was not found in PATH.'
}

Write-Host 'DPC-AIO stable signer recovery'
Write-Host 'This recovers the existing historical signer. It does not generate or rotate a key.'
Write-Host 'Historical encrypted signer and APK hashes are verified before decryption.'
Write-Host 'Use the same value that was entered as release_signing_password in the historical workflow (minimum 12 characters).'
Write-Host 'The password prompt is handled by Python getpass and is not passed on the command line.'

$argsList = @($tool)
if ($Apply) {
    $argsList += '--configure-github'
}
if ($Dispatch) {
    $argsList += '--dispatch'
}

if ($python.Name -ieq 'py.exe' -or $python.Name -ieq 'py') {
    & $python.Source -3 @argsList
} else {
    & $python.Source @argsList
}
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    throw "Stable signer recovery failed with exit code $exitCode. No new signer was generated."
}

if (-not $Apply) {
    Write-Host 'Audit completed. Re-run with -Apply only after Historical APK signer verification: PASS.'
} elseif (-not $Dispatch) {
    Write-Host 'Stable signing configuration submitted. Run again with -Apply -Dispatch to start the stable release workflow.'
}

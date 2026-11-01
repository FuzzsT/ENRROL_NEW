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

function Resolve-RecoveryTool {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string[]]$Fallbacks = @()
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    foreach ($candidate in $Fallbacks) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    return $null
}

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $python) {
    throw 'RECOVERY_PREFLIGHT_MISSING: Python was not found in PATH.'
}

$opensslFallbacks = @(
    "$env:ProgramFiles\Git\usr\bin\openssl.exe",
    "${env:ProgramFiles(x86)}\Git\usr\bin\openssl.exe"
)
$opensslPath = Resolve-RecoveryTool -Name 'openssl' -Fallbacks $opensslFallbacks

$keytoolFallbacks = @()
if ($env:JAVA_HOME) {
    $keytoolFallbacks += (Join-Path $env:JAVA_HOME 'bin\keytool.exe')
}
$keytoolPath = Resolve-RecoveryTool -Name 'keytool' -Fallbacks $keytoolFallbacks

$apksignerPath = $null
$apksignerCommand = Get-Command apksigner -ErrorAction SilentlyContinue
if ($apksignerCommand) {
    $apksignerPath = $apksignerCommand.Source
}
if (-not $apksignerPath) {
    $sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { $_ }
    foreach ($sdkRoot in $sdkRoots) {
        $buildTools = Join-Path $sdkRoot 'build-tools'
        if (-not (Test-Path -LiteralPath $buildTools -PathType Container)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $buildTools -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]($_.Name -replace '[^0-9\.]', '') } -Descending |
            ForEach-Object {
                $path = Join-Path $_.FullName 'apksigner.bat'
                if (Test-Path -LiteralPath $path -PathType Leaf) { $path }
            } |
            Select-Object -First 1
        if ($candidate) {
            $apksignerPath = $candidate
            break
        }
    }
}

$missing = @()
if (-not $opensslPath) { $missing += 'openssl (PATH or Git for Windows)' }
if (-not $keytoolPath) { $missing += 'keytool (PATH or JAVA_HOME\bin)' }
if (-not $apksignerPath) { $missing += 'apksigner (PATH or ANDROID_SDK_ROOT/ANDROID_HOME build-tools)' }

$ghPath = $null
if ($Apply) {
    $ghCommand = Get-Command gh -ErrorAction SilentlyContinue
    if ($ghCommand) {
        $ghPath = $ghCommand.Source
    } else {
        $missing += 'gh (GitHub CLI; required for -Apply/-Dispatch)'
    }
}

if ($missing.Count -gt 0) {
    throw ('RECOVERY_PREFLIGHT_MISSING: ' + ($missing -join '; '))
}

$toolDirs = @(
    (Split-Path -Parent $opensslPath),
    (Split-Path -Parent $keytoolPath),
    (Split-Path -Parent $apksignerPath)
)
if ($ghPath) {
    $toolDirs += (Split-Path -Parent $ghPath)
}
$toolDirs = $toolDirs | Where-Object { $_ } | Select-Object -Unique
$env:PATH = (($toolDirs -join [IO.Path]::PathSeparator) + [IO.Path]::PathSeparator + $env:PATH)

Write-Host 'RECOVERY_PREFLIGHT: PASS'
Write-Host ("  openssl:  {0}" -f $opensslPath)
Write-Host ("  keytool:  {0}" -f $keytoolPath)
Write-Host ("  apksigner:{0}" -f $apksignerPath)
if ($ghPath) {
    Write-Host ("  gh:       {0}" -f $ghPath)
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

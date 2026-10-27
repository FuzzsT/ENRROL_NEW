[CmdletBinding()]
# Example: .\import-existing-stable-signing.ps1 -Keystore C:\keys\release.p12 -Dispatch
param(
    [Parameter(Mandatory=$true)][string]$Keystore,
    [string]$Repo = 'FuzzsT/ENRROL_NEW',
    [string]$Alias,
    [string]$ExpectedSha256,
    [string]$BackupDir = (Join-Path $HOME '.dpc-aio\keystore-backups'),
    [switch]$Dispatch
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "$Name is required." }
}
function Normalize-Sha([string]$Value) {
    return (($Value -replace '[:\s]', '').ToUpperInvariant())
}
function SecureToPlain([Security.SecureString]$Secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
function Set-GhSecretFromSecureString([string]$Name, [Security.SecureString]$Value, [string]$Repository) {
    $plain = SecureToPlain $Value
    try {
        $plain | gh secret set $Name --repo $Repository
        if ($LASTEXITCODE -ne 0) { throw "Failed to set GitHub secret $Name" }
    } finally {
        $plain = $null
    }
}
function Set-GhSecretFromString([string]$Name, [string]$Value, [string]$Repository) {
    $Value | gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Failed to set GitHub secret $Name" }
}

foreach ($cmd in @('keytool','jarsigner','jar','gh')) { Require-Command $cmd }
$resolved = (Resolve-Path -LiteralPath $Keystore).Path
if (-not $Alias) { $Alias = Read-Host 'Signing key alias' }
if (-not $Alias) { throw 'Signing key alias cannot be empty.' }

$storeSecure = Read-Host 'Keystore/store password' -AsSecureString
$keySecure = Read-Host 'Private-key password (enter the store password again if shared)' -AsSecureString
$storePlain = SecureToPlain $storeSecure
$keyPlain = SecureToPlain $keySecure
try {
    $keytoolOut = & keytool -list -v -keystore $resolved -storepass $storePlain -alias $Alias 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Keystore validation failed. Check store password and alias.' }
    $shaLine = $keytoolOut | Where-Object { $_ -match '^\s*SHA256:\s*' } | Select-Object -First 1
    if (-not $shaLine) { throw 'Could not determine SHA-256 signing certificate fingerprint.' }
    $observed = Normalize-Sha ($shaLine -replace '^\s*SHA256:\s*','')
    if ($observed -notmatch '^[0-9A-F]{64}$') { throw 'Invalid observed SHA-256 fingerprint.' }

    $tmp = Join-Path ([IO.Path]::GetTempPath()) ('dpc-aio-signer-probe-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path (Join-Path $tmp 'empty') -Force | Out-Null
    try {
        Push-Location $tmp
        try { & jar --create --file probe.jar -C empty . | Out-Null }
        finally { Pop-Location }
        & jarsigner -keystore $resolved -storepass $storePlain -keypass $keyPlain -signedjar (Join-Path $tmp 'probe-signed.jar') (Join-Path $tmp 'probe.jar') $Alias *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Private-key validation failed. Check alias/key password.' }
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }

    if (-not $ExpectedSha256) {
        $releaseUri = "https://github.com/$Repo/releases/download/dpc-aio-continuous/signing-cert-sha256.txt"
        try { $ExpectedSha256 = (Invoke-WebRequest -UseBasicParsing -Uri $releaseUri -TimeoutSec 20).Content }
        catch { $ExpectedSha256 = $null }
    }
    if (-not $ExpectedSha256) { throw 'No historical stable fingerprint was available. Supply -ExpectedSha256.' }
    $expected = Normalize-Sha $ExpectedSha256
    if ($expected -notmatch '^[0-9A-F]{64}$') { throw 'Expected SHA-256 is not a 64-digit hex fingerprint.' }
    if ($observed -ne $expected) { throw "SIGNER_MISMATCH: expected $expected but recovered keystore is $observed" }

    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $backup = Join-Path $BackupDir ("dpc-aio-stable-$stamp-" + [IO.Path]::GetFileName($resolved))
    Copy-Item -LiteralPath $resolved -Destination $backup -Force

    & gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI is not authenticated.' }

    $bytes = [IO.File]::ReadAllBytes($resolved)
    $b64 = [Convert]::ToBase64String($bytes)
    Set-GhSecretFromString 'DPC_AIO_RELEASE_KEYSTORE_B64' $b64 $Repo
    Set-GhSecretFromSecureString 'DPC_AIO_RELEASE_STORE_PASSWORD' $storeSecure $Repo
    Set-GhSecretFromString 'DPC_AIO_RELEASE_KEY_ALIAS' $Alias $Repo
    Set-GhSecretFromSecureString 'DPC_AIO_RELEASE_KEY_PASSWORD' $keySecure $Repo
    & gh variable set DPC_AIO_EXPECTED_SIGNING_CERT_SHA256 --repo $Repo --body $observed
    if ($LASTEXITCODE -ne 0) { throw 'Failed to set DPC_AIO_EXPECTED_SIGNING_CERT_SHA256.' }

    Write-Host "Configured stable signer for $Repo"
    Write-Host "Certificate SHA-256: $observed"
    Write-Host "Local backup: $backup"
    if ($Dispatch) {
        & gh workflow run build-aio-enrollment.yml --repo $Repo --ref main
        if ($LASTEXITCODE -ne 0) { throw 'Failed to dispatch build-aio-enrollment.yml.' }
        Write-Host 'Dispatched build-aio-enrollment.yml on main.'
    } else {
        Write-Host "Run when ready: gh workflow run build-aio-enrollment.yml --repo '$Repo' --ref main"
    }
} finally {
    $storePlain = $null
    $keyPlain = $null
}

param(
    [string]$Remote = "https://github.com/local-localhost-app-system/dpc_android.git",
    [string]$Branch = "main"
)
$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "tools\migration\push_dpc_android.ps1") -Remote $Remote -Branch $Branch
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

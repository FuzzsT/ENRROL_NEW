param([string]$Repository = "local-localhost-app-system/dpc_android")
$ErrorActionPreference = "Stop"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI (gh) is required." }
gh auth status | Out-Null
if ($LASTEXITCODE -ne 0) { throw "gh is not authenticated." }
gh workflow run build-emergency-enrollment.yml --repo $Repository
if ($LASTEXITCODE -ne 0) { throw "Could not start emergency enrollment workflow." }
Write-Host "Emergency enrollment workflow started for $Repository"
$runId = gh run list --repo $Repository --workflow build-emergency-enrollment.yml --limit 1 --json databaseId --jq '.[0].databaseId'
if ($LASTEXITCODE -ne 0) { throw "Could not resolve workflow run." }
if ($runId) {
    Write-Host "Watching run $runId"
    gh run watch $runId --repo $Repository --exit-status
    if ($LASTEXITCODE -ne 0) { throw "Emergency enrollment workflow failed." }
    Write-Host "Release: https://github.com/$Repository/releases/tag/dpc-aio-emergency-enrollment"
}
